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
        val phone = root.getJSONArray("phone")
        val tablet = root.getJSONArray("tablet")
        val components = root.getJSONArray("pc_component")
        assertEquals(36, phone.length())
        assertEquals(27, tablet.length())
        assertEquals(48, components.length())
        val totalModels = listOf(phone, tablet, components).sumOf { array ->
            (0 until array.length()).sumOf { array.getJSONObject(it).getJSONArray("models").length() }
        }
        assertEquals(2_045, totalModels)
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
                for (modelIndex in 0 until models.length()) {
                    assertTrue(models.getString(modelIndex).isNotBlank())
                }
                if (key == "pc_component") assertTrue(item.getString("componentType").isNotBlank())
                assertTrue(identities.add(item.getString("brand") + "|" + item.optString("componentType")))
            }
        }
    }
}
