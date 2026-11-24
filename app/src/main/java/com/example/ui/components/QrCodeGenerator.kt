package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

fun generateQrImageBitmap(
    content: String,
    sizePx: Int = 512,
    darkColor: Int = android.graphics.Color.BLACK,
    lightColor: Int = android.graphics.Color.WHITE
): ImageBitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val offset = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[offset + x] = if (matrix.get(x, y)) darkColor else lightColor
            }
        }
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
fun QrCodeCard(
    content: String,
    contentDescription: String,
    size: Dp = 148.dp,
    darkColor: Color = Color(0xFF171411),
    lightColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(content, darkColor, lightColor) {
        generateQrImageBitmap(
            content = content,
            sizePx = 420,
            darkColor = darkColor.toArgb(),
            lightColor = lightColor.toArgb()
        )
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(lightColor)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
