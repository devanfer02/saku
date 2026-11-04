package com.dvnnfrr.saku.ui.tags

import com.dvnnfrr.saku.domain.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsAssemblyTest {

    private val grocery = Tag(1, "Monthly grocery", 1, archived = false)
    private val bali = Tag(2, "Trip Bali", 0, archived = true)
    private val ramadan = Tag(3, "Ramadan", 2, archived = false)
    private val all = listOf(grocery, bali, ramadan)

    @Test
    fun `live tags come first with their counts, archived ones after`() {
        val state = assembleTags(all, mapOf(1L to 14, 2L to 22), editing = null)
        assertEquals(listOf(TagRowUi(1, "Monthly grocery", 14, false), TagRowUi(3, "Ramadan", 0, false)), state.active)
        assertEquals(listOf(TagRowUi(2, "Trip Bali", 22, true)), state.archived)
    }

    @Test
    fun `rename refuses another tag's name in any case`() {
        assertEquals(RenameProblem.Taken("Trip Bali"), renameProblem("trip  bali", 1, all))
        assertNull(renameProblem("MONTHLY grocery", 1, all))
        assertEquals(RenameProblem.Invalid, renameProblem("a;b", 1, all))
        assertNull(renameProblem("Belanja bulanan", 1, all))
    }

    @Test
    fun `save is offered only for a different valid name`() {
        val edit = TagEditUi(1, "Monthly grocery", "Monthly grocery", archived = false)
        assertFalse(edit.canSave)
        assertTrue(edit.copy(draft = "Belanja bulanan").canSave)
        assertTrue(edit.copy(draft = "monthly grocery").canSave)
        assertFalse(edit.copy(draft = "Trip Bali", problem = RenameProblem.Taken("Trip Bali")).canSave)
        assertFalse(edit.copy(draft = "  ").canSave)
    }
}
