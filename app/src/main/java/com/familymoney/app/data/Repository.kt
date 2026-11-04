package com.familymoney.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Talks to the Apps Script API. Network problems throw IOException, server problems throw ApiException. */
class ApiClient(private val store: SecureStore) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun call(
        op: String,
        args: JsonArray = JsonArray(emptyList()),
        rid: String? = null,
        urlOverride: String? = null,
        keyOverride: String? = null
    ): JsonElement? = withContext(Dispatchers.IO) {
        val url = urlOverride ?: store.apiUrl
        val key = keyOverride ?: store.deviceKey
        if (url.isBlank() || key.isBlank()) throw ApiException("This phone is not paired yet.")

        val body = buildJsonObject {
            put("key", key)
            put("op", op)
            put("args", args)
            if (rid != null) put("rid", rid)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val parsed: JsonObject = try {
                AppJson.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw ApiException(
                    if (resp.code in 500..599) "The server is busy (error ${resp.code}). Try again."
                    else "Unexpected reply from the server. Check the API URL, and that the deployment is set to " +
                        "\"Execute as: Me\" and \"Who has access: Anyone\"."
                )
            }
            if (parsed["ok"].bool() == true) parsed["data"]
            else throw ApiException(parsed["error"].str() ?: parsed["message"].str() ?: "The server reported an error.")
        }
    }
}

/**
 * Offline-first data layer.
 *  - snapshot: the last full copy of the data from the server (encrypted on disk)
 *  - outbox:   changes made while offline; replayed in order, each with a request id so a retry can never double-post
 */
class Repository(context: Context) {

    val store = SecureStore(context)
    val api = ApiClient(store)

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    private val _outbox = MutableStateFlow<List<OutItem>>(emptyList())
    val outbox: StateFlow<List<OutItem>> = _outbox.asStateFlow()

    private val lock = Mutex()

    init {
        store.readFile(SNAP_FILE)?.let {
            try { _snapshot.value = AppJson.decodeFromString(Snapshot.serializer(), it) } catch (e: Exception) { /* ignore a corrupt cache */ }
        }
        store.readFile(OUT_FILE)?.let {
            try { _outbox.value = AppJson.decodeFromString(ListSerializer(OutItem.serializer()), it) } catch (e: Exception) { /* ignore */ }
        }
    }

    fun isPaired(): Boolean = store.apiUrl.isNotBlank() && store.deviceKey.isNotBlank()

    private fun persistSnapshot(s: Snapshot) {
        try { store.writeFile(SNAP_FILE, AppJson.encodeToString(Snapshot.serializer(), s)) } catch (e: Exception) { /* cache is optional */ }
    }

    private fun persistOutbox() {
        try { store.writeFile(OUT_FILE, AppJson.encodeToString(ListSerializer(OutItem.serializer()), _outbox.value)) } catch (e: Exception) { /* keep in memory */ }
    }

    /** Checks the URL and key, then stores them. Returns null on success or a message to show. */
    suspend fun pair(url: String, key: String, name: String): String? {
        return try {
            api.call("getApiInfo", urlOverride = url.trim(), keyOverride = key.trim())
            store.apiUrl = url.trim()
            store.deviceKey = key.trim()
            store.deviceName = name.trim()
            null
        } catch (e: ApiException) {
            e.message ?: "Could not pair."
        } catch (e: IOException) {
            "No internet connection. Connect and try again."
        } catch (e: IllegalArgumentException) {
            "That does not look like a valid URL."
        }
    }

    fun unpair() {
        store.clearPairingAndData()
        _snapshot.value = null
        _outbox.value = emptyList()
    }

