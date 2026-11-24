package com.example.ui.admin

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val ADMIN_PREFS_NAME = "pizza_club_admin_prefs"
private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"

fun isBiometricLockEnabled(context: Context): Boolean =
    context.getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)

fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, enabled)
        .apply()
}

fun canUseBiometricAuth(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    val result = manager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    return result == BiometricManager.BIOMETRIC_SUCCESS
}

fun launchBiometricAuth(
    context: Context,
    title: String = "Desbloquear Panel Administrativo",
    subtitle: String = "Usá Face ID / Reconocimiento facial o huella registrada",
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onCancelled: () -> Unit = {}
) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        onError("Este dispositivo no pudo iniciar el lector biométrico en esta pantalla.")
        return
    }
    if (!canUseBiometricAuth(context)) {
        onError("No hay Face ID / desbloqueo facial o huella configurada en este dispositivo.")
        return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onCancelled()
                } else {
                    onError(errString.toString())
                }
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .setNegativeButtonText("Usar Google / Apple")
        .build()

    prompt.authenticate(promptInfo)
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Configuración de Google Sign-In faltante (default_web_client_id).")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity
                ?: run {
                    onAuthError("Se requiere contexto de Activity para iniciar sesión.")
                    return@launch
                }
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Tipo de credencial inesperado.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "No se pudo iniciar sesión con Google.")
        }
    }
}

fun onAppleSignInClicked(
    context: Context,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Se requiere contexto de Activity para iniciar sesión con Apple.")
        return
    }

    val provider = OAuthProvider.newBuilder("apple.com")
        .setScopes(listOf("email", "name"))
        .build()

    scope.launch {
        try {
            val pendingTask = Firebase.auth.pendingAuthResult
            if (pendingTask != null) {
                pendingTask.await()
            } else {
                Firebase.auth.startActivityForSignInWithProvider(activity, provider).await()
            }
            onAuthSuccess()
        } catch (e: FirebaseAuthException) {
            Log.w("Auth", "Apple Sign-In error: ${e.errorCode} ${e.message}", e)
            when (e.errorCode) {
                "ERROR_WEB_CONTEXT_CANCELED" -> onAuthCancelled()
                "ERROR_OPERATION_NOT_ALLOWED" -> onAuthError(
                    "Activá 'Apple' en Firebase Console → Authentication → Sign-in method para usar Apple Account."
                )
                else -> onAuthError(e.localizedMessage ?: "No se pudo iniciar sesión con Apple.")
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage.orEmpty()
            if (msg.contains("cancel", ignoreCase = true)) {
                onAuthCancelled()
            } else {
                onAuthError(msg.ifBlank { "No se pudo iniciar sesión con Apple." })
            }
        }
    }
}

fun signOutAdmin(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
