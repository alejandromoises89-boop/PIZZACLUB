package com.example.ui.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.FulfillmentType
import com.example.data.formatPyg
import com.example.viewmodel.CatalogUiState
import com.example.viewmodel.CheckoutFormState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    catalogState: CatalogUiState.Ready,
    form: CheckoutFormState,
    isSubmitting: Boolean,
    submitError: String?,
    onUpdateForm: ((CheckoutFormState) -> CheckoutFormState) -> Unit,
    onSubmitOrder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val selectedItems = catalogState.items.filter { it.cartQuantity > 0 }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Confirmar Preorden", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("checkout_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver al menú")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Order Summary Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumen de tu pedido", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Preparación: ${form.preparationMode.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    selectedItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.cartQuantity}x ${item.product.name}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatPyg(item.product.pricePyg * item.cartQuantity),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total a transferir", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = formatPyg(catalogState.subtotalPyg),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Delivery or Pickup Switch
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modalidad de entrega", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FulfillmentType.entries.forEach { type ->
                        val isSelected = form.fulfillmentType == type
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .clickable { onUpdateForm { it.copy(fulfillmentType = type) } }
                                .testTag("fulfillment_${type.rawValue.lowercase()}"),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (type == FulfillmentType.DELIVERY) Icons.Rounded.DeliveryDining else Icons.Rounded.Storefront,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (type == FulfillmentType.DELIVERY) "Delivery" else "Retiro en local",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Customer Info Fields (No registration required)
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Tus datos (sin registro)", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = form.customerName,
                        onValueChange = { v -> onUpdateForm { it.copy(customerName = v) } },
                        label = { Text("Nombre y apellido *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_name_input")
                    )

                    OutlinedTextField(
                        value = form.customerPhone,
                        onValueChange = { v -> onUpdateForm { it.copy(customerPhone = v) } },
                        label = { Text("Teléfono / WhatsApp *") },
                        placeholder = { Text("Ej: 0981 123 456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_phone_input")
                    )

                    if (form.fulfillmentType == FulfillmentType.DELIVERY) {
                        OutlinedTextField(
                            value = form.desiredDate,
                            onValueChange = { v -> onUpdateForm { it.copy(desiredDate = v) } },
                            label = { Text("Fecha deseada de entrega *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = form.neighborhood,
                            onValueChange = { v -> onUpdateForm { it.copy(neighborhood = v) } },
                            label = { Text("Barrio (${catalogState.settings.deliveryCity}) *") },
                            placeholder = { Text("Ej: Área 1, Boquerón, Paraná Country Club") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_neighborhood_input")
                        )
                        OutlinedTextField(
                            value = form.streetAndNumber,
                            onValueChange = { v -> onUpdateForm { it.copy(streetAndNumber = v) } },
                            label = { Text("Calle y número de casa *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkout_street_input")
                        )
                        OutlinedTextField(
                            value = form.deliveryReference,
                            onValueChange = { v -> onUpdateForm { it.copy(deliveryReference = v) } },
                            label = { Text("Referencia para el repartidor") },
                            placeholder = { Text("Ej: Portón negro, frente a la plaza") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = form.googleMapsLink,
                            onValueChange = { v -> onUpdateForm { it.copy(googleMapsLink = v) } },
                            label = { Text("Link de ubicación Google Maps (opcional)") },
                            placeholder = { Text("https://maps.google.com/...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = form.pickupDate,
                            onValueChange = { v -> onUpdateForm { it.copy(pickupDate = v, desiredDate = v) } },
                            label = { Text("Fecha preferida de retiro *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = form.pickupTime,
                            onValueChange = { v -> onUpdateForm { it.copy(pickupTime = v) } },
                            label = { Text("Horario preferido de retiro *") },
                            placeholder = { Text("Ej: 20:00 hs") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = form.additionalInstructions,
                        onValueChange = { v -> onUpdateForm { it.copy(additionalInstructions = v) } },
                        label = { Text("Instrucciones adicionales (opcional)") },
                        placeholder = { Text("Ej: Sin orégano, tocar timbre") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!submitError.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = submitError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Button(
                onClick = onSubmitOrder,
                enabled = !isSubmitting && selectedItems.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_preorder_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Generar Preorden y Ver Datos de Pago",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
