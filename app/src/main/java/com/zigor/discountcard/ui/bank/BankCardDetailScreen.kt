package com.zigor.discountcard.ui.bank

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.ui.components.BannerTone
import com.zigor.discountcard.ui.components.InfoBanner
import com.zigor.discountcard.util.formatDate
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCardDetailScreen(
    cardId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: BankCardDetailViewModel = viewModel(
        key = "bank-detail-$cardId",
        factory = BankCardDetailViewModel.factory(container.bankCards, cardId),
    )
    val card by viewModel.card.collectAsStateWithLifecycle()
    val secret = viewModel.secret
    var deleteVisible by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<String?>(null) }
    val secured = remember { context.deviceSecured() }

    SecureScreenEffect()

    val unlockTitle = stringResource(R.string.bank_unlock_title)
    val unlockText = stringResource(R.string.bank_unlock_text)
    val copiedMessage = stringResource(R.string.bank_copied)

    val revealGate = rememberCredentialGate(unlockTitle, unlockText) { viewModel.reveal() }
    val copyGate = rememberCredentialGate(unlockTitle, unlockText) {
        viewModel.withNumber { number ->
            copySensitive(context, "card", number)
            copied = number
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // Буфер обмена чистим сами: номер не должен висеть там до вечера
    LaunchedEffect(copied) {
        val number = copied ?: return@LaunchedEffect
        delay(60_000)
        clearClipboard(context, number)
        copied = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(card?.title.orEmpty().ifBlank { stringResource(R.string.bank_cards_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(cardId) }) {
                        Icon(Icons.Default.Create, stringResource(R.string.action_edit))
                    }
                    IconButton(
                        onClick = { deleteVisible = true },
                        modifier = Modifier.testTag("bank_delete_button"),
                    ) {
                        Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val current = card
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("bank_detail_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (current != null) {
                BankCardFace(
                    card = current,
                    number = secret?.number,
                    expiry = secret?.expiry?.ifBlank { null },
                    holder = secret?.holder?.ifBlank { null },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (secret == null) revealGate() else viewModel.hide() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("bank_reveal_button"),
                    ) {
                        Text(
                            stringResource(
                                if (secret == null) R.string.bank_show else R.string.bank_hide,
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = copyGate,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("bank_copy_button"),
                    ) {
                        Text(stringResource(R.string.action_copy))
                    }
                }

                if (secret != null) {
                    Text(
                        text = stringResource(
                            R.string.bank_autohide,
                            BankCardDetailViewModel.VISIBLE_SECONDS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (viewModel.decryptFailed) {
                    InfoBanner(
                        title = stringResource(R.string.bank_decrypt_failed_title),
                        text = stringResource(R.string.bank_decrypt_failed_text),
                        tone = BannerTone.WARNING,
                    )
                }

                if (!secured) {
                    InfoBanner(
                        title = stringResource(R.string.bank_no_lock_title),
                        text = stringResource(R.string.bank_no_lock_text),
                        tone = BannerTone.WARNING,
                    )
                }

                if (current.note.isNotBlank()) {
                    Text(current.note, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    text = stringResource(R.string.card_added_on, formatDate(current.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (deleteVisible) {
        AlertDialog(
            onDismissRequest = { deleteVisible = false },
            title = { Text(stringResource(R.string.bank_delete_title)) },
            text = { Text(stringResource(R.string.bank_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteVisible = false
                    viewModel.delete(onBack)
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
