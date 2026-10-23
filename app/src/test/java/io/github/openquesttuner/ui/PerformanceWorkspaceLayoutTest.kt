package io.github.openquesttuner.ui

import org.junit.Assert.*
import org.junit.Test

class PerformanceWorkspaceLayoutTest {
    @Test fun `Quest landscape shows six compact app cards per page`() {
        val page = PerformanceWorkspaceLayout.page(838f, 428f, 1f)
        assertEquals(3, page.columns); assertEquals(2, page.rows); assertEquals(6, page.size)
        assertTrue(page.rows * page.rowHeightDp + (page.rows - 1) * 8 + 40 <= 428)
    }
    @Test fun `larger fonts reduce density while retaining controls`() {
        val page = PerformanceWorkspaceLayout.page(838f, 428f, 1.3f)
        assertEquals(2, page.columns); assertEquals(1, page.rows)
        assertTrue(page.rowHeightDp >= 228f)
    }
    @Test fun `paging stays bounded across headset window sizes`() {
        for (w in 360..1600 step 40) for (h in 300..1000 step 40) {
            val page = PerformanceWorkspaceLayout.page(w.toFloat(), h.toFloat(), 1f)
            assertTrue(page.size in 1..12)
            assertTrue(page.rows * page.rowHeightDp + (page.rows - 1) * 8 + 40 <= h)
        }
    }
    @Test fun `invalid dimensions and short windows preserve a reachable page`() {
        assertEquals(1, PerformanceWorkspaceLayout.page(Float.NaN, -1f, Float.NaN).size)
        assertEquals(1, PerformanceWorkspaceLayout.page(320f, 120f, 1f).size)
    }
}
