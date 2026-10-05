package com.zigor.discountcard.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.data.repo.CardDraft
import com.zigor.discountcard.data.store.MatchConfidence
import com.zigor.discountcard.ui.components.BannerTone
import com.zigor.discountcard.ui.components.BarcodeView
import com.zigor.discountcard.ui.components.ColorPickerRow
import com.zigor.discountcard.ui.components.InfoBanner
import com.zigor.discountcard.ui.components.PhotoThumb
import com.zigor.discountcard.util.BarcodeRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    cardId: Long,
    initialDraft: CardDraft?,
    photoResult: String?,
    onPhotoConsumed: () -> Unit,
    onTakePhoto: (side: String) -> Unit,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: CardEditViewModel = viewModel(
        key = "edit-$cardId",
        factory = CardEditViewModel.factory(container.repository, container.photoStore, cardId, initialDraft),
    )
    val draft = viewModel.draft
    var storePickerVisible by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }

    LaunchedEffect(photoResult) {
        val result = photoResult ?: return@LaunchedEffect
        val parts = result.split("|", limit = 2)
        if (parts.size == 2) viewModel.setPhoto(parts[0], parts[1])
        onPhotoConsumed()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isNew) R.string.card_new_title else R.string.card_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onSaved) }) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // contentWindowInsets = safeDrawing уже включает клавиатуру,
                // второй отступ imePadding() съедал бы высоту дважды
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            val suggestion = draft.suggestion
            when {
                suggestion.found -> InfoBanner(
                    title = stringResource(
                        if (suggestion.confidence == MatchConfidence.EXACT) R.string.store_found_title
                        else R.string.store_guess_title,
                    ) + ": " + suggestion.title,
                    text = suggestion.reason?.let { "Распознано: $it" },
                    tone = BannerTone.SUCCESS,
                )
                draft.code.isNotBlank() || draft.nfcUid != null -> InfoBanner(
                    title = stringResource(R.string.store_not_found_title),
                    text = stringResource(R.string.store_not_found_text),
                    tone = BannerTone.WARNING,
                    trailing = {
                        Button(
                            onClick = { onTakePhoto(CardEditViewModel.SIDE_FRONT) },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_photo_camera), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (draft.frontPhoto == null) R.string.store_photo_front
                                    else R.string.store_photo_retake,
                                ),
                            )
                        }
                    },
                )
            }

            suggestion.issuerHint?.let {
                Text(
                    text = stringResource(R.string.store_issuer_hint, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (draft.code.isNotBlank()) {
                BarcodeView(code = draft.code, format = draft.codeFormat, height = 120.dp)
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.card_name_label)) },
                placeholder = { Text(stringResource(R.string.card_name_placeholder)) },
                singleLine = true,
                isError = viewModel.error == EditError.NAME_REQUIRED,
                supportingText = if (viewModel.error == EditError.NAME_REQUIRED) {
                    { Text(stringResource(R.string.card_name_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val hints = remember(draft.title) { viewModel.suggestions(draft.title) }
            if (hints.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hints.forEach { store ->
                        AssistChip(
                            onClick = { viewModel.applyStore(store) },
                            label = { Text(store.name) },
                        )
                    }
                }
            }

            val selectedStore = viewModel.selectedStore
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.card_store_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedStore != null) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color(selectedStore.colorArgb), CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = selectedStore?.name ?: stringResource(R.string.card_store_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedStore == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { storePickerVisible = true },
                        modifier = Modifier.testTag("store_pick_button"),
                    ) {
                        Text(
                            stringResource(
                                if (selectedStore == null) R.string.card_store_choose
                                else R.string.card_store_change,
                            ),
                        )
                    }
                    if (selectedStore != null) {
                        TextButton(onClick = viewModel::detachStore) {
                            Text(stringResource(R.string.card_store_detach))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.code,
                onValueChange = viewModel::setCode,
                label = { Text(stringResource(R.string.card_number_label)) },
                singleLine = false,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (draft.code.all { it.isDigit() }) KeyboardType.Number else KeyboardType.Text,
                ),
                isError = viewModel.error == EditError.CODE_REQUIRED,
                supportingText = if (viewModel.error == EditError.CODE_REQUIRED) {
                    { Text(stringResource(R.string.card_code_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Box {
                OutlinedTextField(
                    value = BarcodeRenderer.humanName(draft.codeFormat),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.card_format_label)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 8.dp)
                        .clickable { formatMenu = true },
                )
                DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                    BarcodeRenderer.ALL.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(BarcodeRenderer.humanName(format)) },
                            onClick = {
                                viewModel.setFormat(format)
                                formatMenu = false
                            },
                        )
                    }
                }
            }

            Text(stringResource(R.string.card_color_label), style = MaterialTheme.typography.titleSmall)
            ColorPickerRow(
                colors = CardEditViewModel.COLORS,
                selected = draft.colorArgb,
                onSelect = viewModel::setColor,
            )

            Text(stringResource(R.string.card_photos_label), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoThumb(
                    path = draft.frontPhoto,
                    label = stringResource(R.string.card_front),
                    onCapture = { onTakePhoto(CardEditViewModel.SIDE_FRONT) },
                    onRemove = { viewModel.removePhoto(CardEditViewModel.SIDE_FRONT) },
                    modifier = Modifier.weight(1f),
                )
                PhotoThumb(
                    path = draft.backPhoto,
                    label = stringResource(R.string.card_back),
                    onCapture = { onTakePhoto(CardEditViewModel.SIDE_BACK) },
                    onRemove = { viewModel.removePhoto(CardEditViewModel.SIDE_BACK) },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.card_note_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            if (draft.code.isNotBlank()) {
                Text(
                    text = stringResource(R.string.store_learn_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { viewModel.save(onSaved) },
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

    if (storePickerVisible) {
        StorePickerDialog(
            catalog = viewModel.catalog,
            onDismiss = { storePickerVisible = false },
            onPick = { store ->
                viewModel.applyStore(store)
                storePickerVisible = false
            },
        )
    }
}
