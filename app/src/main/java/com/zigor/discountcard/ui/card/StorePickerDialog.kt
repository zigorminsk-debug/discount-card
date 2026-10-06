package com.zigor.discountcard.ui.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zigor.discountcard.R
import com.zigor.discountcard.data.store.StoreCatalog
import com.zigor.discountcard.data.store.StoreInfo
import com.zigor.discountcard.ui.components.LogoSize
import com.zigor.discountcard.ui.components.StoreLogo

/** Название раздела каталога на языке интерфейса. */
@Composable
fun storeCategoryLabel(category: String): String = stringResource(
    when (category) {
        "food" -> R.string.store_category_food
        "pharmacy" -> R.string.store_category_pharmacy
        "home" -> R.string.store_category_home
        "beauty" -> R.string.store_category_beauty
        "tech" -> R.string.store_category_tech
        "clothes" -> R.string.store_category_clothes
        "kids" -> R.string.store_category_kids
        "fuel" -> R.string.store_category_fuel
        "food_out" -> R.string.store_category_food_out
        else -> R.string.store_category_other
    },
)

/**
 * Список всех магазинов каталога с поиском. Нужен, чтобы привязать уже заведённую карту
 * к магазину вручную — например, когда код не распознался.
 */
@Composable
fun StorePickerDialog(
    catalog: StoreCatalog,
    onDismiss: () -> Unit,
    onPick: (StoreInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(query) { catalog.grouped(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        title = { Text(stringResource(R.string.store_picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.store_picker_search)) },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_picker_search"),
                )
                Spacer(Modifier.size(12.dp))
                if (groups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.store_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .testTag("store_picker_list"),
                    ) {
                        groups.forEach { group ->
                            item(key = "h-${group.category}") {
                                Text(
                                    text = storeCategoryLabel(group.category),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                )
                            }
                            items(group.stores.size, key = { group.stores[it].id }) { index ->
                                StoreRow(store = group.stores[index], onClick = { onPick(group.stores[index]) })
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun StoreRow(store: StoreInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("store_item_${store.id}")
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        StoreLogo(
            storeId = store.id,
            title = store.name,
            color = Color(store.colorArgb),
            size = LogoSize.row,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = store.name, style = MaterialTheme.typography.bodyLarge)
            // сначала название карты сети — по нему карту узнают в кошельке
            val subtitle = listOfNotNull(
                store.card.takeIf { it.isNotBlank() },
                store.domains.firstOrNull(),
            ).joinToString(" · ").takeIf { it.isNotBlank() }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
