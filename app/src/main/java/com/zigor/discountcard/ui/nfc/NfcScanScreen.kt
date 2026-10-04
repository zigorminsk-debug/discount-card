package com.zigor.discountcard.ui.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.data.repo.CardDraft
import com.zigor.discountcard.ui.components.BannerTone
import com.zigor.discountcard.ui.components.InfoBanner
import com.zigor.discountcard.util.findActivity
import com.zigor.discountcard.util.vibrateSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScanScreen(
    onBack: () -> Unit,
    onDraftReady: (CardDraft) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: NfcViewModel = viewModel(factory = NfcViewModel.factory(container.repository))
    val event by viewModel.event.collectAsStateWithLifecycle()
    val lastTag by viewModel.lastTag.collectAsStateWithLifecycle()

    val adapter = remember { context.findActivity()?.let { NfcAdapter.getDefaultAdapter(it) } }
    val availability = remember(adapter?.isEnabled) { nfcAvailability(adapter) }

    NfcReaderEffect(enabled = availability == NfcAvailability.READY, onTag = viewModel::onTag)

    LaunchedEffect(event) {
        when (val current = event) {
            is NfcEvent.Ready -> {
                context.vibrateSuccess()
                viewModel.consumed()
                onDraftReady(current.draft)
            }
            is NfcEvent.Duplicate -> {
                context.vibrateSuccess()
                viewModel.consumed()
                onDuplicate(current.cardId)
            }
            null -> Unit
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nfc_title)) },
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            NfcPulse(active = availability == NfcAvailability.READY)
            Spacer(Modifier.height(20.dp))

            Text(
                text = when (availability) {
                    NfcAvailability.READY -> stringResource(R.string.nfc_hint)
                    NfcAvailability.DISABLED -> stringResource(R.string.nfc_disabled)
                    NfcAvailability.UNSUPPORTED -> stringResource(R.string.nfc_unavailable)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            if (availability == NfcAvailability.DISABLED) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text(stringResource(R.string.nfc_enable))
                }
            }

            lastTag?.let { tag ->
                Spacer(Modifier.height(18.dp))
                InfoBanner(
                    title = stringResource(R.string.nfc_read_ok),
                    text = stringResource(R.string.nfc_uid) + ": " + tag.uid,
                    tone = BannerTone.SUCCESS,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            InfoBanner(
                title = stringResource(R.string.nfc_warning_title),
                text = stringResource(R.string.nfc_warning_text),
                tone = BannerTone.INFO,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun NfcPulse(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "nfc")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Box(
            modifier = Modifier
                .size(118.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nfc),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
