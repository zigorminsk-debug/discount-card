package com.zigor.discountcard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zigor.discountcard.util.BarcodeRenderer
import com.zigor.discountcard.util.RenderedCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Рисует код так же, как он выглядит на пластиковой карте.
 * Белая подложка и максимальная чёткость — чтобы сканер на кассе считал с экрана.
 */
@Composable
fun BarcodeView(
    code: String,
    format: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    onRendered: (RenderedCode) -> Unit = {},
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val twoD = BarcodeRenderer.isTwoD(format)
        val targetWidthPx = with(density) { maxWidth.toPx() }.toInt().coerceIn(200, 1400)
        val targetHeightPx = with(density) { height.toPx() }.toInt().coerceIn(120, 900)

        val rendered by produceState<RenderedCode?>(null, code, format, targetWidthPx, targetHeightPx) {
            value = withContext(Dispatchers.Default) {
                BarcodeRenderer.render(
                    value = code,
                    formatName = format,
                    widthPx = if (twoD) minOf(targetWidthPx, 900) else targetWidthPx,
                    heightPx = if (twoD) minOf(targetWidthPx, 900) else targetHeightPx,
                )
            }.also(onRendered)
        }

        val result = rendered
        when {
            result?.bitmap != null -> {
                val bitmap = result.bitmap!!
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.None),
                    contentDescription = null,
                    contentScale = if (twoD) ContentScale.Fit else ContentScale.FillBounds,
                    modifier = if (twoD) {
                        Modifier.size(260.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(height)
                    },
                )
            }
            result != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Не удалось нарисовать код",
                    color = Color(0xFF8A1C1C),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                result.error?.let {
                    Text(it, color = Color(0xFF8A1C1C), style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> Box(Modifier.height(height), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
    }
}
