package io.github.openquesttuner.core

/** Read explicit focus/resume evidence; a running PID or a recent launch is never enough. */
object ForegroundActivityParser {
    private val component=Regex("""\b([A-Za-z][A-Za-z0-9_.]*)/[A-Za-z0-9_.$]+""")
    private fun marked(output: String, marker: String): List<String> =
        output.lineSequence().filter { Regex(marker).containsMatchIn(it) }.toList()
    private fun unique(lines: List<String>): String? {
        if(lines.isEmpty()) return null
        return lines.map { component.find(it)?.groupValues?.get(1) ?: return null }.distinct().singleOrNull()
    }
    fun parse(output: String): String? {
        val top=marked(output,"""\b(?:topResumedActivity|mTopResumedActivity)\s*[:=]""")
        if(top.isNotEmpty()) return unique(top)
        return unique(marked(output,"""\b(?:mResumedActivity|ResumedActivity|resumedActivity)\s*[:=]"""))
    }
    fun parseWindow(output: String): String? {
        // The focused window takes precedence over a possibly stale mFocusedApp.
        val current=marked(output,"""\bmCurrentFocus\s*[:=]""")
        if(current.isNotEmpty()) return unique(current)
        return unique(marked(output,"""\bmFocusedApp\s*[:=]"""))
    }
    fun evidence(output: String): String = output.lineSequence()
        .filter { it.contains(Regex("(?i)resumedactivity|mCurrentFocus|mFocusedApp|permission denial|error|exception")) }
        .take(12).joinToString(" | ").take(2400).ifBlank { "No recognised focus/resume fields returned" }
}
