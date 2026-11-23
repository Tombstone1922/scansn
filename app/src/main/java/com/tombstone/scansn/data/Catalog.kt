package com.tombstone.scansn.data

import android.content.Context
import org.json.JSONObject

private data class CatalogEntry(
    val brand: String,
    val componentType: String = "",
    val models: List<String>,
)

object Catalog {
    val marketplaces = listOf(
        "WB", "Ozon", "Яндекс Маркет", "AliExpress", "Авито",
        "DNS", "М.Видео", "Ситилинк", "Другое",
    )

    @Volatile private var loaded = false
    private var entries: Map<ProductCategory, List<CatalogEntry>> = emptyMap()

    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            entries = try {
                val json = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                ProductCategory.entries.associateWith { category ->
                    val array = root.getJSONArray(category.catalogKey)
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.getJSONObject(index)
                            val modelArray = item.getJSONArray("models")
                            add(
                                CatalogEntry(
                                    brand = item.getString("brand"),
                                    componentType = item.optString("componentType"),
                                    models = buildList {
                                        for (modelIndex in 0 until modelArray.length()) {
                                            add(modelArray.getString(modelIndex))
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
            loaded = true
        }
    }

    fun componentTypes(history: List<ProductEntity>): List<String> = (
        entries[ProductCategory.PC_COMPONENT].orEmpty().map { it.componentType } +
            history.filter { it.category == ProductCategory.PC_COMPONENT }.map { it.componentType }
        ).filter { it.isNotBlank() }.distinct().sorted()

    fun brands(
        category: ProductCategory,
        history: List<ProductEntity>,
        componentType: String = "",
    ): List<String> {
        val presets = entries[category].orEmpty()
            .filter { category != ProductCategory.PC_COMPONENT || componentType.isBlank() || it.componentType == componentType }
            .map { it.brand }
        val historical = history.filter {
            it.category == category &&
                (category != ProductCategory.PC_COMPONENT || componentType.isBlank() || it.componentType == componentType)
        }.map { it.brand }
        return (historical + presets).filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    fun models(
        brand: String,
        category: ProductCategory,
        history: List<ProductEntity>,
        componentType: String = "",
    ): List<String> {
        val historical = history.filter {
            it.brand.equals(brand, ignoreCase = true) && it.category == category &&
                (category != ProductCategory.PC_COMPONENT || componentType.isBlank() || it.componentType == componentType)
        }.map { it.model }
        val presets = entries[category].orEmpty()
            .filter {
                it.brand.equals(brand, ignoreCase = true) &&
                    (category != ProductCategory.PC_COMPONENT || componentType.isBlank() || it.componentType == componentType)
            }
            .flatMap { it.models }
        return (historical + presets).filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    fun modelCount(category: ProductCategory): Int = entries[category].orEmpty().sumOf { it.models.size }

    private val ProductCategory.catalogKey: String
        get() = when (this) {
            ProductCategory.PHONE -> "phone"
            ProductCategory.TABLET -> "tablet"
            ProductCategory.PC_COMPONENT -> "pc_component"
        }
}
