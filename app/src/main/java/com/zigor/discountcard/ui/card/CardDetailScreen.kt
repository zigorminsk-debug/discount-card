package com.zigor.discountcard.ui.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.data.db.CardKind
import com.zigor.discountcard.ui.components.BannerTone
import com.zigor.discountcard.ui.components.BarcodeView
import com.zigor.discountcard.ui.components.InfoBanner
import com.zigor.discountcard.ui.components.rememberPhotoBitmap
import com.zigor.discountcard.ui.theme.contrastingContent
import com.zigor.discountcard.util.BarcodeRenderer
import com.zigor.discountcard.util.KeepScreenBrightEffect
import com.zigor.discountcard.util.formatCardNumber
import com.zigor.discountcard.util.formatDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: CardDetailViewModel = viewModel(
        key = "detail-$cardId",
        factory = CardDetailViewModel.factory(container.repository, cardId),
    )
    val card by viewModel.card.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirmDelete by remember { mutableStateOf(false) }
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }

    KeepScreenBrightEffect(enabled = card != null)

    val accent = card?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
    val onAccent = accent.contrastingContent()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = accent,
                    titleContentColor = onAccent,
                    navigationIconContentColor = onAccent,
                    actionIconContentColor = onAccent,
                ),
                title = { Text(card?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.action_favorite),
                            tint = if (card?.favorite == true) onAccent else onAccent.copy(alpha = 0.45f),
                        )
                    }
                    IconButton(onClick = { onEdit(cardId) }) {
                        Icon(Icons.Default.Create, stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val current = card
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Шапка цвета магазина
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = current.title,
                    color = onAccent,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.card_show_at_checkout),
                    color = onAccent.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (current.hasCode) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BarcodeView(
                                code = current.code.orEmpty(),
                                format = current.codeFormat,
                                height = if (BarcodeRenderer.isTwoD(current.codeFormat)) 260.dp else 165.dp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = formatCardNumber(current.code.orEmpty()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF14161C),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable {
                                    clipboard.setText(AnnotatedString(current.code.orEmpty()))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.card_number_copied),
                                        )
                                    }
                                },
                            )
                            Text(
                                text = BarcodeRenderer.humanName(current.codeFormat),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF6B7280),
                            )
                        }
                    }
                } else {
                    InfoBanner(
                        title = stringResource(R.string.card_no_code),
                        tone = BannerTone.INFO,
                    )
                }

                if (current.kind == CardKind.NFC) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DetailRow(stringResource(R.string.nfc_uid), current.nfcUid.orEmpty())
                        current.nfcTech?.let { DetailRow(stringResource(R.string.nfc_tech), it) }
                        current.nfcPayload?.let { DetailRow(stringResource(R.string.nfc_payload), it) }
                    }
                    InfoBanner(
                        title = stringResource(R.string.nfc_warning_title),
                        text = stringResource(R.string.nfc_warning_text),
                        tone = BannerTone.INFO,
                    )
                }

                val photos = listOfNotNull(
                    current.frontPhoto?.let { stringResource(R.string.card_front) to it },
                    current.backPhoto?.let { stringResource(R.string.card_back) to it },
                )
                if (photos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        photos.forEach { (label, path) ->
                            PhotoCard(
                                label = label,
                                path = path,
                                onClick = { fullscreenPhoto = path },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                current.note?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    text = stringResource(R.string.card_added_on, formatDate(current.createdAt)) +
                        " · " + stringResource(R.string.card_used_times, current.useCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.card_delete_title)) },
            text = { Text(stringResource(R.string.card_delete_text, card?.title.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    fullscreenPhoto?.let { path ->
        Dialog(onDismissRequest = { fullscreenPhoto = null }) {
            val bitmap = rememberPhotoBitmap(path, maxSize = 2048)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
                    .clickable { fullscreenPhoto = null },
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PhotoCard(label: String, path: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap = rememberPhotoBitmap(path, maxSize = 900)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
