package io.github.openquesttuner.ui

/** Uses the content area after shared navigation; short windows retain accessible inner scrolling. */
object PerformanceWorkspaceLayout {
    data class Page(val columns: Int, val rows: Int, val rowHeightDp: Float) { val size: Int get() = columns * rows }
    fun page(widthDp: Float, heightDp: Float, fontScale: Float): Page {
        val scale = fontScale.takeIf { it.isFinite() && it >= 1f } ?: 1f
        val width = widthDp.takeIf { it.isFinite() && it > 0f } ?: 0f
        val height = heightDp.takeIf { it.isFinite() && it > 0f } ?: 0f
        val rowHeight = 176f * scale
        val columns = ((width + 8f) / (260f * scale + 8f)).toInt().coerceIn(1, 4)
        val rows = ((height - 40f + 8f) / (rowHeight + 8f)).toInt().coerceIn(1, 3)
        return Page(columns, rows, rowHeight)
    }
}
