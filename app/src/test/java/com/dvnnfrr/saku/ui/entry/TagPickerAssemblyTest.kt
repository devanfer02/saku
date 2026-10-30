package com.dvnnfrr.saku.ui.entry

import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.domain.TagName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagPickerAssemblyTest {

    private val grocery = Tag(1, "Monthly grocery", 0, archived = false)
    private val ramadan = Tag(2, "Ramadan", 1, archived = false)
    private val bali = Tag(3, "Trip Bali", 2, archived = true)
    private val groceries = Tag(4, "Big groceries", 3, archived = false)
    private val all = listOf(grocery, ramadan, bali, groceries)

    @Test
    fun `names are trimmed with inner spaces collapsed, case kept`() {
        assertEquals("Monthly grocery", TagName.normalize("  Monthly   grocery "))
        assertNull(TagName.normalize("   "))
        assertNull(TagName.normalize("a;b"))
        assertEquals("x".repeat(30), TagName.normalize("x".repeat(30)))
        assertNull(TagName.normalize("x".repeat(31)))
    }

    @Test
    fun `blank text lists every unarchived tag not on the row`() {
        val picker = tagPicker(all, listOf(EntryTag(2, "Ramadan")), "")
        assertEquals(listOf(grocery, groceries), picker.matches)
        assertNull(picker.createName)
    }

    @Test
    fun `typed text matches ignoring case, names starting with it first`() {
        val picker = tagPicker(all, emptyList(), "GROC")
        assertEquals(listOf(grocery, groceries), picker.matches)
        assertEquals(listOf(ramadan, grocery, groceries), tagPicker(all, emptyList(), "r").matches)
        assertEquals("GROC", picker.createName)
    }

    @Test
    fun `the same name in other case and spacing offers the existing tag, not a new one`() {
        val picker = tagPicker(all, emptyList(), "monthly  grocery")
        assertEquals(listOf(grocery), picker.matches)
        assertNull(picker.createName)
        assertEquals(listOf(EntryTag(1, "Monthly grocery")), emptyList<EntryTag>().plusTyped("monthly  grocery", all))
    }

    @Test
    fun `an archived tag is hidden from browsing but found by its whole name`() {
        assertEquals(emptyList<Tag>(), tagPicker(all, emptyList(), "trip").matches)
        assertEquals(listOf(bali), tagPicker(all, emptyList(), "trip bali").matches)
        assertNull(tagPicker(all, emptyList(), "trip bali").createName)
    }

    @Test
    fun `a new name becomes a chip with no id, created on Save`() {
        assertEquals(listOf(EntryTag(null, "Arisan")), emptyList<EntryTag>().plusTyped(" Arisan ", all))
    }

    @Test
    fun `an invalid or repeated name changes nothing and says why`() {
        val row = listOf(EntryTag(1, "Monthly grocery"))
        assertEquals(row, row.plusTyped("a;b", all))
        assertEquals(row, row.plusTyped("MONTHLY GROCERY", all))
        assertEquals(row, row.plusTag(grocery))
        assertEquals(TagNameProblem.HAS_SEPARATOR, tagPicker(all, row, "a;b").problem)
        assertEquals(TagNameProblem.TOO_LONG, tagPicker(all, row, "x".repeat(31)).problem)
        assertNull(tagPicker(all, row, "x".repeat(30)).problem)
    }

    @Test
    fun `a saved row's tags come back in the user's order`() {
        assertEquals(
            listOf(EntryTag(1, "Monthly grocery"), EntryTag(3, "Trip Bali")),
            entryTagsOf(setOf(3, 1), all),
        )
    }
}
