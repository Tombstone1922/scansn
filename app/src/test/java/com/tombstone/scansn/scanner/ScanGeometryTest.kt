package com.tombstone.scansn.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanGeometryTest {
    @Test
    fun `portrait rotated image maps one to one into portrait preview`() {
        val viewRegion = ViewScanRegion(50f, 100f, 1000f, 300f, 1080f, 1920f)

        val result = ScanGeometry.mapViewRegionToImage(viewRegion, 1920, 1080, 90)!!

        assertEquals(50f, result.left, TOLERANCE)
        assertEquals(100f, result.top, TOLERANCE)
        assertEquals(1000f, result.right, TOLERANCE)
        assertEquals(300f, result.bottom, TOLERANCE)
    }

    @Test
    fun `fill center accounts for horizontally cropped image`() {
        val viewRegion = ViewScanRegion(0f, 0f, 1080f, 1920f, 1080f, 1920f)

        val result = ScanGeometry.mapViewRegionToImage(viewRegion, 1920, 1080, 0)!!

        assertEquals(656.25f, result.left, TOLERANCE)
        assertEquals(0f, result.top, TOLERANCE)
        assertEquals(1263.75f, result.right, TOLERANCE)
        assertEquals(1080f, result.bottom, TOLERANCE)
    }

    @Test
    fun `fill center accounts for vertically cropped rotated image`() {
        val viewRegion = ViewScanRegion(0f, 0f, 1920f, 1080f, 1920f, 1080f)

        val result = ScanGeometry.mapViewRegionToImage(viewRegion, 1920, 1080, 90)!!

        assertEquals(0f, result.left, TOLERANCE)
        assertEquals(656.25f, result.top, TOLERANCE)
        assertEquals(1080f, result.right, TOLERANCE)
        assertEquals(1263.75f, result.bottom, TOLERANCE)
    }

    @Test
    fun `zero width frame produces no scan region`() {
        val viewRegion = ViewScanRegion(100f, 0f, 100f, 100f, 1080f, 1920f)

        val result = ScanGeometry.mapViewRegionToImage(viewRegion, 1920, 1080, 0)

        assertNull(result)
    }

    @Test
    fun `invalid image dimensions produce no scan region`() {
        val viewRegion = ViewScanRegion(0f, 0f, 100f, 100f, 1080f, 1920f)

        assertNull(ScanGeometry.mapViewRegionToImage(viewRegion, 0, 1080, 90))
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
