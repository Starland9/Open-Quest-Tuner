package io.github.openquesttuner.ui

import io.github.openquesttuner.ui.components.CoverArtworkSource
import org.junit.Assert.*
import org.junit.Test

class LauncherGridLayoutTest {
    @Test fun normalQuestWindowShowsSeveralGamesPerRow() {
        assertEquals(5, LauncherGridLayout.columns(839f, LauncherTileSize.STANDARD))
        assertEquals(7, LauncherGridLayout.columns(839f, LauncherTileSize.SMALL))
        assertEquals(4, LauncherGridLayout.columns(839f, LauncherTileSize.LARGE))
    }
    @Test fun narrowWindowsDoNotTurnIntoFullWidthCards() {
        LauncherTileSize.entries.forEach { size ->
            listOf(280f, 320f, 480f, 600f).forEach { width -> assertTrue(LauncherGridLayout.columns(width, size) >= 2) }
        }
    }
    @Test fun wideningTheWindowNeverReducesTheGrid() {
        LauncherTileSize.entries.forEach { size ->
            val columns = (240..2000 step 40).map { LauncherGridLayout.columns(it.toFloat(), size) }
            assertTrue(columns.zipWithNext().all { (a, b) -> a <= b })
            assertTrue(columns.all { it <= 12 })
        }
    }
    @Test fun invalidSavedSizeUsesTheDenseLauncherLayout() {
        assertEquals(LauncherTileSize.SMALL, LauncherTileSize.fromStored(null))
        assertEquals(LauncherTileSize.SMALL, LauncherTileSize.fromStored("compact-list"))
        assertEquals(LauncherTileSize.SMALL, LauncherTileSize.fromStored("SMALL"))
    }
    @Test fun coverLookupUsesTheExactInstalledPackage() {
        assertEquals("https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/com.beatgames.beatsaber.jpg", CoverArtworkSource.url("com.beatgames.beatsaber"))
    }
    @Test fun coverLookupRejectsPathsAndUrls() {
        listOf("../private", "com.example/../../file", "https://example.com/a", "", "com.example?token=1").forEach { assertNull(CoverArtworkSource.url(it)) }
    }
    @Test fun coverDecoderAlwaysHasANonzeroBoundedSample() {
        assertEquals(1, CoverArtworkSource.decodeSampleSize(512, 288))
        assertEquals(8, CoverArtworkSource.decodeSampleSize(4096, 2304))
        assertEquals(2, CoverArtworkSource.decodeSampleSize(512, 512))
    }
    @Test fun coverDecoderRejectsInvalidAndOversizedImages() {
        assertNull(CoverArtworkSource.decodeSampleSize(0, 288))
        assertNull(CoverArtworkSource.decodeSampleSize(8193, 8193))
    }
}