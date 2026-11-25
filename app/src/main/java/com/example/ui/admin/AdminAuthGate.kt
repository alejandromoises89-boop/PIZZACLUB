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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.PizzaClubRepository
import com.example.data.SupabaseRestClient
import com.example.ui.components.BiometricSignInActionButton
import com.example.viewmodel.AdminViewModel

@Composable
fun AdminAuthGate(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var pinInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var hasPinSession by remember(context) { mutableStateOf(hasVerifiedPinOnDevice(context)) }
    var biometricLockEnabled by remember(context) { mutableStateOf(isBiometricLockEnabled(context)) }

    // If biometric lock was previously enabled on this device, offer Face ID immediately
    LaunchedEffect(Unit) {
        if (hasPinSession && biometricLockEnabled && canUseBiometricAuth(context)) {
            launchBiometricAuth(
                context = context,
                onSuccess = {
                    authError = null
                    isAuthenticated = true
                },
                onError = { msg -> authError = msg },
                onCancelled = {}
            )
        }
    }

    if (!isAuthenticated) {
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
                        text = "Ingresá tu PIN de administrador o usá Face ID / Biometría para confirmar transferencias Itaú y gestionar pedidos.",
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

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter { ch -> ch.isDigit() }.take(8)
                            authError = null
                        },
                        label = { Text("PIN de administrador") },
                        placeholder = { Text("••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_pin_input")
                    )

                    Button(
                        onClick = {
                            val expectedPin = getEffectiveAdminPin(context)
                            if (pinInput.trim() == expectedPin) {
                                authError = null
                                setVerifiedPinOnDevice(context, true)
                                hasPinSession = true
                                isAuthenticated = true
                                pinInput = ""
                            } else {
                                authError = "PIN incorrecto. Verificá tu código de administrador."
                            }
                        },
                        enabled = pinInput.length >= 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_pin_submit_button")
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Entrar al Panel")
                    }

                    if (canUseBiometricAuth(context)) {
                        BiometricSignInActionButton(
                            label = if (hasPinSession) {
                                "Desbloquear con Face ID / Biometría"
                            } else {
                                "Activar Face ID / Biometría"
                            },
                            isLoading = false,
                            onClick = {
                                authError = null
                                if (!hasPinSession) {
                                    authError = "Ingresá primero una vez con tu PIN para vincular Face ID / Biometría."
                                } else {
                                    launchBiometricAuth(
                                        context = context,
                                        onSuccess = {
                                            authError = null
                                            isAuthenticated = true
                                        },
                                        onError = { msg -> authError = msg },
                                        onCancelled = {}
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    } else {
        val adminViewModel: AdminViewModel = viewModel(
            key = "admin_local",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    AdminViewModel(PizzaClubRepository(app), "admin_local")
                }
            }
        )
        val cloudConnected = remember(context, isAuthenticated) {
            SupabaseRestClient(context).isConfigured()
        }
        AdminDashboardScreen(
            viewModel = adminViewModel,
            adminEmail = if (cloudConnected) "Admin · Base Online conectada" else "Admin · Modo Local (Room)",
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
                    isAuthenticated = false
                }
            },
            onSignOut = {
                isAuthenticated = false
                pinInput = ""
            },
            modifier = modifier
        )
    }
}
