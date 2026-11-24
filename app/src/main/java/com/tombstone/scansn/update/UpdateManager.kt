package com.tombstone.scansn.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
)

sealed interface UpdateCheckResult {
    data class Available(val update: UpdateInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
}

class UpdateManager(private val context: Context) {
    companion object {
        private const val MANIFEST_URL =
            "https://github.com/Tombstone1922/scansn/releases/latest/download/latest.json"
        private const val EXPECTED_PACKAGE = "com.tombstone.scansn"
        private const val APK_PREFIX =
            "https://github.com/Tombstone1922/scansn/releases/download/"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val json = JSONObject(readUrl(MANIFEST_URL))
        require(json.optString("packageName") == EXPECTED_PACKAGE) {
            "Манифест относится к другому приложению"
        }
        require(json.optInt("minimumSdk", Int.MAX_VALUE) <= Build.VERSION.SDK_INT) {
            "Обновление требует более новую версию Android"
        }
        val versionName = json.getString("versionName")
        val versionCode = json.getLong("versionCode")
        val apkUrl = json.getString("apkUrl")
        val sha256 = json.getString("sha256").lowercase()
        require(apkUrl.startsWith(APK_PREFIX) && apkUrl.endsWith(".apk")) {
            "Недопустимый адрес APK"
        }
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "Некорректная контрольная сумма" }

        val currentVersionCode = currentVersionCode()
        if (versionCode <= currentVersionCode) {
            UpdateCheckResult.UpToDate(currentVersionName())
        } else {
            UpdateCheckResult.Available(UpdateInfo(versionName, versionCode, apkUrl, sha256))
        }
    }

    suspend fun downloadAndInstall(update: UpdateInfo): Unit = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(directory, "ScanSN-${update.versionName}.apk")
        download(update.apkUrl, apk)
        val actualHash = sha256(apk)
        require(actualHash.equals(update.sha256, ignoreCase = true)) {
            apk.delete()
            "Контрольная сумма APK не совпала. Установка отменена."
        }
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun currentVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "неизвестно"

    private fun readUrl(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            require(connection.responseCode in 200..299) {
                "Сервер обновлений ответил: HTTP ${connection.responseCode}"
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(address: String, destination: File) {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            require(connection.responseCode in 200..299) {
                "Не удалось скачать APK: HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
