package com.example.potty

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class SecurityManager(private val context: Context) {

    /**
     * Checks if the user has ANY form of security set up (Fingerprint, Face, or Device PIN/Pattern).
     */
    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        // Check for either strong biometrics OR device credentials (PIN/Pattern/Password)
        val status = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the NATIVE system prompt (matches Google Pay style).
     * This will ask for Fingerprint/Face first, and if unavailable/failed, allow falling back to Device PIN.
     */
    fun showNativeAuthPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // We only call error if it's a real failure, not a user cancellation
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Potty")
            .setSubtitle("Use your phone's fingerprint, face, or PIN to continue")
            // Allowing DEVICE_CREDENTIAL is what enables the PIN/Pattern fallback like GPay
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