    /** Sends queued changes first, then downloads fresh data when the spreadsheet changed (or force = true). */
    suspend fun refresh(force: Boolean): Result<Unit> = lock.withLock {
        try {
            flushLocked()
            val old = _snapshot.value

            var token = ""
            try {
                token = api.call("getSyncToken").asObj()?.get("token").str() ?: ""
            } catch (e: ApiException) {
                // older server without getSyncToken: just download
            }
            if (!force && old != null && token.isNotEmpty() && token == old.syncToken) {
                return@withLock Result.success(Unit)
            }

            val data = api.call("getAppData", buildJsonArray { add("") })
                ?: throw ApiException("The server sent no data.")
            val snap = AppJson.decodeFromJsonElement(Snapshot.serializer(), data)
                .copy(savedAt = System.currentTimeMillis(), syncToken = token)
            _snapshot.value = snap
            persistSnapshot(snap)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs a change on the server. If there is no connection it is queued and replayed later.
     * Server-side rejections (validation errors) are returned as Failed and are never queued.
     */
    suspend fun write(
        op: String,
        args: JsonArray,
        label: String,
        rid: String = UUID.randomUUID().toString(),
        tx: Tx? = null
    ): WriteResult = lock.withLock {
        try {
            val queueClear = flushLocked()          // older queued changes go first, to keep the order
            if (!queueClear) {
                enqueueLocked(op, args, label, rid, tx)
                return@withLock WriteResult.Queued
            }
            WriteResult.Ok(api.call(op, args, rid))
        } catch (e: ApiException) {
            WriteResult.Failed(e.message ?: "The server reported an error.")
        } catch (e: IOException) {
            enqueueLocked(op, args, label, rid, tx)
            WriteResult.Queued
        }
    }

    /** Direct online call for reads that are not cached (trash list, receipt photo, PDF report). */
    suspend fun read(op: String, args: JsonArray = JsonArray(emptyList())): JsonElement? = api.call(op, args)

    private fun enqueueLocked(op: String, args: JsonArray, label: String, rid: String, tx: Tx?) {
        val item = OutItem(
            rid = rid,
            op = op,
            args = args.toString(),
            label = label,
            createdAt = System.currentTimeMillis(),
            tx = tx?.copy(entryId = "pending:$rid", pending = true)
        )
        _outbox.value = _outbox.value + item
        persistOutbox()
    }

    /** Replays pending items in order. Returns true when nothing is waiting any more. Must hold [lock]. */
    private suspend fun flushLocked(): Boolean {
        if (_outbox.value.none { it.status == "pending" }) return true
        val list = _outbox.value.toMutableList()
        var changed = false
        var i = 0
        while (i < list.size) {
            val item = list[i]
            if (item.status != "pending") { i++; continue }
            try {
                val args = AppJson.parseToJsonElement(item.args).jsonArray
                val data = api.call(item.op, args, item.rid)
                val result = data.asObj()
                if (result != null && result["success"].bool() == false) {
                    val duplicate = result["duplicate"].bool() == true
                    list[i] = item.copy(
                        status = if (duplicate) "conflict" else "failed",
                        error = result["message"].str() ?: "The server did not accept this change."
                    )
                    i++
                } else {
                    list.removeAt(i)
                }
                changed = true
            } catch (e: ApiException) {
                list[i] = item.copy(status = "failed", error = e.message ?: "Error")
                i++
                changed = true
            } catch (e: IOException) {
                break
            }
        }
        if (changed) {
            _outbox.value = list
            persistOutbox()
        }
        return list.none { it.status == "pending" }
    }

    suspend fun discard(rid: String) = lock.withLock {
        _outbox.value = _outbox.value.filter { it.rid != rid }
        persistOutbox()
    }

    suspend fun retry(rid: String) = lock.withLock {
        _outbox.value = _outbox.value.map { if (it.rid == rid) it.copy(status = "pending", error = "") else it }
        persistOutbox()
    }

    /** For a "possible duplicate" conflict: the user confirmed it is a real, separate entry. */
    suspend fun keepDuplicate(rid: String) = lock.withLock {
        _outbox.value = _outbox.value.map { item ->
            if (item.rid != rid) item else {
                val original = AppJson.parseToJsonElement(item.args).jsonArray
                val first = original.firstOrNull()?.asObj()
                if (first == null) item else {
                    val patched = JsonObject(first + ("allowDuplicate" to JsonPrimitive(true)))
                    val newArgs = JsonArray(listOf<JsonElement>(patched) + original.drop(1))
                    item.copy(args = newArgs.toString(), status = "pending", error = "")
                }
            }
        }
        persistOutbox()
    }

    companion object {
        private const val SNAP_FILE = "snapshot.json"
        private const val OUT_FILE = "outbox.json"
    }
}
