package com.tombstone.scansn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tombstone.scansn.data.ProductCategory
import com.tombstone.scansn.data.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    products: List<ProductEntity>,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBarcodes: (Long) -> Unit,
    onExport: () -> Unit,
    onUpdates: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ProductCategory?>(null) }
    var deleteCandidate by remember { mutableStateOf<ProductEntity?>(null) }
    val filtered = products.filter { product ->
        (category == null || product.category == category) &&
            (query.isBlank() || listOf(product.brand, product.model, product.sn, product.imei, product.orderNumber)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ScanSN", fontWeight = FontWeight.Bold)
                        Text("${products.size} товаров в базе", style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onUpdates) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = "Обновить приложение")
                    }
                    IconButton(onClick = onExport, enabled = products.isNotEmpty()) {
                        Icon(Icons.Default.TableChart, contentDescription = "Экспорт таблицы")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Добавить товар") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Модель, SN, IMEI или заказ") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить") } }
                } else null,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("Все") },
                )
                ProductCategory.entries.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(if (item == ProductCategory.PC_COMPONENT) "ПК" else item.title) },
                    )
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(if (products.isEmpty()) "Добавьте первый товар" else "Ничего не найдено")
                        Text(
                            if (products.isEmpty()) "SN и IMEI можно сразу отсканировать" else "Измените запрос или фильтр",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { product ->
                        ProductCard(product, onEdit, { onBarcodes(product.id) }, { deleteCandidate = product })
                    }
                }
            }
        }
    }

    deleteCandidate?.let { product ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить товар?") },
            text = { Text("${product.brand} ${product.model} исчезнет из базы и экспорта.") },
            confirmButton = {
                TextButton(onClick = { onDelete(product.id); deleteCandidate = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ProductCard(
    product: ProductEntity,
    onEdit: (Long) -> Unit,
    onBarcodes: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(product.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${product.brand} ${product.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(product.category.title, product.componentType).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (product.price.isNotBlank()) {
                    Text("${product.price} ₽", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onBarcodes) { Icon(Icons.Default.QrCode2, "Показать штрихкоды") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить товар") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { onEdit(product.id) }, label = { Text("SN ${product.sn}") })
                if (product.imei.isNotBlank()) {
                    AssistChip(onClick = { onEdit(product.id) }, label = { Text("IMEI ${product.imei}") })
                }
            }
            Text(
                "Заказ: ${product.orderNumber}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
