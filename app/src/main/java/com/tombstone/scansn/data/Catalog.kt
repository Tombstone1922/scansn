package com.tombstone.scansn.data

object Catalog {
    val marketplaces = listOf("WB", "Ozon", "Яндекс Маркет", "AliExpress", "Авито", "Другое")

    private val commonBrands = listOf("Acer", "Apple", "ASUS", "Dell", "HP", "Huawei", "Lenovo", "MSI", "Samsung", "Xiaomi")
    private val phoneBrands = listOf("Google", "Honor", "Infinix", "Motorola", "Nothing", "OnePlus", "Oppo", "Poco", "Realme", "Tecno", "Vivo", "ZTE")
    private val componentBrands = listOf("AMD", "ASRock", "Cooler Master", "Corsair", "DeepCool", "GIGABYTE", "Intel", "Kingston", "MSI", "NZXT", "Palit", "Sapphire", "Seagate", "Thermaltake", "Western Digital", "Zotac")

    private val models = mapOf(
        "Motorola" to listOf("Edge 50 Fusion", "Moto G100", "Moto G100 Pro"),
        "Lenovo" to listOf("Xiaoxin Pad 2025, 11\", 6/128 ГБ, Wi-Fi", "Xiaoxin Pad 2025, 11\", 8/128 ГБ, Wi-Fi", "IdeaPad"),
        "Honor" to listOf("Magic Pad 10"),
        "OnePlus" to listOf("OnePlus 15", "OnePlus 13"),
        "Poco" to listOf("F8 Pro", "X7 Pro"),
        "Samsung" to listOf("Galaxy S25", "Galaxy A56", "Galaxy Tab S10"),
        "Apple" to listOf("iPhone 16", "iPhone 17", "iPad 11"),
        "AMD" to listOf("Ryzen 5 7500F", "Ryzen 7 7800X3D", "Ryzen 7 9800X3D"),
        "Intel" to listOf("Core i5-14400F", "Core Ultra 7 265K"),
        "ASUS" to listOf("TUF Gaming", "ROG Strix", "Prime"),
        "MSI" to listOf("Ventus", "Gaming X", "MAG"),
        "Kingston" to listOf("FURY Beast", "NV3"),
    )

    fun brands(category: ProductCategory, history: List<ProductEntity>): List<String> {
        val presets = when (category) {
            ProductCategory.PHONE -> phoneBrands + commonBrands
            ProductCategory.TABLET -> commonBrands + listOf("Honor", "Lenovo", "Poco", "Realme", "Xiaomi")
            ProductCategory.PC_COMPONENT -> componentBrands
        }
        return (history.map { it.brand } + presets)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    fun models(brand: String, category: ProductCategory, history: List<ProductEntity>): List<String> {
        val historical = history.filter {
            it.brand.equals(brand, ignoreCase = true) && it.category == category
        }.map { it.model }
        return (historical + models.entries.firstOrNull { it.key.equals(brand, true) }?.value.orEmpty())
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }
}
