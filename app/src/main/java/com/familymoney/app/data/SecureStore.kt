package com.familymoney.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Everything sensitive lives here, encrypted with a key kept in the Android Keystore:
 *  - the API URL and the private device key,
 *  - the app settings,
 *  - the offline copy of your data and the queue of unsent changes (encrypted files).
 */
class SecureStore(context: Context) {

    private val app: Context = context.applicationContext
    private val masterKey: MasterKey = MasterKey.Builder(app)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs: SharedPreferences = openPrefs()

    private fun createPrefs(): SharedPreferences = EncryptedSharedPreferences.create(
        app,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun openPrefs(): SharedPreferences {
        return try {
            createPrefs()
        } catch (e: Exception) {
            // Keystore data can become unreadable (e.g. after a restore). Start clean instead of crashing.
            app.deleteSharedPreferences(PREFS_FILE)
            createPrefs()
        }
    }

    var apiUrl: String
        get() = prefs.getString(K_URL, "") ?: ""
        set(value) { prefs.edit().putString(K_URL, value).apply() }

    var deviceKey: String
        get() = prefs.getString(K_KEY, "") ?: ""
        set(value) { prefs.edit().putString(K_KEY, value).apply() }

    var deviceName: String
        get() = prefs.getString(K_NAME, "") ?: ""
        set(value) { prefs.edit().putString(K_NAME, value).apply() }

    fun loadSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            lockEnabled = prefs.getBoolean("lock", d.lockEnabled),
            secureScreen = prefs.getBoolean("secure", d.secureScreen),
            notifications = prefs.getBoolean("notif", d.notifications),
            notifyHour = prefs.getInt("notifHour", d.notifyHour),
            themeMode = prefs.getString("theme", d.themeMode) ?: d.themeMode,
            widgetHideAmounts = prefs.getBoolean("widgetHide", d.widgetHideAmounts)
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("lock", s.lockEnabled)
            .putBoolean("secure", s.secureScreen)
            .putBoolean("notif", s.notifications)
            .putInt("notifHour", s.notifyHour)
            .putString("theme", s.themeMode)
            .putBoolean("widgetHide", s.widgetHideAmounts)
            .apply()
    }

    private fun encrypted(file: File): EncryptedFile = EncryptedFile.Builder(
        app,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    fun readFile(name: String): String? {
        val f = File(app.filesDir, name)
        if (!f.exists()) return null
        return try {
            encrypted(f).openFileInput().use { String(it.readBytes(), Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
    }

    fun writeFile(name: String, text: String) {
        val f = File(app.filesDir, name)
        if (f.exists()) f.delete()
        encrypted(f).openFileOutput().use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun deleteFile(name: String) {
        File(app.filesDir, name).delete()
    }

    /** Forget the pairing and every piece of cached data (settings are kept). */
    fun clearPairingAndData() {
        prefs.edit().remove(K_URL).remove(K_KEY).remove(K_NAME).apply()
        deleteFile("snapshot.json")
        deleteFile("outbox.json")
    }

    companion object {
        private const val PREFS_FILE = "fm_secure_prefs"
        private const val K_URL = "apiUrl"
        private const val K_KEY = "deviceKey"
        private const val K_NAME = "deviceName"
    }
}
