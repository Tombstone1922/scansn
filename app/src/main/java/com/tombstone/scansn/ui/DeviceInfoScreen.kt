package com.tombstone.scansn.ui
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import java.util.Date

@Composable
fun DeviceInfoScreen(viewModel: ScanSNViewModel, onBack: () -> Unit) {
    val draft by viewModel.draft.collectAsState()
    val state by viewModel.deviceInfo.collectAsState()
    val loading by viewModel.deviceInfoLoading.collectAsState()
    val error by viewModel.deviceInfoError.collectAsState()
    val uriHandler = LocalUriHandler.current
    val brand = draft.brand.trim()
    val model = draft.model.trim()
    LaunchedEffect(brand, model) { viewModel.loadCachedDeviceInfo(brand, model) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Характеристики устройства") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("$brand $model", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Загруженные данные сохраняются локально и доступны без интернета.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.refreshDeviceInfo(brand, model) }, enabled = !loading && brand.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator() else Text(if (state == null) "Загрузить инфу" else "Обновить инфо")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (state != null) {
                item {
                    Text("Основные характеристики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Получено: ${DateFormat.format("dd.MM.yyyy HH:mm", Date(state!!.fetchedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                items(state!!.fields) { field ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(field.label, Modifier.weight(0.9f), fontWeight = FontWeight.SemiBold)
                            Text(field.value, Modifier.weight(1.1f))
                        }
                    }
                }
                item {
                    Text("Источник данных", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { uriHandler.openUri(state!!.sourceUrl) }) {
                        Icon(Icons.Default.OpenInNew, null); Text("Открыть статью-источник")
                    }
                    HorizontalDivider()
                }
            }
            item {
                Text("Дополнительные источники", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Ссылки открывают поиск по модели. Сверяйте региональную версию и конфигурацию.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                val query = URLEncoder.encode("$brand $model", "UTF-8")
                TextButton(onClick = { uriHandler.openUri("https://4pda.to/forum/index.php?act=search&source=all&query=$query") }) {
                    Icon(Icons.Default.OpenInNew, null); Text("Найти на 4PDA")
                }
                TextButton(onClick = { uriHandler.openUri("https://www.google.com/search?q=site%3Agsmarena.com+$query+specifications") }) {
                    Icon(Icons.Default.OpenInNew, null); Text("Характеристики GSMArena")
                }
            }
        }
    }
}