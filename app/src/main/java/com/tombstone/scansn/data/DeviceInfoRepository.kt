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

        // Start with phone-specific catalogues: Wikipedia is too sparse and often misses
        // regional / China-market models such as Moto G100s.
        try {
            val gsmchoice = lookupGsmchoice(normalizedBrand, normalizedModel)
            collected.addAll(gsmchoice.first)
            primaryUrl = gsmchoice.second
        } catch (error: Exception) {
            failures.add("GSMchoice: ${error.message ?: "нет результата"}")
        }

        try {
            val wiki = lookupWikipedia(normalizedBrand, normalizedModel)
            wiki.first.forEach { candidate ->
                if (collected.none { it.label.equals(candidate.label, true) }) collected.add(candidate)
            }
            if (primaryUrl.isBlank() && wiki.first.isNotEmpty()) primaryUrl = wiki.second
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


    /**
     * GSMchoice has a broad handset catalogue and indexes model names and manufacturer
     * model codes (for example Moto G100s / XT2537-4). Prefer exact title matches so a
     * similarly named phone is never silently presented as the requested device.
     */
    private fun lookupGsmchoice(brand: String, model: String): Pair<List<DeviceInfoField>, String> {
        val brandSlug = when (brand.lowercase().replace(" ", "")) {
            "motorola", "moto" -> "motorola"
            "samsung" -> "samsung"
            "apple" -> "apple"
            "google" -> "google"
            "xiaomi", "redmi", "poco" -> brand.lowercase()
            "honor" -> "honor"
            "huawei" -> "huawei"
            "oneplus" -> "oneplus"
            "oppo" -> "oppo"
            "realme" -> "realme"
            "vivo" -> "vivo"
            "tecno" -> "tecno"
            "infinix" -> "infinix"
            else -> brand.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }
        val cleanModel = model.trim()
        val compactModel = cleanModel.lowercase().replace(Regex("[^a-z0-9]"), "")
        // Known manufacturer model codes are aliases, not separate products.
        val knownAliases = mapOf(
            "xt25374" to "moto-g100s",
            "xt21254" to "moto-g100"
        )
        val aliasedSlug = knownAliases[compactModel]
        val modelSlug = cleanModel.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val candidates = linkedSetOf<String>()
        if (aliasedSlug != null) candidates.add(aliasedSlug)
        candidates.add(modelSlug)
        // Motorola catalogue uses the full "moto-" prefix for G-series product names.
        if (brandSlug == "motorola" && !modelSlug.startsWith("moto-") &&
            (modelSlug.startsWith("g") || modelSlug.startsWith("edge") || modelSlug.startsWith("razr"))) {
            candidates.add("moto-$modelSlug")
        }
        var lastError: Exception? = null
        for (slug in candidates) {
            val pageUrl = "https://www.gsmchoice.com/en/catalogue/$brandSlug/$slug/"
            try {
                val document = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .timeout(15000)
                    .get()
                val heading = document.selectFirst("h1")?.text().orEmpty()
                val expected = (aliasedSlug?.substringAfterLast('/') ?: cleanModel)
                    .lowercase().replace(Regex("[^a-z0-9]"), "")
                val actual = heading.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (heading.isBlank() || !actual.contains(expected)) continue

                val fields = mutableListOf<DeviceInfoField>()
                document.select("table tr").forEach { row ->
                    val cells = row.select("th, td")
                    if (cells.size < 2) return@forEach
                    val rawLabel = cells.first()?.text()?.trim().orEmpty()
                    val value = cells.drop(1).joinToString(" | ") { it.text().trim() }
                        .replace(Regex("\\s+"), " ").trim().take(500)
                    if (rawLabel.isBlank() || value.isBlank()) return@forEach
                    val normalized = label(rawLabel)
                    if (fields.none { it.label.equals(normalized, true) }) {
                        fields.add(DeviceInfoField(normalized, value, "GSMchoice", pageUrl))
                    }
                }
                // Reject pages with only navigation / metadata, not actual specifications.
                if (fields.size >= 5) return fields to pageUrl
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IllegalStateException(lastError?.message ?: "точная страница модели не найдена")
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
            return when (digits.length) {\n                8 -> digits\n                15 -> digits.take(8)\n                else -> null\n            }
        }
        private fun key(brand: String, model: String) = "${brand.trim().lowercase()}|${model.trim().lowercase()}"
    }
}