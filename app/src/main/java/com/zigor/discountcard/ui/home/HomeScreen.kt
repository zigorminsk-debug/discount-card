package com.zigor.discountcard.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.ui.components.CardTile
import com.zigor.discountcard.ui.components.EmptyState
import com.zigor.discountcard.util.appVersion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCard: (Long) -> Unit,
    onScanBarcode: () -> Unit,
    onScanNfc: () -> Unit,
    onAddManual: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.repository))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var aboutVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { aboutVisible = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_title))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onScanBarcode,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_scan), null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_scan_barcode))
                    }
                    FilledTonalButton(
                        onClick = onScanNfc,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_nfc), null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_scan_nfc), maxLines = 1)
                    }
                    IconButton(onClick = onAddManual) {
                        Icon(
                            painterResource(R.drawable.ic_keyboard),
                            contentDescription = stringResource(R.string.action_add_manual),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
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

            when {
                state.loading -> Box(Modifier.fillMaxSize())
                state.total == 0 -> EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                    modifier = Modifier.padding(top = 40.dp),
                )
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
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.cards, key = { it.id }) { card ->
                        CardTile(card = card, onClick = { onOpenCard(card.id) })
                    }
                }
            }
        }
    }

    if (aboutVisible) {
        val version = remember { context.appVersion() }
        val storesCount = remember { container.catalog.stores.size }
        AlertDialog(
            onDismissRequest = { aboutVisible = false },
            confirmButton = { TextButton(onClick = { aboutVisible = false }) { Text(stringResource(R.string.action_ok)) } },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_version, version.name, version.code))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_text))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_stores_known, storesCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            iconContentColor = Color.Unspecified,
        )
    }
}
