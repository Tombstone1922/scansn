package com.tombstone.scansn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tombstone.scansn.data.ProductEntity
import com.tombstone.scansn.export.TableExport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(products: List<ProductEntity>, viewModel: ScanSNViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.now())
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)) { uri ->
        uri?.let { viewModel.export(it, ExportFormat.CSV, context) }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.XLSX.mimeType)) { uri ->
        uri?.let { viewModel.export(it, ExportFormat.XLSX, context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экспорт в таблицу") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Готово: ${products.size} товаров", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Скопируйте TSV и вставьте в Excel или Google Таблицы. Длинные SN, IMEI и заказы останутся точным текстом без округления.")
                }
            }
            Button(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("ScanSN: товары", TableExport.toTsv(products)))
                    Toast.makeText(context, "Таблица скопирована. Вставьте её в лист", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Text("  Скопировать всё для вставки")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { xlsxLauncher.launch("ScanSN-$date.xlsx") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.TableChart, null)
                    Text("  Excel")
                }
                OutlinedButton(
                    onClick = { csvLauncher.launch("ScanSN-$date.csv") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Description, null)
                    Text("  CSV")
                }
            }
            Text("Колонки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TableExport.headers.forEach { header ->
                    Card(Modifier.padding(end = 6.dp)) {
                        Text(header, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text("Предпросмотр", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            products.take(8).forEach { product ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${product.brand} ${product.model}", fontWeight = FontWeight.SemiBold)
                        ExportLine("SN", product.sn)
                        if (product.imei.isNotBlank()) ExportLine("IMEI", product.imei)
                        ExportLine("Заказ", product.orderNumber)
                        if (product.price.isNotBlank()) ExportLine("Цена", "${product.price} ₽")
                    }
                }
            }
            if (products.size > 8) {
                Text("Ещё ${products.size - 8} товаров попадут в файл", color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExportLine(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
