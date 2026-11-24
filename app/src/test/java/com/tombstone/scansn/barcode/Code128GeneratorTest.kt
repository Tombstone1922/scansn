package com.tombstone.scansn.barcode

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Code128GeneratorTest {
    @Test
    fun generatedCodesCanBeDecoded() {
        listOf("001234567890123456789", "001234567890123", "AP00854171267251").forEach { value ->
            val matrix = Code128Generator.encode(value)
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                val x = index % matrix.width
                val y = index / matrix.width
                if (matrix[x, y]) 0 else 0xFFFFFF
            }
            val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
            val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
            assertEquals(value, decoded.text)
        }
    }

    @Test
    fun pngHasValidSignatureAndDimensions() {
        val png = Code128Generator.encode("SN-000123").toPng()
        assertEquals(
            listOf(-119, 80, 78, 71, 13, 10, 26, 10),
            png.take(8).map { it.toInt() },
        )
        assertTrue(png.size > 100)
    }

    @Test
    fun blankAndNonAsciiValuesAreRejected() {
        assertFalse(Code128Generator.supports("ЗАКАЗ-123"))
        assertThrows(IllegalArgumentException::class.java) { Code128Generator.encode("") }
        assertThrows(IllegalArgumentException::class.java) { Code128Generator.encode("ЗАКАЗ-123") }
    }
}
