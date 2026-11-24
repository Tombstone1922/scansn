package com.tombstone.scansn.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** A platform-independent barcode raster that can be drawn by Compose or embedded in XLSX. */
class BarcodeMatrix(val width: Int, val height: Int, private val pixels: ByteArray) {
    operator fun get(x: Int, y: Int): Boolean = pixels[y * width + x] != 0.toByte()

    fun toPng(): ByteArray {
        val raw = ByteArrayOutputStream((width + 1) * height)
        for (y in 0 until height) {
            raw.write(0) // PNG filter: none
            for (x in 0 until width) raw.write(if (this[x, y]) 0 else 255)
        }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { it.write(raw.toByteArray()) }

        val header = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use {
                it.writeInt(width)
                it.writeInt(height)
                it.writeByte(8) // bit depth
                it.writeByte(0) // grayscale
                it.writeByte(0) // compression
                it.writeByte(0) // filter
                it.writeByte(0) // interlace
            }
        }.toByteArray()

        return ByteArrayOutputStream().also { png ->
            png.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
            png.write(pngChunk("IHDR", header))
            png.write(pngChunk("IDAT", compressed.toByteArray()))
            png.write(pngChunk("IEND", ByteArray(0)))
        }.toByteArray()
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use {
                it.writeInt(data.size)
                it.write(typeBytes)
                it.write(data)
                it.writeInt(crc.value.toInt())
            }
        }.toByteArray()
    }
}

object Code128Generator {
    const val DEFAULT_WIDTH = 480
    const val DEFAULT_HEIGHT = 140

    fun supports(value: String): Boolean = value.isNotBlank() && value.trim().all { it.code in 32..126 }

    fun encode(value: String, width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT): BarcodeMatrix {
        val content = value.trim()
        require(content.isNotEmpty()) { "Пустое значение штрихкода" }
        require(supports(content)) { "Code 128 поддерживает только цифры, латиницу и ASCII-символы" }
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.CODE_128,
            width,
            height,
            mapOf(EncodeHintType.MARGIN to 12),
        )
        val pixels = ByteArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] = if (matrix[x, y]) 1 else 0
            }
        }
        return BarcodeMatrix(matrix.width, matrix.height, pixels)
    }
}
