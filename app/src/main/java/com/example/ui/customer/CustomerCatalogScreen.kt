package com.example.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PreparationMode
import com.example.data.formatPyg
import com.example.ui.components.MetricPill
import com.example.ui.components.SocialSection
import com.example.ui.components.pizzaDrawableForKey
import com.example.ui.theme.CheeseContainer
import com.example.ui.theme.CheeseSecondary
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.OnCheeseContainer
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer
import com.example.viewmodel.CatalogItemUi
import com.example.viewmodel.CatalogUiState

@Composable
fun CustomerCatalogScreen(
    uiState: CatalogUiState,
    selectedPreparationMode: PreparationMode,
    onSelectPreparationMode: (PreparationMode) -> Unit,
    onIncrement: (String, Long) -> Unit,
    onDecrement: (String) -> Unit,
    onProceedToCheckout: () -> Unit,
    onOpenSocialQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is CatalogUiState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is CatalogUiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is CatalogUiState.Ready -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val columns = if (maxWidth >= 680.dp) 2 else 1
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = if (uiState.totalPizzas > 0) 104.dp else 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1000.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        item(span = { GridItemSpan(columns) }) {
                            PizzeriaHeroBanner(
                                brandName = uiState.settings.brandName,
                                city = uiState.settings.deliveryCity,
                                instagram = uiState.settings.instagramHandle,
                                acceptingOrders = uiState.settings.acceptingOrders
                            )
                        }

                        item(span = { GridItemSpan(columns) }) {
                            SocialSection(
                                instagramHandle = uiState.settings.instagramHandle,
                                whatsappNumber = uiState.settings.whatsappNumber,
                                brandName = uiState.settings.brandName,
                                onOpenQrHub = onOpenSocialQr
                            )
                        }

                        item(span = { GridItemSpan(columns) }) {
                            PreparationModeSelector(
                                selected = selectedPreparationMode,
                                onSelect = onSelectPreparationMode
                            )
                        }

                        item(span = { GridItemSpan(columns) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Nuestros Sabores",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "Tamaño único artesanal",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        items(uiState.items, key = { it.product.id }) { item ->
                            PizzaProductCard(
                                item = item,
                                acceptingOrders = uiState.settings.acceptingOrders,
                                onIncrement = { onIncrement(item.product.id, item.stock) },
                                onDecrement = { onDecrement(item.product.id) }
                            )
                        }
                    }

                    // Floating Cart Summary Bar
                    AnimatedVisibility(
                        visible = uiState.totalPizzas > 0,
                        enter = slideInVertically { it } + fadeIn(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .widthIn(max = 600.dp)
                    ) {
                        FloatingCartBar(
                            totalPizzas = uiState.totalPizzas,
                            subtotalPyg = uiState.subtotalPyg,
                            onProceedToCheckout = onProceedToCheckout
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PizzeriaHeroBanner(
    brandName: String,
    city: String,
    instagram: String,
    acceptingOrders: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
            .clip(MaterialTheme.shapes.extraLarge)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_pizzeria),
            contentDescription = "Pizzas artesanales de $brandName",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            EspressoDark.copy(alpha = 0.25f),
                            EspressoDark.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricPill(
                    icon = Icons.Rounded.LocationOn,
                    text = "$city · Alto Paraná",
                    containerColor = EspressoDark.copy(alpha = 0.72f),
                    contentColor = Color.White
                )
                MetricPill(
                    icon = Icons.Rounded.LocalFireDepartment,
                    text = if (acceptingOrders) "Tomando pedidos" else "Pausado",
                    containerColor = if (acceptingOrders) CheeseSecondary else DangerRedContainer,
                    contentColor = if (acceptingOrders) EspressoDark else DangerRed
                )
            }

            Column {
                Text(
                    text = brandName,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Masa madre artesanal · Sin registro ni login · $instagram",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PreparationModeSelector(
    selected: PreparationMode,
    onSelect: (PreparationMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "¿Cómo preferís tus pizzas?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PreparationMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.medium
                        )
                        .clickable { onSelect(mode) }
                        .testTag("prep_mode_${mode.name.lowercase()}"),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (mode == PreparationMode.READY_TO_EAT) Icons.Rounded.LocalFireDepartment else Icons.Rounded.Restaurant,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = mode.shortDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PizzaProductCard(
    item: CatalogItemUi,
    acceptingOrders: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val product = item.product
    val outOfStock = item.stock <= 0L
    val lowStock = item.stock in 1L..item.lowStockThreshold

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            ) {
                Image(
                    painter = painterResource(id = pizzaDrawableForKey(product.imageKey)),
                    contentDescription = "Pizza ${product.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                val (badgeBg, badgeFg, badgeText) = when {
                    outOfStock -> Triple(DangerRedContainer, DangerRed, "Agotado")
                    lowStock -> Triple(PendingAmberContainer, PendingAmber, "¡Solo ${item.stock} disp.!")
                    else -> Triple(WhatsAppGreenContainer, WhatsAppGreen, "${item.stock} disponibles")
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (item.cartQuantity > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${item.cartQuantity}x",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = formatPyg(product.pricePyg),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Por unidad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = onDecrement,
                                enabled = item.cartQuantity > 0,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("dec_${product.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Remove,
                                    contentDescription = "Quitar ${product.name}",
                                    tint = if (item.cartQuantity > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Text(
                            text = "${item.cartQuantity}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(24.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val canAdd = acceptingOrders && !outOfStock && item.cartQuantity < item.stock
                        Surface(
                            color = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = onIncrement,
                                enabled = canAdd,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("inc_${product.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Agregar ${product.name}",
                                    tint = if (canAdd) Color.White else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingCartBar(
    totalPizzas: Int,
    subtotalPyg: Long,
    onProceedToCheckout: () -> Unit
) {
    Surface(
        color = EspressoDark,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = CheeseSecondary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ShoppingBag,
                            contentDescription = null,
                            tint = EspressoDark
                        )
                    }
                }
                Column {
                    Text(
                        text = "$totalPizzas ${if (totalPizzas == 1) "pizza" else "pizzas"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Text(
                        text = formatPyg(subtotalPyg),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CheeseSecondary
                    )
                }
            }

            Button(
                onClick = onProceedToCheckout,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("proceed_checkout_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Hacer Pedido")
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
