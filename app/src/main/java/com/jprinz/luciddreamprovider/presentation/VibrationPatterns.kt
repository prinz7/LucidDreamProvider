package com.jprinz.luciddreamprovider.presentation

// Datenklasse für Vibrationsmuster
data class VibrationPattern(val id: String, val name: String, val pattern: LongArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VibrationPattern
        if (id != other.id) return false
        if (name != other.name) return false
        if (!pattern.contentEquals(other.pattern)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + pattern.contentHashCode()
        return result
    }
}

// Liste der verfügbaren Vibrationsmuster
val availableVibrationPatterns = listOf(
    VibrationPattern("default", "Default (Long-Short-Long)", longArrayOf(0, 500, 200, 500)),
    VibrationPattern("short", "Short Pulse", longArrayOf(0, 200)),
    VibrationPattern("double", "Double Pulse", longArrayOf(0, 200, 100, 200)),
    VibrationPattern("long", "Long Pulse", longArrayOf(0, 800))
)