package com.tombstone.scansn.scanner

import com.tombstone.scansn.ui.ScanTarget

private const val MINIMUM_FRAME_COVERAGE = 0.95f

data class BarcodeCandidate(
    val rawValue: String,
    val bounds: ScanBounds?,
)

object BarcodeSelector {
    fun select(
        candidates: List<BarcodeCandidate>,
        region: ImageScanRegion?,
        target: ScanTarget,
    ): String? {
        if (region == null) return null
        return candidates.mapNotNull { candidate ->
            val bounds = candidate.bounds ?: return@mapNotNull null
            val value = normalizeForTarget(candidate.rawValue, target) ?: return@mapNotNull null
            if (!region.containsCenter(bounds) || region.coverageOf(bounds) < MINIMUM_FRAME_COVERAGE) {
                return@mapNotNull null
            }
            val distanceFromCenter = squaredDistance(bounds.centerX, bounds.centerY, region.centerX, region.centerY)
            ScoredCandidate(value, distanceFromCenter)
        }.minByOrNull { it.distanceFromCenter }?.value
    }

    fun normalizeForTarget(rawValue: String, target: ScanTarget): String? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null
        return when (target) {
            ScanTarget.IMEI -> value.takeIf { isValidImei(it) }
            ScanTarget.SN, ScanTarget.ORDER -> value
        }
    }

    fun isValidImei(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{15}"))) return false
        if (value.all { it == value[0] }) return false
        var sum = 0
        value.forEachIndexed { index, character ->
            var digit = character - '0'
            if (index % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return sum % 10 == 0
    }

    private fun squaredDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private data class ScoredCandidate(val value: String, val distanceFromCenter: Float)
}
