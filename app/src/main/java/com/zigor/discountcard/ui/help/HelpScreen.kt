package com.zigor.discountcard.ui.help

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zigor.discountcard.R

/** Подробная инструкция: что делает каждая кнопка и как пользоваться на кассе. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.help_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Step(R.drawable.ic_scan, R.string.help_scan_title, R.string.help_scan_text)
            Step(R.drawable.ic_card, R.string.help_unknown_title, R.string.help_unknown_text)
            Step(R.drawable.ic_image_search, R.string.help_image_title, R.string.help_image_text)
            Step(R.drawable.ic_keyboard, R.string.help_manual_title, R.string.help_manual_text)
            Step(R.drawable.ic_nfc, R.string.help_nfc_title, R.string.help_nfc_text)
            Step(R.drawable.ic_barcode, R.string.help_checkout_title, R.string.help_checkout_text)
            Step(R.drawable.ic_card, R.string.help_list_title, R.string.help_list_text)
            Step(R.drawable.ic_barcode, R.string.help_store_title, R.string.help_store_text)
            Step(R.drawable.ic_card, R.string.help_logo_title, R.string.help_logo_text)
            Step(R.drawable.ic_photo_camera, R.string.help_photo_title, R.string.help_photo_text)
            Step(R.drawable.ic_transfer, R.string.help_transfer_title, R.string.help_transfer_text)
            Step(R.drawable.ic_update, R.string.help_update_title, R.string.help_update_text)
            Step(R.drawable.ic_card, R.string.help_privacy_title, R.string.help_privacy_text)

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Step(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes text: Int,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
