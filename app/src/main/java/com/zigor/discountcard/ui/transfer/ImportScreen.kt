package com.zigor.discountcard.ui.transfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zigor.discountcard.R
import com.zigor.discountcard.appContainer
import com.zigor.discountcard.ui.nav.IncomingFile
import java.text.DateFormat
import java.util.Date

/**
 * Приём карт с другого телефона: файл `.mkcards`, присланный в Telegram,
 * открывается здесь. Можно и просто выбрать файл в памяти устройства.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.factory(container.repository, container.photoStore),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) onBack() else viewModel.openFile(context, uri)
    }

    LaunchedEffect(Unit) {
        val incoming = IncomingFile.take()
        if (incoming != null) {
            viewModel.openFile(context, incoming)
        } else {
            picker.launch(arrayOf("application/octet-stream", "application/zip", "application/*", "*/*"))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_transfer),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(18.dp))

            when (val current = state) {
                is ImportState.Working -> {
                    Text(
                        text = stringResource(R.string.import_working),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    CircularProgressIndicator()
                }

                is ImportState.Ready -> {
                    val date = remember(current.preview.exportedAt) {
                        DateFormat.getDateInstance().format(Date(current.preview.exportedAt))
                    }
                    Text(
                        text = stringResource(R.string.import_found, current.preview.cards, date),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.import_duplicates_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = { viewModel.confirm(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_action))
                    }
                }

                is ImportState.Done -> {
                    Text(
                        text = stringResource(
                            R.string.import_done,
                            current.result.added,
                            current.result.skipped,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_ok))
                    }
                }

                is ImportState.Failed -> {
                    Text(
                        text = stringResource(R.string.import_failed),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}
