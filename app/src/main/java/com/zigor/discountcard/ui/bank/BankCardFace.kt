package com.zigor.discountcard.ui.bank

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zigor.discountcard.data.bank.CardNumber
import com.zigor.discountcard.data.bank.PaymentSystem
import com.zigor.discountcard.data.db.BankCardEntity
import com.zigor.discountcard.ui.theme.contrastingContent

/**
 * «Пластик» на экране: банк, платёжная система, номер и срок.
 * Если [number] не передан — показываем только последние четыре цифры.
 */
@Composable
fun BankCardFace(
    card: BankCardEntity,
    modifier: Modifier = Modifier,
    number: String? = null,
    expiry: String? = null,
    holder: String? = null,
    compact: Boolean = false,
) {
    val base = Color(card.colorArgb)
    val content = base.contrastingContent()
    val gradient = Brush.linearGradient(listOf(base, base.darken(0.22f)))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .padding(if (compact) 16.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = card.title.ifBlank { card.bank },
                color = content,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val system = PaymentSystem.title(card.system)
            if (system.isNotEmpty()) {
                Text(
                    text = system,
                    color = content.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Условный чип — карта должна выглядеть как карта
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 26.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(content.copy(alpha = 0.3f)),
                )
            }
        }

        Text(
            text = number?.let { CardNumber.format(it) } ?: CardNumber.mask(card.last4),
            color = content,
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 17.sp else 22.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium,
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                val name = holder.orEmpty()
                if (name.isNotBlank()) {
                    Text(
                        text = name,
                        color = content.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (card.bank.isNotBlank() && card.bank != card.title) {
                    Text(
                        text = card.bank,
                        color = content.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!expiry.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = expiry,
                    color = content.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Затемняет цвет — нижний край градиента на «пластике». */
private fun Color.darken(amount: Float): Color = Color(
    red = red * (1 - amount),
    green = green * (1 - amount),
    blue = blue * (1 - amount),
    alpha = 1f,
)
