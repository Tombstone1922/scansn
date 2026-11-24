package com.tombstone.scansn.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tombstone.scansn.data.DeviceInfoRepository
import java.net.URLEncoder
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(viewModel: ScanSNViewModel, onBack: () -> Unit) {
    val draft by viewModel.draft.collectAsState()
    val state by viewModel.deviceInfo.collectAsState()
    val loading by viewModel.deviceInfoLoading.collectAsState()
    val error by viewModel.deviceInfoError.collectAsState()
    val uriHandler = LocalUriHandler.current
    var tacInput by remember { mutableStateOf("") }
    var tacError by remember { mutableStateOf<String?>(null) }
    val brand = draft.brand.trim()
    val model = draft.model.trim()
    LaunchedEffect(brand, model) { viewModel.loadCachedDeviceInfo(brand, model) }
    val query = URLEncoder.encode("$brand $model", "UTF-8")
    Scaffold(topBar = {
        TopAppBar(title = { Text("Характеристики устройства") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("$brand $model", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Поиск по нескольким открытым источникам. Сохранённые результаты доступны офлайн.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.refreshDeviceInfo(brand, model) }, enabled = !loading && brand.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator() else Text(if (state == null) "Найти характеристики" else "Обновить характеристики")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                HorizontalDivider()
                Text("Поиск по IMEI / TAC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Введи IMEI или TAC. Приложение использует только первые 8 цифр (TAC); полный IMEI никуда не отправляется.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = tacInput, onValueChange = { tacInput = it.filter(Char::isDigit).take(15); tacError = null },
                    label = { Text("IMEI (15 цифр) или TAC (8 цифр)") },
                    supportingText = { Text("Для поиска достаточно первых 8 цифр") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                tacError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    val tac = DeviceInfoRepository.normalizeTac(tacInput)
                    if (tac == null) tacError = "Нужно ввести 8 или 15 цифр"
                    else {
                        val url = "https://www.google.com/search?q=" + URLEncoder.encode("IMEI TAC $tac device model", "UTF-8")
                        uriHandler.openUri(url)
                    }
                }, enabled = tacInput.length == 8 || tacInput.length == 15, modifier = Modifier.fillMaxWidth()) {
                    Text("Искать модель по TAC")
                }
                Text("Поиск по TAC определяет семейство модели, но не проверяет blacklist, блокировки или подлинность устройства.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (state != null) {
                item {
                    HorizontalDivider()
                    Text("Найденные характеристики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Получено: ${DateFormat.format("dd.MM.yyyy HH:mm", Date(state!!.fetchedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                items(state!!.fields) { field ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(field.label, Modifier.weight(0.9f), fontWeight = FontWeight.SemiBold)
                                Text(field.value, Modifier.weight(1.1f))
                            }
                            if (field.sourceUrl.isNotBlank()) {
                                TextButton(onClick = { uriHandler.openUri(field.sourceUrl) }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.OpenInNew, null)
                                    Text("Источник: ${field.sourceName}")
                                }
                            } else Text("Источник: ${field.sourceName}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Дополнительные каталоги", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Откроется поиск по точной модели или TAC. Проверяй региональный код и конфигурацию.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                TextButton(onClick = { uriHandler.openUri("https://4pda.to/forum/index.php?act=search&source=all&query=$query") }) {
                    Icon(Icons.Default.OpenInNew, null); Text("Искать на 4PDA")
                }
                TextButton(onClick = { uriHandler.openUri("https://www.google.com/search?q=site%3Agsmarena.com+$query+specifications") }) {
                    Icon(Icons.Default.OpenInNew, null); Text("Искать на GSMArena")
                }
                TextButton(onClick = { uriHandler.openUri("https://www.imei.info/") }) {
                    Icon(Icons.Default.OpenInNew, null); Text("IMEI.info — каталог и API")
                }
                TextButton(onClick = { uriHandler.openUri("https://fccid.io/TAC") }) {
                    Icon(Icons.Default.OpenInNew, null); Text("FCC ID — TAC каталог")
                }
            }
        }
    }
}