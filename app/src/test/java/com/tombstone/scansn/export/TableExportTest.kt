package com.tombstone.scansn.export

import com.tombstone.scansn.data.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class TableExportTest {
    private val product = ProductEntity(
        id = 1,
        brand = "Motorola",
        model = "Edge 50 Fusion, 8/256 ГБ",
        sn = "001234567890123456789",
        imei = "001234567890123",
        orderNumber = "AP00854171267251",
        notes = "строка 1\nстрока 2",
        updatedAt = 0,
    )

    @Test
    fun tsvPreservesLongNumericCodesAsTextFormulas() {
        val output = TableExport.toTsv(listOf(product))
        assertTrue(output.contains("=\"001234567890123456789\""))
        assertTrue(output.contains("=\"001234567890123\""))
        assertTrue(output.contains("AP00854171267251"))
        assertTrue(output.contains("строка 1 строка 2"))
    }

    @Test
    fun csvEscapesSeparatorsQuotesAndUsesUtf8Bom() {
        val quoted = product.copy(model = "Edge; \"Fusion\"")
        val stream = ByteArrayOutputStream()
        TableExport.writeCsv(listOf(quoted), stream)
        val bytes = stream.toByteArray()
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        val text = bytes.toString(StandardCharsets.UTF_8)
        assertTrue(text.contains("\"Edge; \"\"Fusion\"\"\""))
    }

    @Test
    fun xlsxHasStyledProductsAndBarcodeSheetsWithPngImages() {
        val entries = xlsxEntries(listOf(product))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/styles.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries.containsKey("xl/drawings/drawing1.xml"))
        assertTrue(entries.containsKey("xl/media/image1.png"))
        assertTrue(entries.containsKey("xl/media/image2.png"))
        assertTrue(entries.containsKey("xl/media/image3.png"))

        val productsSheet = entries.getValue("xl/worksheets/sheet1.xml").toString(StandardCharsets.UTF_8)
        assertTrue(productsSheet.contains("t=\"inlineStr\""))
        assertTrue(productsSheet.contains("001234567890123"))
        assertFalse(productsSheet.contains("=\"001234567890123\""))

        val workbook = entries.getValue("xl/workbook.xml").toString(StandardCharsets.UTF_8)
        assertTrue(workbook.contains("name=\"Товары\""))
        assertTrue(workbook.contains("name=\"Штрихкоды\""))
        val drawing = entries.getValue("xl/drawings/drawing1.xml").toString(StandardCharsets.UTF_8)
        assertEquals(3, "r:embed".toRegex().findAll(drawing).count())
        val png = entries.getValue("xl/media/image1.png").toByteArray()
        assertEquals(listOf(-119, 80, 78, 71, 13, 10, 26, 10), png.take(8).map { it.toInt() })
    }

    @Test
    fun unsupportedBarcodeTextDoesNotBreakExport() {
        val entries = xlsxEntries(listOf(product.copy(sn = "СЕРИЯ-123", imei = "", orderNumber = "")))
        assertFalse(entries.containsKey("xl/media/image1.png"))
        val sheet = entries.getValue("xl/worksheets/sheet2.xml").toString(StandardCharsets.UTF_8)
        assertTrue(sheet.contains("Неподдерживаемые символы"))
        assertTrue(sheet.contains("СЕРИЯ-123"))
    }

    @Test
    fun formulasFromManualFieldsAreNeutralized() {
        val output = TableExport.toTsv(listOf(product.copy(model = "=HYPERLINK(\"bad\")")))
        assertTrue(output.contains("'=HYPERLINK"))
    }

    private fun xlsxEntries(products: List<ProductEntity>): Map<String, ByteArray> {
        val stream = ByteArrayOutputStream()
        TableExport.writeXlsx(products, stream)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(stream.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }
}
