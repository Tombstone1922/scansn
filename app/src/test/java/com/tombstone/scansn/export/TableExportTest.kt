package com.tombstone.scansn.export

import com.tombstone.scansn.data.ProductEntity
import org.junit.Assert.assertEquals
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
    fun xlsxIsAValidPackageWithInlineTextCells() {
        val stream = ByteArrayOutputStream()
        TableExport.writeXlsx(listOf(product), stream)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(stream.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                entries[entry.name] = zip.readBytes().toString(StandardCharsets.UTF_8)
            }
        }
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("t=\"inlineStr\""))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("001234567890123"))
    }

    @Test
    fun formulasFromManualFieldsAreNeutralized() {
        val output = TableExport.toTsv(listOf(product.copy(model = "=HYPERLINK(\"bad\")")))
        assertTrue(output.contains("'=HYPERLINK"))
    }
}
