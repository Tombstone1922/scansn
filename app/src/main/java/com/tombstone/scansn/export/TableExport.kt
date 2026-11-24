package com.tombstone.scansn.export

import com.tombstone.scansn.barcode.BarcodeMatrix
import com.tombstone.scansn.barcode.Code128Generator
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
        "Площадка", "Тип товара", "Подкатегория", "Бренд", "Модель", "Цвет", "Аккаунт",
        "Цена", "Таможенная пошлина", "SN", "IMEI", "Номер заказа",
        "Комментарий", "Дата изменения",
    )

    private val barcodeHeaders = listOf("Товар", "Тип кода", "Значение", "Штрихкод Code 128")
    private val columnWidths = listOf(18, 20, 24, 18, 36, 18, 22, 14, 20, 28, 22, 28, 40, 22)

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
        val barcodeRows = products.flatMap { product ->
            val productName = listOf(product.brand, product.model).filter { it.isNotBlank() }.joinToString(" ")
            listOfNotNull(
                product.sn.takeIf { it.isNotBlank() }?.let { BarcodeRow(productName, "SN", it) },
                product.imei.takeIf { it.isNotBlank() }?.let { BarcodeRow(productName, "IMEI", it) },
                product.orderNumber.takeIf { it.isNotBlank() }?.let { BarcodeRow(productName, "Заказ", it) },
            )
        }.map { row ->
            row.copy(matrix = if (Code128Generator.supports(row.value)) {
                runCatching { Code128Generator.encode(row.value) }.getOrNull()
            } else null)
        }
        val images = barcodeRows.mapIndexedNotNull { index, row ->
            row.matrix?.let { BarcodeImage(index + 2, it) } // worksheet rows are 1-based; row 1 is the header
        }

        ZipOutputStream(output).use { zip ->
            zip.text("[Content_Types].xml", contentTypes(images.isNotEmpty()))
            zip.text("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.text("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView activeTab="0"/></bookViews><sheets><sheet name="Товары" sheetId="1" r:id="rId1"/><sheet name="Штрихкоды" sheetId="2" r:id="rId2"/></sheets></workbook>""")
            zip.text("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            zip.text("xl/styles.xml", styles())
            zip.text("xl/worksheets/sheet1.xml", productsSheet(products))
            zip.text("xl/worksheets/sheet2.xml", barcodesSheet(barcodeRows, images.isNotEmpty()))
            if (images.isNotEmpty()) {
                zip.text("xl/worksheets/_rels/sheet2.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>""")
                zip.text("xl/drawings/drawing1.xml", drawing(images))
                zip.text("xl/drawings/_rels/drawing1.xml.rels", drawingRelationships(images))
                images.forEachIndexed { index, image ->
                    zip.binary("xl/media/image${index + 1}.png", image.matrix.toPng())
                }
            }
        }
    }

    private fun productsSheet(products: List<ProductEntity>): String {
        val rows = listOf(headers) + products.map { product ->
            row(product).map { cell ->
                if (cell.matches(Regex("^=\"\\d+\"$"))) cell.substring(2, cell.lastIndex) else cell
            }
        }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
            append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            append("<sheetFormatPr defaultRowHeight=\"21\"/>")
            append("<cols>")
            columnWidths.forEachIndexed { index, width ->
                append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
            }
            append("</cols><sheetData>")
            rows.forEachIndexed { rowIndex, values ->
                if (rowIndex == 0) append("<row r=\"1\" ht=\"30\" customHeight=\"1\">")
                else append("<row r=\"${rowIndex + 1}\" ht=\"30\" customHeight=\"1\">")
                values.forEachIndexed { columnIndex, value ->
                    val reference = "${columnName(columnIndex)}${rowIndex + 1}"
                    val numeric = rowIndex > 0 && columnIndex in listOf(7, 8)
                    append(cell(reference, value, if (rowIndex == 0) 1 else if (numeric) 3 else 2, numeric))
                }
                append("</row>")
            }
            append("</sheetData><autoFilter ref=\"A1:N${rows.size}\"/>")
            append("<printOptions horizontalCentered=\"1\"/><pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/><pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
            append("</worksheet>")
        }
    }

    private fun barcodesSheet(rows: List<BarcodeRow>, hasDrawing: Boolean): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
        append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"21\"/><cols><col min=\"1\" max=\"1\" width=\"38\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"14\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"32\" customWidth=\"1\"/><col min=\"4\" max=\"4\" width=\"56\" customWidth=\"1\"/></cols><sheetData>")
        append("<row r=\"1\" ht=\"30\" customHeight=\"1\">")
        barcodeHeaders.forEachIndexed { index, value -> append(cell("${columnName(index)}1", value, 1)) }
        append("</row>")
        rows.forEachIndexed { index, row ->
            val rowNumber = index + 2
            append("<row r=\"$rowNumber\" ht=\"84\" customHeight=\"1\">")
            append(cell("A$rowNumber", row.productName, 2))
            append(cell("B$rowNumber", row.type, 2))
            append(cell("C$rowNumber", row.value, 2))
            append(cell("D$rowNumber", if (row.matrix == null) "Неподдерживаемые символы" else "", 2))
            append("</row>")
        }
        append("</sheetData><autoFilter ref=\"A1:D${rows.size + 1}\"/>")
        append("<printOptions horizontalCentered=\"1\"/><pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/><pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
        if (hasDrawing) append("<drawing r:id=\"rId1\"/>")
        append("</worksheet>")
    }

    private fun cell(reference: String, value: String, style: Int, numeric: Boolean = false): String {
        val number = value.replace(',', '.').takeIf { numeric && it.matches(Regex("\\d+(\\.\\d{1,2})?")) }
        return if (number != null) {
            "<c r=\"$reference\" s=\"$style\" t=\"n\"><v>$number</v></c>"
        } else {
            "<c r=\"$reference\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
        }
    }

    private fun contentTypes(hasDrawing: Boolean): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        if (hasDrawing) append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        if (hasDrawing) append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
        append("</Types>")
    }

    private fun styles(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF146B62"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFD8E2E0"/></left><right style="thin"><color rgb="FFD8E2E0"/></right><top style="thin"><color rgb="FFD8E2E0"/></top><bottom style="thin"><color rgb="FFD8E2E0"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Обычный" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun drawing(images: List<BarcodeImage>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        images.forEachIndexed { index, image ->
            val width = 360 * 9525
            val height = 100 * 9525
            append("<xdr:oneCellAnchor><xdr:from><xdr:col>3</xdr:col><xdr:colOff>38100</xdr:colOff><xdr:row>${image.row - 1}</xdr:row><xdr:rowOff>38100</xdr:rowOff></xdr:from><xdr:ext cx=\"$width\" cy=\"$height\"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"${index + 1}\" name=\"Штрихкод ${index + 1}\"/><xdr:cNvPicPr/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed=\"rId${index + 1}\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$width\" cy=\"$height\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>")
        }
        append("</xdr:wsDr>")
    }

    private fun drawingRelationships(images: List<BarcodeImage>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        images.forEachIndexed { index, _ ->
            append("<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image${index + 1}.png\"/>")
        }
        append("</Relationships>")
    }

    private fun row(product: ProductEntity): List<String> = listOf(
        product.marketplace,
        product.category.title,
        product.componentType,
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
            result = ('A' + (number - 1) % 26) + result
            number = (number - 1) / 26
        }
        return result
    }

    private fun ZipOutputStream.text(path: String, content: String) = binary(path, content.toByteArray(StandardCharsets.UTF_8))

    private fun ZipOutputStream.binary(path: String, content: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(content)
        closeEntry()
    }

    private data class BarcodeRow(
        val productName: String,
        val type: String,
        val value: String,
        val matrix: BarcodeMatrix? = null,
    )

    private data class BarcodeImage(val row: Int, val matrix: BarcodeMatrix)
}

fun xlsxBytes(products: List<ProductEntity>): ByteArray = ByteArrayOutputStream().also {
    TableExport.writeXlsx(products, it)
}.toByteArray()
