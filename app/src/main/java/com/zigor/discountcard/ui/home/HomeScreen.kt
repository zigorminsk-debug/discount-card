package com.zigor.discountcard.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import androidx.core.content.FileProvider
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.data.transfer.CardTransfer
import com.zigor.discountcard.data.update.UpdateChecker
import com.zigor.discountcard.ui.components.CardTile
import com.zigor.discountcard.ui.components.EmptyState
import com.zigor.discountcard.util.appVersion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenCard: (Long) -> Unit,
    onScanBarcode: () -> Unit,
    onScanNfc: () -> Unit,
    onScanImage: () -> Unit,
    onImportCards: () -> Unit,
    onOpenHelp: () -> Unit,
    onAddManual: () -> Unit,
    onOpenBankCards: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.repository))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var aboutVisible by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var logosEnabled by remember { mutableStateOf(container.logos.downloadEnabled) }
    val scope = rememberCoroutineScope()
    // Пока открыта клавиатура, нижняя панель прячется: иначе её отступ под клавиатуру
    // и отступ содержимого складываются, и поле поиска схлопывается (обрезается текст).
    val imeVisible = WindowInsets.isImeVisible

    // Проверка новой версии: тихо при запуске (не чаще раза в сутки) и по кнопке в меню
    var update by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        update = runCatching { UpdateChecker.checkInBackground(context) }.getOrNull()
    }

    fun checkUpdatesNow() {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            val found = runCatching { UpdateChecker.check(context) }.getOrNull()
            checkingUpdate = false
            if (found != null) {
                update = found
            } else {
                Toast.makeText(context, R.string.update_none, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Экспорт: карты и фото пакуются в один файл и уходят в «Поделиться»
    // (Telegram, почта, Bluetooth) — приложению для этого не нужен интернет.
    fun exportCards() {
        scope.launch {
            val done = runCatching { CardTransfer.export(context, container.repository) }
            done.onSuccess { result ->
                runCatching {
                    val uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".files",
                        result.file,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = CardTransfer.MIME
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            context.getString(R.string.export_subject, result.cards),
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(send, context.getString(R.string.export_share_title)),
                    )
                }.onFailure {
                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_title)) },
                    actions = {
                        IconButton(onClick = { aboutVisible = true }) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.about_title),
                            )
                        }
                    },
                )
                // Поле поиска живёт в верхней панели: её высота не зависит от того,
                // сколько места съела клавиатура, поэтому поле нельзя сжать или обрезать
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("home_search"),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.home_search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        bottomBar = {
            if (!imeVisible) Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // панель рисуется поверх системной навигации, поэтому её отступы
                        // нужно забрать себе — иначе кнопки уезжают под кнопки системы
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("home_bottom_bar"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onScanBarcode,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("scan_barcode_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_scan), null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_scan_barcode),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = onScanNfc,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_nfc_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_nfc), null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_scan_nfc),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.testTag("more_button"),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_help)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_help), null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onOpenHelp()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_scan_image)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_image_search), null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onScanImage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_add_manual)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_keyboard), null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onAddManual()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bank_cards_title)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_bank_card), null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onOpenBankCards()
                                },
                                modifier = Modifier.testTag("bank_cards_menu"),
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export_cards)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_share), null)
                                },
                                enabled = state.total > 0,
                                onClick = {
                                    menuOpen = false
                                    exportCards()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_import_cards)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_download), null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onImportCards()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (logosEnabled) R.string.action_logos_off
                                            else R.string.action_logos_on,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_card), null)
                                },
                                onClick = {
                                    menuOpen = false
                                    logosEnabled = !logosEnabled
                                    container.logos.downloadEnabled = logosEnabled
                                    if (!logosEnabled) container.logos.clearDownloaded()
                                    Toast.makeText(
                                        context,
                                        if (logosEnabled) R.string.logos_enabled else R.string.logos_disabled,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (checkingUpdate) R.string.update_checking
                                            else R.string.action_check_updates,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_update), null)
                                },
                                enabled = !checkingUpdate,
                                onClick = {
                                    menuOpen = false
                                    checkUpdatesNow()
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().weight(1f))
                state.total == 0 -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_subtitle),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
                state.cards.isEmpty() -> Text(
                    text = stringResource(R.string.home_nothing_found),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(state.cards, key = { it.id }) { card ->
                        CardTile(card = card, onClick = { onOpenCard(card.id) })
                    }
                }
            }
        }
    }

    update?.let { release ->
        val installed = remember { context.appVersion().name }
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text(stringResource(R.string.update_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_text, release.version, installed))
                    if (release.notes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = release.notes.take(400),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.update_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)),
                            )
                        }
                        update = null
                    },
                ) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        UpdateChecker.skip(context, release.version)
                        update = null
                    },
                ) { Text(stringResource(R.string.update_later)) }
            },
        )
    }

    if (aboutVisible) {
        val version = remember { context.appVersion() }
        AlertDialog(
            onDismissRequest = { aboutVisible = false },
            confirmButton = { TextButton(onClick = { aboutVisible = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = {
                TextButton(
                    onClick = {
                        aboutVisible = false
                        onOpenHelp()
                    },
                ) { Text(stringResource(R.string.action_help)) }
            },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_version, version.name, version.code))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_text))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.about_developer),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:+375293371412")),
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.about_developer_phone))
                    }
                }
            },
            iconContentColor = Color.Unspecified,
        )
    }
}
