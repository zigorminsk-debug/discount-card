package com.zigor.discountcard.ui.bank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.zigor.discountcard.ui.components.EmptyState
import com.zigor.discountcard.ui.components.InfoBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCardsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val viewModel: BankCardsViewModel = viewModel(
        factory = BankCardsViewModel.factory(container.bankCards),
    )
    val cards by viewModel.cards.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bank_cards_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.bank_add)) },
                modifier = Modifier.testTag("bank_add_button"),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("bank_cards_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoBanner(
                    title = stringResource(R.string.bank_notice_pos_title),
                    text = stringResource(R.string.bank_notice_pos_text),
                    tone = BannerTone.WARNING,
                )
            }
            item {
                InfoBanner(
                    title = stringResource(R.string.bank_notice_safe_title),
                    text = stringResource(R.string.bank_notice_safe_text),
                    tone = BannerTone.INFO,
                )
            }

            if (cards.isEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    EmptyState(
                        title = stringResource(R.string.bank_empty_title),
                        subtitle = stringResource(R.string.bank_empty_text),
                    )
                }
            } else {
                items(cards, key = { it.id }) { card ->
                    BankCardFace(
                        card = card,
                        compact = true,
                        modifier = Modifier
                            .testTag("bank_card_item")
                            .clickable { onOpen(card.id) },
                    )
                }
            }
        }
    }
}
