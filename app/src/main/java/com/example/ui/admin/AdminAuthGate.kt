package com.example.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.PizzaClubRepository
import com.example.ui.components.AppleSignInActionButton
import com.example.ui.components.BiometricSignInActionButton
import com.example.ui.components.GoogleSignInActionButton
import com.example.viewmodel.AdminViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AdminAuthGate(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }
    var isCheckingAdmin by remember { mutableStateOf(false) }
    var isAuthorizedAdmin by remember { mutableStateOf(false) }
    var biometricLockEnabled by remember(context) { mutableStateOf(isBiometricLockEnabled(context)) }
    var isBiometricUnlocked by remember(context) {
        mutableStateOf(!isBiometricLockEnabled(context))
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    // Silent check on first entry into Admin tab (if biometric lock is disabled)
    LaunchedEffect(Unit) {
        if (currentUser == null && !biometricLockEnabled) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isBiometricUnlocked = true
                    currentUser = Firebase.auth.currentUser
                },
                onUnauthenticated = { /* Show interactive Sign-In buttons */ },
                scope = scope
            )
        }
    }

    val user = currentUser
    LaunchedEffect(user?.uid) {
        if (user == null) {
            isAuthorizedAdmin = false
            isCheckingAdmin = false
        } else {
            isCheckingAdmin = true
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            val repo = PizzaClubRepository(FirebaseFirestore.getInstance(dbId))
            isAuthorizedAdmin = repo.verifyIsAdmin()
            isCheckingAdmin = false
            if (isAuthorizedAdmin && biometricLockEnabled && !isBiometricUnlocked && canUseBiometricAuth(context)) {
                launchBiometricAuth(
                    context = context,
                    onSuccess = {
                        authError = null
                        isBiometricUnlocked = true
                    },
                    onError = { msg -> authError = msg },
                    onCancelled = {}
                )
            }
        }
    }

    if (user == null || (isAuthorizedAdmin && biometricLockEnabled && !isBiometricUnlocked)) {
        val hasSavedSession = user != null
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 420.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "Panel Administrativo",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (hasSavedSession) {
                            "Sesión vinculada (${user?.email ?: "Admin"}). Desbloqueá con Face ID / Biometría o cambiá de cuenta con Google o Apple."
                        } else {
                            "Iniciá sesión con Google, Apple o Face ID / Biometría para confirmar transferencias Itaú, gestionar stock y ver métricas."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!authError.isNullOrBlank()) {
                        Text(
                            text = authError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    BiometricSignInActionButton(
                        label = if (hasSavedSession) {
                            "Desbloquear con Face ID / Biometría"
                        } else {
                            "Iniciar con Face ID / Biometría"
                        },
                        isLoading = isSigningIn,
                        onClick = {
                            authError = null
                            if (!hasSavedSession) {
                                authError = if (canUseBiometricAuth(context)) {
                                    "Iniciá sesión una primera vez con Google o Apple y tocá el ícono de candado/rostro arriba para vincular Face ID / Biometría."
                                } else {
                                    "Configurá reconocimiento facial o huella en los ajustes de tu teléfono e iniciá sesión primero con Google o Apple."
                                }
                            } else {
                                launchBiometricAuth(
                                    context = context,
                                    onSuccess = {
                                        authError = null
                                        isBiometricUnlocked = true
                                    },
                                    onError = { msg -> authError = msg },
                                    onCancelled = {}
                                )
                            }
                        }
                    )

                    GoogleSignInActionButton(
                        isLoading = isSigningIn,
                        onClick = {
                            authError = null
                            isSigningIn = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isSigningIn = false
                                    isBiometricUnlocked = true
                                    currentUser = Firebase.auth.currentUser
                                },
                                onAuthError = { msg ->
                                    isSigningIn = false
                                    authError = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isSigningIn = false
                                }
                            )
                        }
                    )

                    AppleSignInActionButton(
                        isLoading = isSigningIn,
                        onClick = {
                            authError = null
                            isSigningIn = true
                            onAppleSignInClicked(
                                context = context,
                                onAuthSuccess = {
                                    isSigningIn = false
                                    isBiometricUnlocked = true
                                    currentUser = Firebase.auth.currentUser
                                },
                                onAuthError = { msg ->
                                    isSigningIn = false
                                    authError = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isSigningIn = false
                                }
                            )
                        }
                    )

                    if (hasSavedSession) {
                        OutlinedButton(
                            onClick = {
                                setBiometricLockEnabled(context, false)
                                biometricLockEnabled = false
                                isBiometricUnlocked = false
                                signOutAdmin(credentialManager, scope) {
                                    currentUser = null
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("admin_locked_signout")
                        ) {
                            Text("Cerrar sesión actual")
                        }
                    }
                }
            }
        }
    } else if (isCheckingAdmin) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (!isAuthorizedAdmin) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 420.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockPerson,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(52.dp)
                    )
                    Text("Acceso Restringido", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "La cuenta ${user.email ?: user.uid} no tiene permisos de administrador.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = {
                            signOutAdmin(credentialManager, scope) {
                                currentUser = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_unauthorized_signout")
                    ) {
                        Text("Cerrar sesión e intentar con otra cuenta")
                    }
                }
            }
        }
    } else {
        val uid = user.uid
        key(uid) {
            val adminViewModel: AdminViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        AdminViewModel(PizzaClubRepository(db), uid)
                    }
                }
            )
            AdminDashboardScreen(
                viewModel = adminViewModel,
                adminEmail = user.email ?: "Admin",
                isBiometricEnabled = biometricLockEnabled,
                onToggleBiometricLock = {
                    if (!biometricLockEnabled) {
                        if (!canUseBiometricAuth(context)) {
                            Toast.makeText(
                                context,
                                "Configurá rostro o huella en tu teléfono para activar este bloqueo.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            launchBiometricAuth(
                                context = context,
                                title = "Activar Face ID / Biometría",
                                subtitle = "Confirmá tu identidad para proteger el Panel Admin",
                                onSuccess = {
                                    setBiometricLockEnabled(context, true)
                                    biometricLockEnabled = true
                                    isBiometricUnlocked = true
                                    Toast.makeText(
                                        context,
                                        "Desbloqueo con Face ID / Biometría activado.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                        isBiometricUnlocked = false
                    }
                },
                onSignOut = {
                    setBiometricLockEnabled(context, false)
                    biometricLockEnabled = false
                    isBiometricUnlocked = false
                    signOutAdmin(credentialManager, scope) {
                        currentUser = null
                    }
                },
                modifier = modifier
            )
        }
    }
}
