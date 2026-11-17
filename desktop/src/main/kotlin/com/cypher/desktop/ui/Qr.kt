package com.cypher.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR drawn straight from zxing's bit matrix: no bitmap round trip, so it stays crisp at
 * any size and follows the theme colours. It carries the same cypher://p2p code the user
 * can copy as text, which is how a playlist travels between devices without a server.
 */
@Composable
fun QrCode(
    content: String,
    size: Dp = 240.dp,
    dark: Color = Color(0xFF14101A),
    light: Color = Color(0xFFF2EDF2)
) {
    val matrix = remember(content) {
        runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 1
            )
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        }.getOrNull()
    }

    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(light)
            .padding(10.dp)
    ) {
        if (matrix == null) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val cells = matrix.width
            val cell = size.toPx() / cells
            for (y in 0 until cells) {
                for (x in 0 until cells) {
                    if (!matrix.get(x, y)) continue
                    drawRect(
                        color = dark,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}
