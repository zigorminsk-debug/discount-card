package com.zigor.discountcard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zigor.discountcard.R
import com.zigor.discountcard.data.db.CardEntity
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.ui.theme.contrastingContent
import com.zigor.discountcard.util.shortenCode

/** Плитка карты на главном экране. */
@Composable
fun CardTile(card: CardEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val base = rememberBrandColor(card.storeId, card.colorArgb)
    val content = base.contrastingContent()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(base, base.copy(alpha = 0.82f).compositeOverDark()),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                StoreLogo(
                    storeId = card.storeId,
                    title = card.title,
                    color = base,
                    size = LogoSize.tile,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = card.title,
                    color = content,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        when {
                            card.kind == CardKind.NFC -> R.drawable.ic_nfc
                            card.kind == CardKind.PHOTO -> R.drawable.ic_photo_camera
                            else -> R.drawable.ic_barcode
                        },
                    ),
                    contentDescription = null,
                    tint = content.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        card.hasCode -> shortenCode(card.code)
                        card.nfcUid != null -> "NFC " + shortenCode(card.nfcUid, 6)
                        else -> "фото карты"
                    },
                    color = content.copy(alpha = 0.92f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (card.favorite) {
                    Spacer(Modifier.width(8.dp))
                    Text("★", color = content.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }
    }
}

private fun Color.compositeOverDark(): Color = Color(
    red = red * 0.72f,
    green = green * 0.72f,
    blue = blue * 0.78f,
    alpha = 1f,
)

/** Цветная информационная плашка (нашли магазин / не нашли / предупреждение). */
@Composable
fun InfoBanner(
    title: String,
    text: String? = null,
    tone: BannerTone = BannerTone.INFO,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val (bg, fg) = when (tone) {
        BannerTone.SUCCESS -> Color(0xFFE4F6E7) to Color(0xFF14532D)
        BannerTone.WARNING -> Color(0xFFFFF3DC) to Color(0xFF7C4A03)
        BannerTone.INFO -> Color(0xFFE6EDFF) to Color(0xFF14306B)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(14.dp),
    ) {
        Text(title, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (!text.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text, color = fg.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
        }
        if (trailing != null) {
            Spacer(Modifier.height(10.dp))
            trailing()
        }
    }
}

enum class BannerTone { SUCCESS, WARNING, INFO }

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_card),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Ряд выбора цвета карты. */
@Composable
fun ColorPickerRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { argb ->
            val color = Color(argb)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (argb == selected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
            )
        }
    }
}
