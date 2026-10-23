package io.github.openquesttuner.ui.components

/** Public landscape artwork source also used by Evolve Launcher; no APKs or executable content. */
object CoverArtworkSource {
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    fun url(packageName: String): String? = if (packagePattern.matches(packageName))
        "https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/$packageName.jpg"
    else null
    fun decodeSampleSize(width: Int, height: Int): Int? {
        if (width !in 1..8192 || height !in 1..8192) return null
        var sample = 1
        while (width / sample > 512 || height / sample > 288) sample *= 2
        return sample
    }
}
