package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FulfillmentType
import com.example.data.OrderStatus
import com.example.data.formatOrderCountdownLabel
import com.example.data.formatPyg
import kotlinx.coroutines.delay
import com.example.ui.theme.CheeseContainer
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoBlueContainer
import com.example.ui.theme.OnCheeseContainer
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.TomatoPrimary
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer

@DrawableRes
fun pizzaDrawableForKey(imageKey: String): Int = when (imageKey) {
    "pepperoni" -> R.drawable.img_pizza_pepperoni
    "mozzarella" -> R.drawable.img_pizza_mozzarella
    "four_cheeses" -> R.drawable.img_pizza_four_cheeses
    "chicken_catupiry" -> R.drawable.img_pizza_chicken_catupiry
    else -> R.drawable.img_hero_pizzeria
}

@Composable
fun OrderStatusBadge(
    status: OrderStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (status) {
        OrderStatus.PENDING_PAYMENT -> Triple(PendingAmberContainer, PendingAmber, Icons.Rounded.Schedule)
        OrderStatus.PAYMENT_CONFIRMED -> Triple(WhatsAppGreenContainer, WhatsAppGreen, Icons.Rounded.Verified)
        OrderStatus.PREPARING -> Triple(CheeseContainer, OnCheeseContainer, Icons.Rounded.LocalFireDepartment)
        OrderStatus.READY_FOR_PICKUP -> Triple(InfoBlueContainer, InfoBlue, Icons.Rounded.Storefront)
        OrderStatus.OUT_FOR_DELIVERY -> Triple(InfoBlueContainer, InfoBlue, Icons.Rounded.DeliveryDining)
        OrderStatus.COMPLETED -> Triple(WhatsAppGreenContainer, WhatsAppGreen, Icons.Rounded.CheckCircle)
        OrderStatus.CANCELLED -> Triple(DangerRedContainer, DangerRed, Icons.Rounded.Cancel)
    }

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = status.labelEs,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OrderProgressStepper(
    status: OrderStatus,
    fulfillmentType: FulfillmentType,
    modifier: Modifier = Modifier,
    stageUpdatedAtMillis: Long = System.currentTimeMillis()
) {
    if (status == OrderStatus.CANCELLED) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = DangerRedContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Cancel, contentDescription = null, tint = DangerRed)
                Text(
                    text = "Este pedido fue cancelado.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = DangerRed
                )
            }
        }
        return
    }

    val steps = listOf(
        Triple("Preorden", Icons.AutoMirrored.Rounded.ReceiptLong, "Recibida"),
        Triple("Pago OK", Icons.Rounded.Verified, "Itaú OK"),
        Triple("Horno", Icons.Rounded.LocalFireDepartment, "Masa madre"),
        Triple(
            if (fulfillmentType == FulfillmentType.DELIVERY) "En camino" else "Retiro",
            if (fulfillmentType == FulfillmentType.DELIVERY) Icons.Rounded.DeliveryDining else Icons.Rounded.Storefront,
            if (fulfillmentType == FulfillmentType.DELIVERY) "Delivery" else "En local"
        ),
        Triple("Listo", Icons.Rounded.CheckCircle, "Disfrutalo")
    )
    val currentStep = status.stepIndex.coerceIn(0, 4)
    val animatedProgress by animateFloatAsState(
        targetValue = (currentStep + 1) / 5f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "orderProgress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasActiveCountdown = status == OrderStatus.PAYMENT_CONFIRMED ||
        status == OrderStatus.PREPARING ||
        status == OrderStatus.OUT_FOR_DELIVERY
    LaunchedEffect(status, stageUpdatedAtMillis) {
        nowMillis = System.currentTimeMillis()
        while (hasActiveCountdown) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val liveEtaCopy = formatOrderCountdownLabel(
        status = status,
        fulfillmentType = fulfillmentType,
        stageStartedAtMillis = stageUpdatedAtMillis,
        nowMillis = nowMillis
    )

    val accentColor = if (currentStep >= 1) WhatsAppGreen else TomatoPrimary

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = if (currentStep >= 1) WhatsAppGreenContainer else CheeseContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = if (currentStep >= 1) WhatsAppGreen else OnCheeseContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = liveEtaCopy,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (currentStep >= 1) WhatsAppGreen else OnCheeseContainer
                )
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            steps.forEachIndexed { index, (label, icon, sub) ->
                val completed = index < currentStep || (index == 4 && currentStep == 4)
                val isActive = index == currentStep && currentStep < 4
                val reached = index <= currentStep
                val dotColor = if (reached) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .scale(if (isActive) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(dotColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (completed) Icons.Rounded.Check else icon,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ItauTransferCard(
    bankName: String,
    paymentAlias: String,
    totalAmountPyg: Long,
    orderNumber: String,
    onCopyAlias: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = CheeseContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalance,
                            contentDescription = "Banco para transferencia",
                            tint = OnCheeseContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = "Datos para Transferencia Bancaria",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pedido $orderNumber",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            TransferDetailRow("Banco", bankName)
            Spacer(Modifier.height(6.dp))
            TransferDetailRow("Alias", paymentAlias)
            Spacer(Modifier.height(6.dp))
            TransferDetailRow("Monto exacto", formatPyg(totalAmountPyg), highlight = true)

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = onCopyAlias,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("copy_alias_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copiar alias ($paymentAlias)", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TransferDetailRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun GoogleSignInActionButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("google_sign_in_button"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = "Iniciar sesión con Google",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun AppleSignInActionButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("apple_sign_in_button"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF111111),
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.PhoneIphone,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Iniciar sesión con Apple",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun BiometricSignInActionButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("biometric_sign_in_button"),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(
            imageVector = Icons.Rounded.Face,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MetricPill(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}
