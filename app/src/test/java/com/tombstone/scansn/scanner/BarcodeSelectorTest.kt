package com.tombstone.scansn.scanner

import com.tombstone.scansn.ui.ScanTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeSelectorTest {
    private val region = ImageScanRegion(left = 100f, top = 100f, right = 900f, bottom = 300f)

    @Test
    fun `IMEI selects valid fifteen digit code instead of serial number`() {
        val candidates = listOf(
            candidate("SN-ABC-123456", 400f, 120f, 600f, 180f),
            candidate(VALID_IMEI, 400f, 200f, 600f, 260f),
        )

        assertEquals(VALID_IMEI, BarcodeSelector.select(candidates, region, ScanTarget.IMEI))
    }

    @Test
    fun `IMEI rejects invalid check digit`() {
        val candidates = listOf(candidate("490154203237519", 400f, 120f, 600f, 180f))

        assertNull(BarcodeSelector.select(candidates, region, ScanTarget.IMEI))
    }

    @Test
    fun `IMEI rejects repeated digits and formatted values`() {
        assertFalse(BarcodeSelector.isValidImei("000000000000000"))
        assertFalse(BarcodeSelector.isValidImei("49015420323751"))
        assertFalse(BarcodeSelector.isValidImei("49015-42032-37518"))
        assertFalse(BarcodeSelector.isValidImei("IMEI490154203237518"))
        assertTrue(BarcodeSelector.isValidImei(VALID_IMEI))
    }

    @Test
    fun `code fully outside frame is ignored`() {
        val candidates = listOf(candidate(VALID_IMEI, 0f, 0f, 90f, 60f))

        assertNull(BarcodeSelector.select(candidates, region, ScanTarget.IMEI))
    }

    @Test
    fun `code mostly outside frame is ignored`() {
        val candidates = listOf(candidate(VALID_IMEI, -300f, 120f, 550f, 180f))

        assertNull(BarcodeSelector.select(candidates, region, ScanTarget.IMEI))
    }

    @Test
    fun `code without bounds is ignored`() {
        val candidates = listOf(BarcodeCandidate(VALID_IMEI, bounds = null))

        assertNull(BarcodeSelector.select(candidates, region, ScanTarget.IMEI))
    }

    @Test
    fun `generic scan chooses code nearest frame center`() {
        val candidates = listOf(
            candidate("LEFT", 110f, 120f, 250f, 180f),
            candidate("CENTER", 450f, 160f, 550f, 240f),
        )

        assertEquals("CENTER", BarcodeSelector.select(candidates, region, ScanTarget.SN))
    }

    @Test
    fun `generic scan still accepts alphanumeric serial`() {
        val candidates = listOf(candidate("  SN-ABC-123456  ", 400f, 120f, 600f, 180f))

        assertEquals("SN-ABC-123456", BarcodeSelector.select(candidates, region, ScanTarget.SN))
    }

    @Test
    fun `null image region disables delivery`() {
        val candidates = listOf(candidate(VALID_IMEI, 400f, 120f, 600f, 180f))

        assertNull(BarcodeSelector.select(candidates, null, ScanTarget.IMEI))
    }

    private fun candidate(value: String, left: Float, top: Float, right: Float, bottom: Float) =
        BarcodeCandidate(value, ScanBounds(left, top, right, bottom))

    private companion object {
        const val VALID_IMEI = "490154203237518"
    }
}
