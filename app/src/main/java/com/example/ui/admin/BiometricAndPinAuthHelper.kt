package com.example.ui.admin

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.BuildConfig

private const val ADMIN_PREFS_NAME = "pizza_club_admin_prefs"
private const val KEY_BIOMETRIC_ENABLED = "admin_biometric_lock_enabled"
private const val KEY_CUSTOM_PIN = "admin_custom_pin"
private const val KEY_ADMIN_AUTHENTICATED = "admin_pin_verified_once"

fun getEffectiveAdminPin(context: Context): String {
    val prefs = context.applicationContext.getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_CUSTOM_PIN, "")?.trim().orEmpty()
    if (saved.length >= 4) return saved
    val envPin = BuildConfig.ADMIN_PIN.trim()
    return if (envPin.length >= 4) envPin else "2026"
}

fun saveCustomAdminPin(context: Context, newPin: String) {
    val clean = newPin.trim()
    if (clean.length < 4) return
    context.applicationContext
        .getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CUSTOM_PIN, clean)
        .apply()
}

fun hasVerifiedPinOnDevice(context: Context): Boolean {
    return context.applicationContext
        .getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ADMIN_AUTHENTICATED, false)
}

fun setVerifiedPinOnDevice(context: Context, verified: Boolean) {
    context.applicationContext
        .getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ADMIN_AUTHENTICATED, verified)
        .apply()
}

fun isBiometricLockEnabled(context: Context): Boolean {
    return context.applicationContext
        .getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_BIOMETRIC_ENABLED, false)
}

fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
        .apply()
}

fun canUseBiometricAuth(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    val result = manager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    return result == BiometricManager.BIOMETRIC_SUCCESS
}

fun launchBiometricAuth(
    context: Context,
    title: String = "Desbloquear Panel Admin",
    subtitle: String = "Usá Face ID / reconocimiento facial o huella dactilar",
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onCancelled: () -> Unit = {}
) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        onError("No se pudo iniciar el lector biométrico en esta pantalla.")
        return
    }
    if (!canUseBiometricAuth(context)) {
        onError("Este dispositivo no tiene Face ID ni huella configurada.")
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            ) {
                onCancelled()
            } else {
                onError(errString.toString())
            }
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Usar PIN")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .build()

    prompt.authenticate(promptInfo)
}
