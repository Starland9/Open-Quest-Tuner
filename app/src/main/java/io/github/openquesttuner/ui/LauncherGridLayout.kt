package io.github.openquesttuner.ui

/** Cover dimensions, independent of device pixels and of the width of a preview host. */
enum class LauncherTileSize(val label: String, val artworkWidthDp: Int) {
    SMALL("Small", 99), STANDARD("Standard", 125), LARGE("Large", 165);

    companion object {
        fun fromStored(value: String?): LauncherTileSize = entries.firstOrNull { it.name == value } ?: SMALL
    }
}

object LauncherGridLayout {
    const val TILE_PADDING_DP = 6
    const val GAP_DP = 8

    fun columns(availableWidthDp: Float, size: LauncherTileSize): Int {
        if (!availableWidthDp.isFinite() || availableWidthDp <= 0f) return 1
        val tileWidth = size.artworkWidthDp + TILE_PADDING_DP * 2
        val fitting = ((availableWidthDp + GAP_DP) / (tileWidth + GAP_DP)).toInt()
        // Keep narrow Quest windows a grid too. Only an exceptionally tiny surface
        // below 240dp uses one column; the normal headset window has many columns.
        return fitting.coerceIn(if (availableWidthDp >= 240f) 2 else 1, 12)
    }
}
