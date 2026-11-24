package com.tombstone.scansn.scanner

import kotlin.math.max

/** The visible scan frame in PreviewView coordinates. */
data class ViewScanRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val viewWidth: Float,
    val viewHeight: Float,
)

data class ScanBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class ImageScanRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun containsCenter(bounds: ScanBounds): Boolean =
        bounds.centerX in left..right && bounds.centerY in top..bottom

    fun coverageOf(bounds: ScanBounds): Float {
        val area = bounds.width * bounds.height
        if (area <= 0f) return 0f
        val intersectionWidth = (minOf(right, bounds.right) - maxOf(left, bounds.left)).coerceAtLeast(0f)
        val intersectionHeight = (minOf(bottom, bounds.bottom) - maxOf(top, bounds.top)).coerceAtLeast(0f)
        return intersectionWidth * intersectionHeight / area
    }
}

object ScanGeometry {
    /**
     * Maps the Compose frame into the rotated image coordinates used by ML Kit.
     * PreviewView.ScaleType.FILL_CENTER crops the overflowing image dimension.
     */
    fun mapViewRegionToImage(
        region: ViewScanRegion,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
    ): ImageScanRegion? {
        if (imageWidth <= 0 || imageHeight <= 0 || region.viewWidth <= 0f || region.viewHeight <= 0f) return null
        val rotation = ((rotationDegrees % 360) + 360) % 360
        require(rotation % 90 == 0) { "Rotation must be a multiple of 90 degrees" }
        val displayedWidth = if (rotation == 90 || rotation == 270) imageHeight.toFloat() else imageWidth.toFloat()
        val displayedHeight = if (rotation == 90 || rotation == 270) imageWidth.toFloat() else imageHeight.toFloat()
        val scale = max(region.viewWidth / displayedWidth, region.viewHeight / displayedHeight)
        val offsetX = (region.viewWidth - displayedWidth * scale) / 2f
        val offsetY = (region.viewHeight - displayedHeight * scale) / 2f

        val left = ((region.left - offsetX) / scale).coerceIn(0f, displayedWidth)
        val top = ((region.top - offsetY) / scale).coerceIn(0f, displayedHeight)
        val right = ((region.right - offsetX) / scale).coerceIn(0f, displayedWidth)
        val bottom = ((region.bottom - offsetY) / scale).coerceIn(0f, displayedHeight)
        if (right <= left || bottom <= top) return null
        return ImageScanRegion(left, top, right, bottom)
    }
}
