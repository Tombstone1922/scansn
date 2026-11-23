package com.tombstone.scansn.export

import com.tombstone.scansn.data.ProductEntity
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TableExport {
    val headers = listOf(
        "Площадка", "Тип товара", "Бренд", "Модель", "Цвет", "Аккаунт",
        "Цена", "Таможенная пошлина", "SN", "IMEI", "Номер заказа",
        "Комментарий", "Дата изменения",
    )

    fun toTsv(products: List<ProductEntity>): String = buildString {
        append(headers.joinToString("\t"))
        products.forEach { product ->
            append('\n')
            append(row(product).joinToString("\t") { cleanCell(it) })
        }
        append('\n')
    }

    fun writeCsv(products: List<ProductEntity>, output: OutputStream) {
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val text = buildString {
            append(headers.joinToString(";") { csvCell(it) })
            products.forEach { product ->
                append("\r\n")
                append(row(product).joinToString(";") { csvCell(it) })
            }
            append("\r\n")
        }
        output.write(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun writeXlsx(products: List<ProductEntity>, output: OutputStream) {
        val rows = listOf(headers) + products.map { product ->
            row(product).map { cell ->
                if (cell.matches(Regex("^=\"\\d+\"$"))) cell.substring(2, cell.lastIndex) else cell
            }
        }
        val sheet = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            append("<cols><col min=\"1\" max=\"13\" width=\"20\" customWidth=\"1\"/><col min=\"4\" max=\"4\" width=\"36\" customWidth=\"1\"/></cols><sheetData>")
            rows.forEachIndexed { rowIndex, values ->
                append("<row r=\"${rowIndex + 1}\">")
                values.forEachIndexed { columnIndex, value ->
                    append("<c r=\"${columnName(columnIndex)}${rowIndex + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData><autoFilter ref=\"A1:M${rows.size.coerceAtLeast(1)}\"/></worksheet>")
        }

        ZipOutputStream(output).use { zip ->
            zip.text("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            zip.text("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.text("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Товары" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.text("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            zip.text("xl/worksheets/sheet1.xml", sheet)
        }
    }

    private fun row(product: ProductEntity): List<String> = listOf(
        product.marketplace,
        product.category.title,
        product.brand,
        product.model,
        product.color,
        product.account,
        product.price,
        product.customsDuty,
        textNumber(product.sn),
        textNumber(product.imei),
        textNumber(product.orderNumber),
        product.notes,
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(product.updatedAt)),
    ).map { protectFormula(it) }

    private fun textNumber(value: String): String = if (value.length >= 6 && value.all(Char::isDigit)) {
        "=\"${value.replace("\"", "\"\"")}\""
    } else value

    private fun protectFormula(value: String): String = if (
        value.startsWith("=") && !value.matches(Regex("^=\"\\d+\"$")) ||
        value.startsWith("+") || value.startsWith("-") || value.startsWith("@")
    ) "'$value" else value

    private fun cleanCell(value: String): String = value.replace('\t', ' ').replace(Regex("[\\r\\n]+"), " ")
    private fun csvCell(value: String): String = "\"${cleanCell(value).replace("\"", "\"\"")}\""
    private fun xml(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (char.code >= 32 || char == '\n' || char == '\r' || char == '\t') append(char)
            }
        }
    }

    private fun columnName(index: Int): String {
        var number = index + 1
        var result = ""
        while (number > 0) {
            result = ('A' + (number - 1) % 26) + result.toString()
            number = (number - 1) / 26
        }
        return result
    }

    private fun ZipOutputStream.text(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }
}

fun xlsxBytes(products: List<ProductEntity>): ByteArray = ByteArrayOutputStream().also {
    TableExport.writeXlsx(products, it)
}.toByteArray()
