package com.example.ui.customer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.BusinessSettingsModel
import com.example.data.instagramProfileUrl
import com.example.ui.components.GoogleLogoIcon
import com.example.ui.components.InstagramLogoIcon
import com.example.ui.components.QrBoxScannerView
import com.example.ui.components.QrCodeCard
import com.example.ui.components.SocialSection
import com.example.ui.components.WhatsAppLogoIcon
import com.example.ui.theme.CheeseContainer
import com.example.ui.theme.CheeseSecondary
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.OnCheeseContainer
import com.example.ui.theme.TomatoPrimary
import com.example.ui.theme.WhatsAppGreen
import java.net.URLEncoder
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialAndTreasureBottomSheet(
    settings: BusinessSettingsModel,
    initialTab: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = settings.brandName,
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Premio de tu Caja") },
                    icon = { Icon(Icons.Rounded.CardGiftcard, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Redes, iPhone y QR") },
                    icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                SurpriseBoxRewardExperience(settings = settings)
            } else {
                SocialAndQrHub(settings = settings)
            }
        }
    }
}

@Composable
private fun SurpriseBoxRewardExperience(settings: BusinessSettingsModel) {
    val context = LocalContext.current
    var unlockedPrizeCode by remember { mutableStateOf<String?>(null) }
    var codeError by remember { mutableStateOf<String?>(null) }

    val expectedCode = settings.treasureCode.trim().uppercase().ifBlank { "PIZZACLUB-ORO" }
    val giftScale by animateFloatAsState(
        targetValue = if (unlockedPrizeCode != null) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "giftScale"
    )

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = EspressoDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(EspressoDark, Color(0xFF2D1E16))
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = CheeseSecondary,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).scale(giftScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (unlockedPrizeCode != null) Icons.Rounded.AutoAwesome else Icons.Rounded.CardGiftcard,
                            contentDescription = null,
                            tint = EspressoDark
                        )
                    }
                }
                Column {
                    Text(
                        text = "Dentro de cada pizza hay un premio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CheeseSecondary
                    )
                    Text(
                        text = "Sorpresa exclusiva en tu caja",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Text(
                text = "Escaneá directamente el QR impreso dentro de tu caja de ${settings.brandName} para descubrir tu regalo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
        }
    }

    if (unlockedPrizeCode == null) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QrBoxScannerView(
                    onQrScanned = { rawQr ->
                        val normalized = rawQr.trim().uppercase()
                        if (normalized.contains(expectedCode) || normalized.contains("PIZZACLUB")) {
                            codeError = null
                            unlockedPrizeCode = "PC-PREMIO-${Random.nextInt(1000, 9999)}"
                        } else {
                            codeError = "Este QR no pertenece a una caja de ${settings.brandName}. Intentá con el QR de tu caja."
                        }
                    },
                    onScanError = { msg -> codeError = msg }
                )

                if (!codeError.isNullOrBlank()) {
                    Text(
                        text = codeError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    } else {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn() + fadeIn()
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = CheeseContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, CheeseSecondary, MaterialTheme.shapes.extraLarge)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CardGiftcard,
                        contentDescription = null,
                        tint = OnCheeseContainer,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "¡PREMIO SORPRESA DESBLOQUEADO!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = OnCheeseContainer,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = settings.treasureReward,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TomatoPrimary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Cupón: $unlockedPrizeCode",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = EspressoDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                    Button(
                        onClick = {
                            val claimMsg = buildString {
                                appendLine("🎁 *¡Descubrí el premio sorpresa de ${settings.brandName}!*")
                                appendLine("Código de caja: $expectedCode")
                                appendLine("Cupón: *$unlockedPrizeCode*")
                                appendLine("Premio: ${settings.treasureReward}")
                                append("¡Quiero canjearlo en mi próximo pedido!")
                            }
                            val url = "https://wa.me/${settings.whatsappNumber}?text=${URLEncoder.encode(claimMsg, "UTF-8")}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        WhatsAppLogoIcon(size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Canjear por WhatsApp")
                    }

                    // Gentle optional Google Review invitation inside the box surprise
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.googleReviewUrl)))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Buscá ${settings.brandName} en Google", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_google_review_button"),
                        border = BorderStroke(1.dp, OnCheeseContainer.copy(alpha = 0.3f))
                    ) {
                        GoogleLogoIcon(size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Contanos tu experiencia en Google", color = EspressoDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialAndQrHub(settings: BusinessSettingsModel) {
    val context = LocalContext.current
    val waUrl = "https://wa.me/${settings.whatsappNumber}"
    val igUrl = instagramProfileUrl(settings.instagramHandle)
    val installUrl = settings.pwaInstallUrl.trim().ifBlank { "https://gen-lang-client-0684931110.web.app" }

    SocialSection(
        instagramHandle = settings.instagramHandle,
        whatsappNumber = settings.whatsappNumber,
        brandName = settings.brandName
    )

    // iPhone (iOS Safari) + Cross-platform PWA Install QR Card
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = EspressoDark),
        border = BorderStroke(1.5.dp, CheeseSecondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.PhoneIphone, contentDescription = null, tint = CheeseSecondary)
                Text(
                    text = "Instalar en iPhone (iOS) y Web",
                    style = MaterialTheme.typography.titleMedium,
                    color = CheeseSecondary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QrCodeCard(
                    content = installUrl,
                    contentDescription = "QR para instalar ${settings.brandName} en iPhone y Android",
                    size = 112.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "1. Escaneá con la cámara de tu iPhone (Safari).\n2. Tocá Compartir (⬆️).\n3. Elegí \"Agregar a pantalla de inicio\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Button(
                        onClick = {
                            val shareText = "🍕 ¡Pedí en ${settings.brandName} (${settings.deliveryCity}) sin registrarte! Abrí o instalá en iPhone / Android: $installUrl · IG: ${settings.instagramHandle}"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "${settings.brandName} App")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir app de ${settings.brandName}"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("share_cross_platform_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CheeseSecondary,
                            contentColor = EspressoDark
                        )
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir Link")
                    }
                }
            }
        }
    }

    // Instagram Card with Scannable QR & Official Logo
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QrCodeCard(
                content = igUrl,
                contentDescription = "QR de Instagram ${settings.instagramHandle}",
                size = 112.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstagramLogoIcon(size = 22.dp)
                    Text("Instagram Oficial", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "${settings.instagramHandle} · Novedades, sabores artesanales y sorpresas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(igUrl)))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TomatoPrimary, contentColor = Color.White)
                ) {
                    Text("Seguir en Instagram")
                }
            }
        }
    }

    // WhatsApp Direct QR & Link Card with Official Logo
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QrCodeCard(
                content = waUrl,
                contentDescription = "QR de WhatsApp Pizza Club",
                size = 112.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhatsAppLogoIcon(size = 22.dp)
                    Text("WhatsApp Directo", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "Escaneá o tocá para consultas rápidas y pedidos en ${settings.deliveryCity}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Abrir Chat", color = WhatsAppGreen)
                }
            }
        }
    }

    // Google Reviews Scannable QR Card with Official Google Logo
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CheeseContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QrCodeCard(
                content = settings.googleReviewUrl,
                contentDescription = "QR de Google para ${settings.brandName}",
                size = 112.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoogleLogoIcon(size = 20.dp)
                    Text("Pizza Club en Google", style = MaterialTheme.typography.titleMedium, color = OnCheeseContainer)
                }
                Text(
                    text = "Encontranos en Google Maps y compartí tu opinión sobre nuestras pizzas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnCheeseContainer
                )
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.googleReviewUrl)))
                        } catch (_: Exception) { }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EspressoDark, contentColor = Color.White)
                ) {
                    Text("Ver en Google")
                }
            }
        }
    }
}
