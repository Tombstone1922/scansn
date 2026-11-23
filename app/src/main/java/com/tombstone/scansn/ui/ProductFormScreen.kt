package com.tombstone.scansn.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tombstone.scansn.data.Catalog
import com.tombstone.scansn.data.ProductCategory
import com.tombstone.scansn.data.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    viewModel: ScanSNViewModel,
    products: List<ProductEntity>,
    onBack: () -> Unit,
    onScan: (ScanTarget) -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val error by viewModel.formError.collectAsState()
    val saving by viewModel.saving.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "Новый товар" else "Карточка товара") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(16.dp))
                    } else {
                        TextButton(onClick = { viewModel.save(onBack) }) {
                            Text("Сохранить", fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Категория", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProductCategory.entries.forEach { category ->
                    FilterChip(
                        selected = draft.category == category,
                        onClick = { viewModel.updateDraft { it.copy(category = category) } },
                        label = { Text(category.title) },
                    )
                }
            }

            SectionTitle("Товар")
            SuggestionField(
                value = draft.brand,
                label = "Бренд *",
                suggestions = Catalog.brands(draft.category, products),
                onValueChange = { value -> viewModel.updateDraft { it.copy(brand = value) } },
            )
            SuggestionField(
                value = draft.model,
                label = "Модель и конфигурация *",
                suggestions = Catalog.models(draft.brand, draft.category, products),
                onValueChange = { value -> viewModel.updateDraft { it.copy(model = value) } },
            )
            SuggestionField(
                value = draft.marketplace,
                label = "Площадка",
                suggestions = Catalog.marketplaces,
                onValueChange = { value -> viewModel.updateDraft { it.copy(marketplace = value) } },
            )
            OutlinedTextField(
                value = draft.color,
                onValueChange = { value -> viewModel.updateDraft { it.copy(color = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Цвет") },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.account,
                onValueChange = { value -> viewModel.updateDraft { it.copy(account = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Аккаунт") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.price,
                    onValueChange = { value -> viewModel.updateDraft { it.copy(price = value) } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Цена, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.customsDuty,
                    onValueChange = { value -> viewModel.updateDraft { it.copy(customsDuty = value) } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Пошлина, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }

            SectionTitle("Штрихкоды")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    when (draft.category) {
                        ProductCategory.PHONE -> "Для телефона заполните SN, IMEI и номер заказа."
                        ProductCategory.TABLET -> "Для планшета обязательны SN и заказ; IMEI можно добавить при наличии."
                        ProductCategory.PC_COMPONENT -> "Для комплектующего нужны SN и номер заказа."
                    },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ScanField("SN *", draft.sn, { value -> viewModel.updateDraft { it.copy(sn = value) } }) {
                onScan(ScanTarget.SN)
            }
            if (draft.category != ProductCategory.PC_COMPONENT) {
                ScanField(
                    label = if (draft.category.requiresImei) "IMEI * (15 цифр)" else "IMEI (если есть)",
                    value = draft.imei,
                    keyboardType = KeyboardType.Number,
                    onValueChange = { value -> viewModel.updateDraft { it.copy(imei = value) } },
                    onScan = { onScan(ScanTarget.IMEI) },
                )
            }
            ScanField(
                "Номер заказа *",
                draft.orderNumber,
                { value -> viewModel.updateDraft { it.copy(orderNumber = value) } },
            ) { onScan(ScanTarget.ORDER) }

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { value -> viewModel.updateDraft { it.copy(notes = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Комментарий") },
                minLines = 2,
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ScanField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    onScan: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, "Сканировать $label") }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionField(
    value: String,
    label: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = suggestions.filter { value.isBlank() || it.contains(value, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 280.dp),
            ) {
                filtered.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = { onValueChange(suggestion); expanded = false },
                    )
                }
            }
        }
    }
}
