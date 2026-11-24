package com.tombstone.scansn.data
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLEncoder

data class DeviceInfoField(val label: String, val value: String)
data class DeviceInfoResult(val brand: String, val model: String, val fields: List<DeviceInfoField>, val sourceUrl: String, val fetchedAt: Long)

class DeviceInfoRepository(private val dao: DeviceInfoDao) {
    suspend fun cached(brand: String, model: String): DeviceInfoResult? =
        dao.get(key(brand, model))?.let { row ->
            val json = JSONArray(row.fieldsJson)
            val fields = buildList {
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    add(DeviceInfoField(item.optString("label"), item.optString("value")))
                }
            }
            DeviceInfoResult(row.brand, row.model, fields, row.sourceUrl, row.fetchedAt)
        }

    suspend fun refresh(brand: String, model: String): DeviceInfoResult {
        require(brand.isNotBlank() && model.isNotBlank()) { "Сначала укажите бренд и модель" }
        val result = lookup(brand.trim(), model.trim())
        val fields = JSONArray().apply {
            result.fields.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) }
        }
        dao.upsert(DeviceInfoCacheEntity(key(brand, model), brand, model, fields.toString(), result.sourceUrl, result.fetchedAt))
        return result
    }

    private fun lookup(brand: String, model: String): DeviceInfoResult {
        val query = URLEncoder.encode("$brand $model smartphone", "UTF-8")
        val search = JSONObject(request("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$query&format=json&utf8=1&srlimit=8"))
            .getJSONObject("query").getJSONArray("search")
        for (i in 0 until search.length()) {
            val page = search.getJSONObject(i)
            val id = page.optLong("pageid")
            if (id <= 0) continue
            try {
                val url = "https://en.wikipedia.org/w/api.php?action=parse&pageid=$id&prop=text&format=json&formatversion=2"
                val html = JSONObject(request(url)).getJSONObject("parse").getString("text")
                val fields = buildList {
                    Jsoup.parse(html).select("table.infobox tr").forEach { row ->
                        val rawLabel = row.selectFirst("th")?.text()?.trim().orEmpty()
                        val value = row.selectFirst("td")?.text()?.trim().orEmpty().replace(Regex("\\s+"), " ").take(500)
                        if (rawLabel.isNotBlank() && value.isNotBlank()) {
                            val label = label(rawLabel)
                            if (none { it.label.equals(label, true) }) add(DeviceInfoField(label, value))
                        }
                    }
                }.take(35)
                if (fields.size >= 3) {
                    val title = URLEncoder.encode(page.optString("title").replace(' ', '_'), "UTF-8").replace("+", "_")
                    return DeviceInfoResult(brand, model, fields, "https://en.wikipedia.org/wiki/$title", System.currentTimeMillis())
                }
            } catch (_: Exception) { /* Continue to the next matching article. */ }
        }
        throw IllegalStateException("Не найдена открытая таблица характеристик для $brand $model. Проверьте ссылки на 4PDA и GSMArena ниже.")
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 12000
        connection.readTimeout = 18000
        connection.setRequestProperty("User-Agent", "ScanSN-Android/1.3 (device information lookup)")
        return connection.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun label(raw: String): String = when {
        "model" in raw.lowercase() -> "Модель"
        "brand" in raw.lowercase() || "manufacturer" in raw.lowercase() -> "Производитель"
        "network" in raw.lowercase() -> "Сети"
        "dimensions" in raw.lowercase() -> "Размеры"
        "weight" in raw.lowercase() -> "Вес"
        "display" in raw.lowercase() || "screen" in raw.lowercase() -> "Экран"
        "resolution" in raw.lowercase() -> "Разрешение"
        "operating system" in raw.lowercase() || raw.equals("OS", true) -> "Операционная система"
        "chipset" in raw.lowercase() || "processor" in raw.lowercase() || "cpu" in raw.lowercase() -> "Процессор"
        "memory" in raw.lowercase() || "ram" in raw.lowercase() -> "Память"
        "storage" in raw.lowercase() || "internal" in raw.lowercase() -> "Встроенная память"
        "camera" in raw.lowercase() -> "Камеры"
        "battery" in raw.lowercase() -> "Аккумулятор"
        "charging" in raw.lowercase() -> "Зарядка"
        "sim" in raw.lowercase() -> "SIM"
        "bluetooth" in raw.lowercase() -> "Bluetooth"
        "usb" in raw.lowercase() -> "USB"
        "release" in raw.lowercase() || "announced" in raw.lowercase() -> "Дата выхода"
        else -> raw.take(80)
    }
    private fun key(brand: String, model: String) = "${brand.trim().lowercase()}|${model.trim().lowercase()}"
}