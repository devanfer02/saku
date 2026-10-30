package com.dvnnfrr.saku.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** A pushed screen keeps its tab lit, so opening it never reads as a tab switch. */
class OwningDestinationTest {

    @Test
    fun `search keeps the Trans tab lit`() {
        assertEquals(SakuDestination.TRANSACTIONS, SakuDestination.owning(TransRoute.SEARCH.route))
    }

    @Test
    fun `More screens keep More lit and tabs own themselves`() {
        assertEquals(SakuDestination.MORE, SakuDestination.owning(MoreRoute.CATEGORIES.route))
        assertEquals(SakuDestination.STATS, SakuDestination.owning(SakuDestination.STATS.route))
    }
}
