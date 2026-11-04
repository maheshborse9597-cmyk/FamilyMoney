package com.familymoney.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.familymoney.app.ui.AppRoot
import com.familymoney.app.ui.AppViewModel
import com.familymoney.app.ui.Incoming
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        lifecycleScope.launch {
            vm.settings.collect { applySecureFlag(it.secureScreen) }
        }

        setContent { AppRoot(vm = vm, host = this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        vm.onAppStopped()
    }

    /** Hides the app from screenshots and from the recent-apps preview. */
    private fun applySecureFlag(secure: Boolean) {
        if (secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null && data.scheme == "familymoney") {
            when (data.host) {
                "pair" -> vm.incoming.value = Incoming(
                    kind = "pair",
                    url = data.getQueryParameter("u") ?: "",
                    key = data.getQueryParameter("k") ?: ""
                )
                "add" -> vm.incoming.value = Incoming(kind = "add", type = data.getQueryParameter("type") ?: "Expense")
            }
        } else if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (uri != null) vm.incoming.value = Incoming(kind = "share", uri = uri)
        }
        val open = intent.getStringExtra(EXTRA_OPEN)
        if (open != null) vm.incoming.value = Incoming(kind = "open", open = open)
    }

    /** Shows the fingerprint / face / screen-lock prompt. */
    fun promptUnlock(onSuccess: () -> Unit, onMessage: (String) -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val status = BiometricManager.from(this).canAuthenticate(authenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            // The phone no longer has a screen lock, so app lock cannot work. Do not lock the person out.
            onMessage("No screen lock is set on this phone, so app lock is off until you set one.")
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onMessage(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Family Money")
            .setSubtitle("Confirm that it is you")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    /** Can this phone use app lock at all? (used by the settings screen) */
    fun canUseAppLock(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
