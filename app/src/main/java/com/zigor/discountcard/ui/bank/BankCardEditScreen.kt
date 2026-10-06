package com.zigor.discountcard.ui.bank

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.data.bank.BankPalette
import com.zigor.discountcard.data.bank.BankSuggestions
import com.zigor.discountcard.data.bank.PaymentSystem
import com.zigor.discountcard.ui.components.BannerTone
import com.zigor.discountcard.ui.components.ColorPickerRow
import com.zigor.discountcard.ui.components.InfoBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCardEditScreen(
    cardId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: BankCardEditViewModel = viewModel(
        key = "bank-edit-$cardId",
        factory = BankCardEditViewModel.factory(container.bankCards, cardId),
    )
    val draft = viewModel.draft

    // Номер и срок видно, пока их вводят, поэтому прячем экран от скриншотов
    SecureScreenEffect()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isNew) R.string.bank_new_title else R.string.bank_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(onSaved) },
                        modifier = Modifier.testTag("bank_save_button"),
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
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
                .padding(horizontal = 16.dp)
                .testTag("bank_edit_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            OutlinedTextField(
                value = draft.number,
                onValueChange = viewModel::setNumber,
                label = { Text(stringResource(R.string.bank_number_label)) },
                placeholder = { Text("0000 0000 0000 0000") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = viewModel.error == BankEditError.NUMBER_INVALID,
                supportingText = {
                    val system = PaymentSystem.title(draft.system)
                    when {
                        viewModel.error == BankEditError.NUMBER_INVALID ->
                            Text(stringResource(R.string.bank_number_invalid))
                        system.isNotEmpty() ->
                            Text(stringResource(R.string.bank_system_detected, system))
                        else -> Text(stringResource(R.string.bank_number_hint))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_number_field"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.expiry,
                    onValueChange = viewModel::setExpiry,
                    label = { Text(stringResource(R.string.bank_expiry_label)) },
                    placeholder = { Text("09/29") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = viewModel.error == BankEditError.EXPIRY_INVALID,
                    supportingText = if (viewModel.error == BankEditError.EXPIRY_INVALID) {
                        { Text(stringResource(R.string.bank_expiry_invalid)) }
                    } else if (viewModel.expired) {
                        { Text(stringResource(R.string.bank_expiry_expired)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bank_expiry_field"),
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(stringResource(R.string.bank_title_label)) },
                    placeholder = { Text(stringResource(R.string.bank_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = draft.holder,
                onValueChange = viewModel::setHolder,
                label = { Text(stringResource(R.string.bank_holder_label)) },
                placeholder = { Text("IVAN IVANOV") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.bank,
                onValueChange = viewModel::setBank,
                label = { Text(stringResource(R.string.bank_bank_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val hints = remember(draft.bank) {
                if (draft.bank.isBlank()) {
                    BankSuggestions.NAMES
                } else {
                    BankSuggestions.NAMES.filter { it.contains(draft.bank.trim(), ignoreCase = true) }
                }
            }
            if (hints.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hints.forEach { name ->
                        AssistChip(onClick = { viewModel.setBank(name) }, label = { Text(name) })
                    }
                }
            }

            InfoBanner(
                title = stringResource(R.string.bank_cvv_title),
                text = stringResource(R.string.bank_cvv_text),
                tone = BannerTone.INFO,
            )

            Text(stringResource(R.string.bank_color_label), style = MaterialTheme.typography.titleSmall)
            ColorPickerRow(
                colors = BankPalette.COLORS,
                selected = draft.colorArgb,
                onSelect = viewModel::setColor,
            )

            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.bank_note_label)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !viewModel.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
