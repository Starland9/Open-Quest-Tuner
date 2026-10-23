package io.github.openquesttuner.ui

import io.github.openquesttuner.games.InstalledGame
import org.junit.Assert.*
import org.junit.Test

class PerformanceGameOrderTest {
    private fun game(name: String) = InstalledGame("com.test.$name", name, "com.test.Main")

    @Test fun `recorded and unrecorded games can be compared in both directions`() {
        val recorded = game("Recorded")
        val unrecorded = game("Unrecorded")
        val comparator = performanceGameOrder(null) { if (it == recorded.packageName) 1_792_590_000_000L else null }
        assertTrue(comparator.compare(recorded, unrecorded) < 0)
        assertTrue(comparator.compare(unrecorded, recorded) > 0)
        assertEquals(0, comparator.compare(recorded, recorded))
    }

    @Test fun `78 game library retains all games and puts latest histories first`() {
        val games = (0 until 78).map { game("Game%02d".format(it)) }
        val timestamps = games.filterIndexed { index, _ -> index % 3 == 0 }
            .mapIndexed { index, game -> game.packageName to 1_792_590_000_000L + index }.toMap()
        val actual = games.reversed().sortedWith(performanceGameOrder(null, timestamps::get))
        val expected = games.filter { it.packageName in timestamps }.reversed() + games.filter { it.packageName !in timestamps }
        assertEquals(expected, actual)
        assertEquals(78, actual.map { it.packageName }.toSet().size)
    }

    @Test fun `active game stays first even without history`() {
        val active = game("Zelda")
        val recorded = game("Alpha")
        val actual = listOf(recorded, active).sortedWith(performanceGameOrder(active.packageName) {
            if (it == recorded.packageName) Long.MAX_VALUE else null
        })
        assertEquals(listOf(active, recorded), actual)
    }

    @Test fun `empty histories and equal timestamps use alphabetical names`() {
        val games = listOf(game("Zulu"), game("alpha"), game("Bravo"))
        val expected = listOf(games[1], games[2], games[0])
        assertEquals(expected, games.sortedWith(performanceGameOrder(null) { null }))
        assertEquals(expected, games.sortedWith(performanceGameOrder(null) { 1_792_590_000_000L }))
    }

    @Test fun `timestamp ordering keeps full Long precision`() {
        val older = game("Alpha")
        val newer = game("Zulu")
        val timestamps = mapOf(older.packageName to Int.MAX_VALUE.toLong(), newer.packageName to Long.MAX_VALUE)
        assertEquals(listOf(newer, older), listOf(older, newer).sortedWith(performanceGameOrder(null, timestamps::get)))
    }

    @Test fun `empty and single game libraries remain valid`() {
        val comparator = performanceGameOrder(null) { null }
        assertTrue(emptyList<InstalledGame>().sortedWith(comparator).isEmpty())
        assertEquals(listOf(game("Only")), listOf(game("Only")).sortedWith(comparator))
    }
}
