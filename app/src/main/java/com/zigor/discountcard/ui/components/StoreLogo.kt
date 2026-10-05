package com.zigor.discountcard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.data.store.StoreLogoStore
import com.zigor.discountcard.ui.theme.contrastingContent

/**
 * Логотип сети. Если логотипа ещё нет на телефоне — показывается монограмма
 * в фирменном цвете, а логотип подтягивается в фоне (когда это разрешено).
 */
@Composable
fun StoreLogo(
    storeId: String?,
    title: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val logos = container.logos
    val version by logos.updates.collectAsStateWithLifecycle()
    val bitmap = remember(storeId, version) { logos.logo(storeId) }

    LaunchedEffect(storeId, version) {
        if (bitmap == null && storeId != null) {
            container.catalog.byId(storeId)?.let { logos.fetch(it) }
        }
    }

    val shape = RoundedCornerShape(size / 4)
    val onColor = color.contrastingContent()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (bitmap != null) Color.White else color)
            // монограмма на плитке того же цвета: тонкая рамка, чтобы она читалась
            .then(
                if (bitmap == null) Modifier.border(1.dp, onColor.copy(alpha = 0.35f), shape)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .padding(size / 10),
            )
        } else {
            Text(
                text = StoreLogoStore.initials(title),
                color = onColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.6f).sp,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * Цвет карты. Если пользователь не менял цвет руками, берём цвет,
 * вычисленный по логотипу сети, — он точнее записанного в каталоге.
 */
@Composable
fun rememberBrandColor(storeId: String?, savedColorArgb: Int): Color {
    val container = LocalContext.current.appContainer
    val version by container.logos.updates.collectAsStateWithLifecycle()
    val argb = remember(storeId, savedColorArgb, version) {
        val catalogColor = container.catalog.byId(storeId)?.colorArgb
        if (catalogColor != null && catalogColor == savedColorArgb) {
            container.logos.brandColor(storeId) ?: savedColorArgb
        } else {
            savedColorArgb
        }
    }
    return Color(argb)
}

/** Размеры логотипов, чтобы они совпадали на всех экранах. */
object LogoSize {
    val tile: Dp = 38.dp
    val row: Dp = 34.dp
    val small: Dp = 24.dp
}
