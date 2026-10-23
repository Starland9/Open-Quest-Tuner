package io.github.openquesttuner.ui

import io.github.openquesttuner.games.InstalledGame

/** Shared ordering for the Performance page, independent of Compose. */
internal fun performanceGameOrder(
    activePackage: String?,
    latestTimestampMs: (String) -> Long?,
): Comparator<InstalledGame> {
    // Keep missing history and stored epoch timestamps the same numeric type.
    // An Int default in the generic selector boxes Int and Long differently and crashes sorting.
    val timestamp: (InstalledGame) -> Long = { latestTimestampMs(it.packageName) ?: 0L }
    return compareByDescending<InstalledGame> { it.packageName == activePackage }
        .thenByDescending(timestamp)
        .thenBy { it.label.lowercase() }
}
