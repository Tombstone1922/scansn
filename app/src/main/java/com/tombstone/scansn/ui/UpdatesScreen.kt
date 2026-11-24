package com.tombstone.scansn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tombstone.scansn.update.UpdateCheckResult
import com.tombstone.scansn.update.UpdateInfo
import com.tombstone.scansn.update.UpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { UpdateManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Проверьте наличие новой версии ScanSN.") }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var needsPermission by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обновление приложения") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(status, style = MaterialTheme.typography.bodyLarge)
                    update?.let {
                        Text("Доступна версия ${it.versionName}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "APK будет скачан с официального GitHub-релиза. Перед установкой приложение проверит SHA-256.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    busy = true
                    status = "Проверяем сервер обновлений…"
                    update = null
                    scope.launch {
                        try {
                            when (val result = manager.check()) {
                                is UpdateCheckResult.Available -> {
                                    update = result.update
                                    status = "Найдено обновление."
                                }
                                is UpdateCheckResult.UpToDate -> {
                                    status = "Установлена актуальная версия (${result.currentVersion})."
                                }
                            }
                        } catch (error: Exception) {
                            status = error.message ?: "Не удалось проверить обновления."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Пожалуйста, подождите…" else "Проверить обновления") }

            if (update != null) {
                if (needsPermission || !manager.canInstallPackages()) {
                    Text(
                        "Android может запросить разрешение на установку приложений для ScanSN. Вернитесь сюда после выдачи разрешения.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            if (manager.canInstallPackages()) {
                                needsPermission = false
                            } else {
                                manager.openInstallPermissionSettings()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (manager.canInstallPackages()) "Продолжить" else "Разрешить установку") }
                }
                Button(
                    onClick = {
                        if (!manager.canInstallPackages()) {
                            needsPermission = true
                            manager.openInstallPermissionSettings()
                        } else {
                            val candidate = update ?: return@Button
                            busy = true
                            status = "Скачиваем APK и проверяем контрольную сумму…"
                            scope.launch {
                                try {
                                    manager.downloadAndInstall(candidate)
                                    status = "Проверка завершена. Подтвердите установку в окне Android."
                                } catch (error: Exception) {
                                    status = error.message ?: "Не удалось подготовить установку."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Установить обновление") }
            }
        }
    }
}
