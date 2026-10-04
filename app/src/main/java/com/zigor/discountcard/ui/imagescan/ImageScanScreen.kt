package com.zigor.discountcard.ui.imagescan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.zigor.discountcard.data.repo.CardDraft

/**
 * Распознавание кода на готовой картинке: скриншот карты из мессенджера,
 * фотография из галереи, сохранённый купон. Камера не нужна.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScanScreen(
    onBack: () -> Unit,
    onDraftReady: (CardDraft) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: ImageScanViewModel = viewModel(
        factory = ImageScanViewModel.factory(container.repository, container.photoStore),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) currentOnBack() else viewModel.onImagePicked(context, uri)
    }

    LaunchedEffect(state) {
        when (val current = state) {
            is ImageScanState.Picking -> picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            is ImageScanState.Ready -> onDraftReady(current.draft)
            is ImageScanState.Duplicate -> onDuplicate(current.cardId)
            else -> Unit
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.image_scan_title)) },
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
                painter = painterResource(R.drawable.ic_image_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(
                    if (state is ImageScanState.Working) R.string.image_scan_working
                    else R.string.image_scan_hint,
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (state is ImageScanState.Working) {
                Spacer(Modifier.height(22.dp))
                CircularProgressIndicator()
            }
        }
    }

    val noCode = state as? ImageScanState.NoCode
    if (noCode != null) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.image_scan_not_found_title)) },
            text = { Text(stringResource(R.string.image_scan_not_found_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.useAsCardPhoto(context, noCode.uri) }) {
                    Text(stringResource(R.string.image_scan_use_as_photo))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.pickAgain() }) {
                    Text(stringResource(R.string.image_scan_pick_another))
                }
            },
        )
    }
}
