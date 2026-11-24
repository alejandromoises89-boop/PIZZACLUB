package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.DefaultCatalogDefaults
import com.example.data.instagramProfileUrl
import java.net.URLEncoder

@Composable
fun SocialSection(
    instagramHandle: String,
    whatsappNumber: String,
    brandName: String = DefaultCatalogDefaults.defaultSettings.brandName,
    onOpenQrHub: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openExternalUrl(url: String, fallbackToast: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            if (onOpenQrHub != null) {
                onOpenQrHub()
            } else {
                Toast.makeText(context, fallbackToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val igUrl = instagramProfileUrl(instagramHandle)
    val cleanWa = whatsappNumber.filter { it.isDigit() }
        .ifBlank { DefaultCatalogDefaults.defaultSettings.whatsappNumber }
    val supportGreeting = URLEncoder.encode(
        "¡Hola $brandName! Te escribo desde la app para consultar sobre un pedido 🍕",
        "UTF-8"
    )
    val waUrl = "https://wa.me/$cleanWa?text=$supportGreeting"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("social_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Seguinos o escribinos",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onOpenQrHub != null) {
                TextButton(
                    onClick = onOpenQrHub,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("social_section_qr_hub")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = "Ver QR e instalar en iPhone",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("QR y iPhone")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Instagram Button with Official Brand Logo
            Surface(
                onClick = { openExternalUrl(igUrl, "Instagram: $instagramHandle") },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Seguir en Instagram $instagramHandle"
                    }
                    .testTag("social_section_instagram")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InstagramLogoIcon(size = 30.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Instagram",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = instagramHandle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // WhatsApp Support Button with Official Brand Logo
            Surface(
                onClick = { openExternalUrl(waUrl, "WhatsApp: +$cleanWa") },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.45f)),
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Contactar soporte por WhatsApp"
                    }
                    .testTag("social_section_whatsapp")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WhatsAppLogoIcon(size = 30.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WhatsApp",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF157A3D),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Soporte directo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstagramLogoIcon(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val gradient = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFEDA75),
                    Color(0xFFFA7E1E),
                    Color(0xFFD62976),
                    Color(0xFF962FBF),
                    Color(0xFF4F5BD5)
                ),
                start = Offset(0f, h),
                end = Offset(w, 0f)
            )
            // Outer rounded gradient square
            drawRoundRect(
                brush = gradient,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(w * 0.28f, w * 0.28f)
            )
            val stroke = w * 0.085f
            val inset = w * 0.18f
            // Inner camera border
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2f, h - inset * 2f),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
                style = Stroke(width = stroke, join = StrokeJoin.Round)
            )
            // Center lens
            drawCircle(
                color = Color.White,
                radius = w * 0.16f,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = stroke)
            )
            // Top-right flash dot
            drawCircle(
                color = Color.White,
                radius = w * 0.048f,
                center = Offset(w * 0.69f, h * 0.31f)
            )
        }
    }
}

@Composable
fun WhatsAppLogoIcon(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Official WhatsApp green circle badge
            drawCircle(
                color = Color(0xFF25D366),
                radius = w * 0.5f,
                center = Offset(w / 2f, h / 2f)
            )
            // Speech bubble outline + bottom-left tail
            val bubbleStroke = w * 0.085f
            val r = w * 0.31f
            drawCircle(
                color = Color.White,
                radius = r,
                center = Offset(w * 0.51f, h * 0.48f),
                style = Stroke(width = bubbleStroke)
            )
            val tailPath = Path().apply {
                moveTo(w * 0.26f, h * 0.66f)
                lineTo(w * 0.20f, h * 0.82f)
                lineTo(w * 0.37f, h * 0.76f)
                close()
            }
            drawPath(path = tailPath, color = Color.White)
            // Inner green mask to blend tail seamlessly
            drawCircle(
                color = Color(0xFF25D366),
                radius = r - bubbleStroke * 0.48f,
                center = Offset(w * 0.51f, h * 0.48f)
            )
            // Classic white telephone handset receiver arc
            val handsetPath = Path().apply {
                moveTo(w * 0.39f, h * 0.36f)
                quadraticTo(w * 0.35f, h * 0.48f, w * 0.45f, h * 0.57f)
                quadraticTo(w * 0.54f, h * 0.65f, w * 0.64f, h * 0.60f)
            }
            drawPath(
                path = handsetPath,
                color = Color.White,
                style = Stroke(width = w * 0.105f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun GoogleLogoIcon(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.19f
            val pad = stroke / 2f
            val arcSize = Size(w - stroke, h - stroke)
            val topLeft = Offset(pad, pad)

            // Red top arc
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = 215f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // Yellow left arc
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 140f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // Green bottom arc
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 40f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // Blue right arc + crossbar
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = -5f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(w * 0.50f, h * 0.50f),
                end = Offset(w * 0.94f, h * 0.50f),
                strokeWidth = stroke,
                cap = StrokeCap.Butt
            )
        }
    }
}
