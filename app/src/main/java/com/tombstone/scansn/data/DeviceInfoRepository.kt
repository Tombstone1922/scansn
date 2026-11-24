package com.tombstone.scansn.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLEncoder

data class DeviceInfoField(
    val label: String,
    val value: String,
    val sourceName: String = "Wikipedia",
    val sourceUrl: String = ""
)
data class DeviceInfoResult(
    val brand: String,
    val model: String,
    val fields: List<DeviceInfoField>,
    val sourceUrl: String,
    val fetchedAt: Long
)

class DeviceInfoRepository(private val dao: DeviceInfoDao) {
    suspend fun cached(brand: String, model: String): DeviceInfoResult? =
        dao.get(key(brand, model))?.let { row ->
            val json = JSONArray(row.fieldsJson)
            val fields = buildList {
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    add(DeviceInfoField(
                        item.optString("label"), item.optString("value"),
                        item.optString("sourceName", "Wikipedia"), item.optString("sourceUrl", row.sourceUrl)
                    ))
                }
            }
            DeviceInfoResult(row.brand, row.model, fields, row.sourceUrl, row.fetchedAt)
        }

    suspend fun refresh(brand: String, model: String): DeviceInfoResult {
        require(brand.isNotBlank() && model.isNotBlank()) { "Сначала укажите бренд и модель" }
        val normalizedBrand = brand.trim()
        val normalizedModel = model.trim()
        val collected = mutableListOf<DeviceInfoField>()
        var primaryUrl = ""
        val failures = mutableListOf<String>()

        try {
            val wiki = lookupWikipedia(normalizedBrand, normalizedModel)
            collected.addAll(wiki.first)
            primaryUrl = wiki.second
        } catch (error: Exception) {
            failures.add("Wikipedia: ${error.message ?: "нет результата"}")
        }

        try {
            val wikidata = lookupWikidata(normalizedBrand, normalizedModel)
            wikidata.forEach { candidate ->
                if (collected.none { it.label.equals(candidate.label, true) }) collected.add(candidate)
            }
            if (primaryUrl.isBlank() && wikidata.isNotEmpty()) primaryUrl = wikidata.first().sourceUrl
        } catch (error: Exception) {
            failures.add("Wikidata: ${error.message ?: "нет результата"}")
        }

        if (collected.isEmpty()) {
            throw IllegalStateException(
                "Не удалось автоматически найти характеристики для $normalizedBrand $normalizedModel. " +
                    "Попробуйте поиск по TAC или откройте дополнительные каталоги."
            )
        }
        val result = DeviceInfoResult(normalizedBrand, normalizedModel, collected.take(45), primaryUrl, System.currentTimeMillis())
        val json = JSONArray().apply {
            result.fields.forEach { field ->
                put(JSONObject()
                    .put("label", field.label)
                    .put("value", field.value)
                    .put("sourceName", field.sourceName)
                    .put("sourceUrl", field.sourceUrl))
            }
        }
        dao.upsert(DeviceInfoCacheEntity(
            key(normalizedBrand, normalizedModel), normalizedBrand, normalizedModel,
            json.toString(), primaryUrl, result.fetchedAt
        ))
        return result
    }

    private fun lookupWikipedia(brand: String, model: String): Pair<List<DeviceInfoField>, String> {
        val query = URLEncoder.encode("$brand $model smartphone", "UTF-8")
        val search = JSONObject(request(
            "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$query&format=json&utf8=1&srlimit=8"
        )).getJSONObject("query").getJSONArray("search")
        for (i in 0 until search.length()) {
            val page = search.getJSONObject(i)
            val id = page.optLong("pageid")
            if (id <= 0) continue
            try {
                val html = JSONObject(request(
                    "https://en.wikipedia.org/w/api.php?action=parse&pageid=$id&prop=text&format=json&formatversion=2"
                )).getJSONObject("parse").getString("text")
                val title = page.optString("title")
                val pageUrl = "https://en.wikipedia.org/wiki/" +
                    URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "_")
                val fields = mutableListOf<DeviceInfoField>()
                Jsoup.parse(html).select("table.infobox tr").forEach { row ->
                    val rawLabel = row.selectFirst("th")?.text()?.trim().orEmpty()
                    val value = row.selectFirst("td")?.text()?.trim().orEmpty()
                        .replace(Regex("\\s+"), " ").take(500)
                    if (rawLabel.isNotBlank() && value.isNotBlank()) {
                        val normalized = label(rawLabel)
                        if (fields.none { it.label.equals(normalized, true) }) {
                            fields.add(DeviceInfoField(normalized, value, "Wikipedia", pageUrl))
                        }
                    }
                }
                if (fields.size >= 3) return fields to pageUrl
            } catch (_: Exception) { /* Try another search result. */ }
        }
        throw IllegalStateException("подходящая таблица не найдена")
    }

    private fun lookupWikidata(brand: String, model: String): List<DeviceInfoField> {
        val query = URLEncoder.encode("$brand $model", "UTF-8")
        val search = JSONObject(request(
            "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$query&language=en&format=json&limit=6"
        )).optJSONArray("search") ?: return emptyList()
        for (i in 0 until search.length()) {
            val item = search.getJSONObject(i)
            val id = item.optString("id")
            if (!id.matches(Regex("Q[0-9]+"))) continue
            val entityUrl = "https://www.wikidata.org/wiki/$id"
            val entity = JSONObject(request(
                "https://www.wikidata.org/wiki/Special:EntityData/$id.json"
            )).getJSONObject("entities").getJSONObject(id)
            val claims = entity.optJSONObject("claims") ?: continue
            val fields = mutableListOf<DeviceInfoField>()
            fun addClaim(property: String, label: String) {
                val statements = claims.optJSONArray(property) ?: return
                val values = mutableListOf<String>()
                for (index in 0 until statements.length()) {
                    val value = statements.optJSONObject(index)?.optJSONObject("mainsnak")
                        ?.optJSONObject("datavalue")?.opt("value")
                    val text = when (value) {
                        is JSONObject -> value.optString("amount").ifBlank { value.optString("text") }
                        else -> value?.toString().orEmpty()
                    }
                    if (text.isNotBlank() && text !in values) values.add(text)
                }
                if (values.isNotEmpty()) fields.add(DeviceInfoField(label, values.joinToString(", ").take(500), "Wikidata", entityUrl))
            }
            addClaim("P176", "Производитель")
            addClaim("P178", "Разработчик")
            addClaim("P1542", "Область применения")
            addClaim("P2067", "Масса")
            addClaim("P2048", "Высота")
            addClaim("P2049", "Ширина")
            addClaim("P277", "Язык программирования")
            if (fields.isNotEmpty()) return fields
        }
        return emptyList()
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "ScanSN-Android/1.3 (device specifications; contact: support@scansn.app)")
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

    companion object {
        fun normalizeTac(input: String): String? {
            val digits = input.filter(Char::isDigit)
            return if (digits.length == 8) digits else null
        }
        private fun key(brand: String, model: String) = "${brand.trim().lowercase()}|${model.trim().lowercase()}"
    }
}