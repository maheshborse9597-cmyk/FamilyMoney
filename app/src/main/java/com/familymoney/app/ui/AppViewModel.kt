package com.familymoney.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familymoney.app.FamilyMoneyApp
import com.familymoney.app.data.ApiException
import com.familymoney.app.data.AppSettings
import com.familymoney.app.data.OutItem
import com.familymoney.app.data.Repository
import com.familymoney.app.data.Snapshot
import com.familymoney.app.data.Template
import com.familymoney.app.data.Tx
import com.familymoney.app.data.WriteResult
import com.familymoney.app.data.arr
import com.familymoney.app.data.asObj
import com.familymoney.app.data.bool
import com.familymoney.app.data.str
import com.familymoney.app.logic.Calc
import com.familymoney.app.logic.Dates
import com.familymoney.app.logic.ImageUtil
import com.familymoney.app.widget.BalanceWidgetProvider
import com.familymoney.app.work.Scheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.io.IOException
import java.util.UUID

/** A short message shown at the bottom of the screen, optionally with an action such as Undo. */
data class Msg(
    val text: String,
    val kind: String = "info",
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

/** Something that arrived from outside: a pairing link, an app shortcut, a shared photo or a notification tap. */
data class Incoming(
    val kind: String,
    val type: String = "",
    val uri: Uri? = null,
    val url: String = "",
    val key: String = "",
    val open: String = ""
)

sealed class TxOutcome {
    data class Saved(val entryId: String?) : TxOutcome()
    object Queued : TxOutcome()
    data class Duplicate(val message: String) : TxOutcome()
    data class Failed(val message: String) : TxOutcome()
}

data class TrashItem(val kind: String, val id: String, val label: String, val deletedAt: String)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val fm: FamilyMoneyApp = application as FamilyMoneyApp
    val repo: Repository = fm.repo

    val settings = MutableStateFlow(repo.store.loadSettings())
    val snapshot: StateFlow<Snapshot?> = repo.snapshot
    val outbox: StateFlow<List<OutItem>> = repo.outbox

    /** The data the screens show: the server copy plus anything saved offline that has not synced yet. */
    val effective: StateFlow<Snapshot?> = combine(repo.snapshot, repo.outbox) { s, o ->
        if (s == null) null else Calc.applyPending(s, o)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        repo.snapshot.value?.let { Calc.applyPending(it, repo.outbox.value) }
    )

    val syncing = MutableStateFlow(false)
    val paired = MutableStateFlow(repo.isPaired())
    val locked = MutableStateFlow(repo.isPaired() && repo.store.loadSettings().lockEnabled)
    val month = MutableStateFlow(Dates.today().take(7))
    val incoming = MutableStateFlow<Incoming?>(null)

    private val _messages = MutableSharedFlow<Msg>(extraBufferCapacity = 16)
    val messages: SharedFlow<Msg> = _messages.asSharedFlow()

    private var backgroundedAt = 0L
    private var lastRefresh = 0L

    init {
        if (repo.isPaired()) {
            fm.scheduleBackgroundWork()
            refresh(force = false, quiet = true)
        }
    }

    fun emit(text: String, kind: String = "info", actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.tryEmit(Msg(text, kind, actionLabel, action))
    }

    private fun friendly(t: Throwable): String = when (t) {
        is IOException -> "You are offline. Showing the last saved data."
        is ApiException -> t.message ?: "The server reported an error."
        else -> t.message ?: "Something went wrong."
    }

    /* ---------------- pairing & settings ---------------- */

    suspend fun pair(url: String, key: String, name: String): String? {
        val error = repo.pair(url, key, name)
        if (error == null) {
            paired.value = true
            fm.scheduleBackgroundWork()
            refresh(force = true, quiet = false)
        }
        return error
    }

    fun unpair() {
        Scheduler.cancelAll(fm)
        repo.unpair()
        paired.value = false
        locked.value = false
        BalanceWidgetProvider.updateAll(fm, repo)
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val old = settings.value
        val updated = change(old)
        settings.value = updated
        repo.store.saveSettings(updated)
        if (paired.value) {
            Scheduler.scheduleReminder(fm, updated)
        }
        BalanceWidgetProvider.updateAll(fm, repo)
    }

    /* ---------------- app lock ---------------- */

    fun onAppStopped() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onAppStarted() {
        val s = settings.value
        if (s.lockEnabled && paired.value && backgroundedAt > 0L &&
            System.currentTimeMillis() - backgroundedAt > LOCK_AFTER_MS
        ) {
            locked.value = true
        }
        if (paired.value && System.currentTimeMillis() - lastRefresh > 5 * 60_000L) {
            refresh(force = false, quiet = true)
        }
    }

    fun unlock() {
        locked.value = false
        backgroundedAt = 0L
    }

    /* ---------------- sync ---------------- */

    fun refresh(force: Boolean = true, quiet: Boolean = false) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val result = repo.refresh(force)
            syncing.value = false
            lastRefresh = System.currentTimeMillis()
            result.onSuccess { BalanceWidgetProvider.updateAll(fm, repo) }
            result.onFailure { if (!quiet) emit(friendly(it), "warn") }
        }
    }

    private fun refreshInBackground() {
        viewModelScope.launch {
            repo.refresh(true)
            lastRefresh = System.currentTimeMillis()
            BalanceWidgetProvider.updateAll(fm, repo)
        }
    }

    fun retryItem(rid: String) = viewModelScope.launch { repo.retry(rid); repo.refresh(true) }
    fun discardItem(rid: String) = viewModelScope.launch { repo.discard(rid) }
    fun keepDuplicate(rid: String) = viewModelScope.launch { repo.keepDuplicate(rid); repo.refresh(true) }

    /* ---------------- writes ---------------- */

    /**
     * Runs a change. Returns null when it worked (or was queued offline), otherwise a message to show in the form.
     */
    suspend fun run(op: String, args: JsonArray, label: String, okMsg: String? = null, silent: Boolean = false): String? {
        return when (val r = repo.write(op, args, label)) {
            is WriteResult.Ok -> {
                val d = r.data.asObj()
                if (d != null && d["success"].bool() == false) {
                    d["message"].str() ?: "The server did not accept this change."
                } else {
                    if (!silent) emit(okMsg ?: d?.get("message").str() ?: "Saved", "ok")
                    refreshInBackground()
                    null
                }
            }
            is WriteResult.Queued -> {
                emit("Saved on this phone. It will sync when you are online.", "warn")
                null
            }
            is WriteResult.Failed -> r.message
        }
    }

    fun deleteItem(op: String, id: String, label: String) {
        viewModelScope.launch {
            when (val r = repo.write(op, arr(id), label)) {
                is WriteResult.Ok -> {
                    val d = r.data.asObj()
                    val kind = d?.get("kind").str()
                    val itemId = d?.get("id").str()
                    val text = d?.get("message").str() ?: "Deleted."
                    refreshInBackground()
                    if (kind != null && itemId != null) {
                        emit(text, "ok", "Undo") { undoDelete(kind, itemId) }
                    } else {
                        emit(text, "ok")
                    }
                }
                is WriteResult.Queued -> emit("Will be deleted when you are online.", "warn")
                is WriteResult.Failed -> emit(r.message, "err")
            }
        }
    }

    fun postRecurringNow() {
        viewModelScope.launch {
            val err = run("postRecurringNow", JsonArray(emptyList()), "Post recurring entries")
            if (err != null) emit(err, "err")
        }
    }

    fun undoDelete(kind: String, id: String) {
        viewModelScope.launch {
            val err = run("restoreFromTrash", arr(kind, id), "Restore", okMsg = "Restored.")
            if (err != null) emit(err, "err")
        }
    }

    private fun pctFor(category: String, month: String): Double? {
        val s = effective.value ?: return null
        return Calc.budgetRows(s.transactions, s.budgets, month)
            .firstOrNull { it.category.equals(category, ignoreCase = true) }?.pct
    }

    /** Saves (or edits) a transaction. Handles offline queueing, duplicate warnings, Undo and budget warnings. */
    suspend fun saveTx(data: JsonObject, optimistic: Tx?, editing: Boolean, label: String): TxOutcome {
        val rid = UUID.randomUUID().toString()
        val payload = JsonObject(data + ("requestId" to JsonPrimitive(rid)))
        val category = data["category"].str() ?: ""
        val month = (data["date"].str() ?: "").take(7)
        val before = if (editing) null else pctFor(category, month)
        val isExpense = data["type"].str() == "Expense"

        return when (val r = repo.write("saveTransaction", arr(payload), label, rid = rid, tx = if (editing) null else optimistic)) {
            is WriteResult.Ok -> {
                val d = r.data.asObj()
                val success = d?.get("success").bool()
                if (success == false) {
                    val msg = d?.get("message").str() ?: "The server did not accept this entry."
                    if (d?.get("duplicate").bool() == true) TxOutcome.Duplicate(msg) else TxOutcome.Failed(msg)
                } else {
                    val entryId = d?.get("entryId").str()
                    val text = d?.get("message").str() ?: "Saved."
                    if (!editing && entryId != null) {
                        emit(text, "ok", "Undo") { deleteItem("deleteTransaction", entryId, "Undo entry") }
                    } else {
                        emit(text, "ok")
                    }
                    viewModelScope.launch {
                        repo.refresh(true)
                        lastRefresh = System.currentTimeMillis()
                        BalanceWidgetProvider.updateAll(fm, repo)
                        if (isExpense) warnIfBudgetCrossed(category, month, before)
                    }
                    TxOutcome.Saved(entryId)
                }
            }
            is WriteResult.Queued -> {
                emit("Saved on this phone. It will sync when you are online.", "warn")
                TxOutcome.Queued
            }
            is WriteResult.Failed -> TxOutcome.Failed(r.message)
        }
    }

    private fun warnIfBudgetCrossed(category: String, month: String, before: Double?) {
        val after = pctFor(category, month) ?: return
        val level = (snapshot.value?.settings?.budgetAlertPercent ?: 80).toDouble()
        val crossedOver = after > 100 && (before == null || before <= 100)
        val crossedNear = after >= level && (before == null || before < level)
        if (crossedOver || crossedNear) {
            emit("$category budget is now ${after.toInt()}% used.", "warn")
        }
    }

    fun useTemplate(t: Template) {
        viewModelScope.launch {
            val today = Dates.today()
            val description = if (t.type == "Transfer") "Transfer: ${t.person} → ${t.toPerson}" else t.description
            val optimistic = Tx(
                date = today, type = t.type, category = t.category, description = description, amount = t.amount,
                paymentMode = t.paymentMode, person = t.person, toPerson = t.toPerson,
                bankAccount = t.bank, transferToBank = t.toBank
            )
            when (val r = repo.write("useTemplate", arr(t.id), "Quick add: ${t.name}", tx = optimistic)) {
                is WriteResult.Ok -> {
                    val d = r.data.asObj()
                    val entryId = d?.get("entryId").str()
                    val text = d?.get("message").str() ?: "Saved."
                    refreshInBackground()
                    if (entryId != null) emit(text, "ok", "Undo") { deleteItem("deleteTransaction", entryId, "Undo entry") }
                    else emit(text, "ok")
                }
                is WriteResult.Queued -> emit("Saved on this phone. It will sync when you are online.", "warn")
                is WriteResult.Failed -> emit(r.message, "err")
            }
        }
    }

    /* ---------------- reads that are not cached ---------------- */

    suspend fun fetchTrash(): List<TrashItem> {
        val list = repo.read("getTrash")?.jsonArray ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e.asObj() ?: return@mapNotNull null
            TrashItem(
                kind = o["kind"].str() ?: "", id = o["id"].str() ?: "",
                label = o["label"].str() ?: "", deletedAt = o["deletedAt"].str() ?: ""
            )
        }
    }

    suspend fun fetchReceipt(url: String): Bitmap? {
        val o = repo.read("getReceipt", arr(url)).asObj() ?: return null
        val b64 = o["base64"].str() ?: return null
        return ImageUtil.decodeBase64(b64)
    }

    /** Returns the file name and the PDF bytes of the monthly report. */
    suspend fun fetchReport(month: String): Pair<String, ByteArray> {
        val o = repo.read("exportReport", arr(month)).asObj() ?: throw ApiException("The server sent no report.")
        val name = o["name"].str() ?: "Family-Money-$month.pdf"
        val b64 = o["base64"].str() ?: throw ApiException("The report is empty.")
        return name to Base64.decode(b64, Base64.DEFAULT)
    }

    companion object {
        private const val LOCK_AFTER_MS = 30_000L
    }
}
