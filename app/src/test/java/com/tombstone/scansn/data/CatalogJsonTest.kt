package com.tombstone.scansn.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogJsonTest {
    private val root = JSONObject(File("src/main/assets/catalog.json").readText())

    @Test
    fun catalogHasSubstantialCoverageForEveryCategory() {
        assertTrue(root.getInt("version") >= 3)
        val phone = root.getJSONArray("phone")
        val tablet = root.getJSONArray("tablet")
        val components = root.getJSONArray("pc_component")
        assertEquals(36, phone.length())
        assertEquals(27, tablet.length())
        assertEquals(48, components.length())
        val totalModels = listOf(phone, tablet, components).sumOf { array ->
            (0 until array.length()).sumOf { array.getJSONObject(it).getJSONArray("models").length() }
        }
        assertEquals(2_498, totalModels)
    }

    @Test
    fun everyEntryHasBrandModelsAndValidComponentType() {
        listOf("phone", "tablet", "pc_component").forEach { key ->
            val array = root.getJSONArray(key)
            val identities = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                assertTrue(item.getString("brand").isNotBlank())
                assertTrue(item.getJSONArray("models").length() > 0)
                val models = item.getJSONArray("models")
                val uniqueModels = mutableSetOf<String>()
                for (modelIndex in 0 until models.length()) {
                    val model = models.getString(modelIndex)
                    assertTrue(model.isNotBlank())
                    assertTrue("Duplicate ${item.getString("brand")} $model", uniqueModels.add(model.lowercase()))
                }
                if (key == "pc_component") assertTrue(item.getString("componentType").isNotBlank())
                assertTrue(identities.add(item.getString("brand") + "|" + item.optString("componentType")))
            }
        }
    }

    @Test
    fun recentPhonesAndTabletsArePresent() {
        val required = mapOf(
            "phone" to mapOf(
                "Apple" to listOf("iPhone Air", "iPhone 17e", "iPhone 18 Pro Max", "iPhone Duo"),
                "Samsung" to listOf("Galaxy S26 Ultra", "Galaxy Z Fold 8", "Galaxy A57"),
                "Xiaomi" to listOf("Xiaomi 17 Ultra", "Xiaomi 18 Pro"),
                "Poco" to listOf("Poco X8 Pro Max", "Poco F9 Ultra"),
                "Honor" to listOf("Honor Magic V6", "Honor 700 Pro"),
                "Oppo" to listOf("Find X9 Ultra", "Find N6"),
                "Vivo" to listOf("Vivo X300 Ultra"),
                "Google" to listOf("Pixel 10a", "Pixel 11 Pro Fold"),
                "Motorola" to listOf("Razr Fold", "Edge 70 Neo"),
                "Infinix" to listOf("Note 60 Ultra"),
            ),
            "tablet" to mapOf(
                "Apple" to listOf("iPad Air 11 M4", "iPad Pro 13 M5"),
                "Samsung" to listOf("Galaxy Tab S12 Ultra"),
                "Lenovo" to listOf("Legion Y700 Gen 5", "Idea Tab Pro Gen 2"),
                "Honor" to listOf("Honor MagicPad 4"),
            ),
        )
        required.forEach { (category, brands) ->
            val entries = root.getJSONArray(category)
            brands.forEach { (brand, models) ->
                val entry = (0 until entries.length()).map { entries.getJSONObject(it) }
                    .first { it.getString("brand") == brand }
                val available = entry.getJSONArray("models").let { array ->
                    (0 until array.length()).map { array.getString(it) }.toSet()
                }
                models.forEach { model -> assertTrue("$brand $model", available.contains(model)) }
            }
        }
    }
}
