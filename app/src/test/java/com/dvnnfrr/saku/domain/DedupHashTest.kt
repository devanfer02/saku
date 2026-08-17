package com.dvnnfrr.saku.domain

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupHashTest {

    private val at = Instant.parse("2026-08-16T18:47:34Z")
    private val bca = 1L
    private val cash = 2L

    private fun hash(
        instant: Instant = at,
        type: TxnType = TxnType.EXPENSE,
        amount: Money = Money(187_000),
        accountId: Long = bca,
        toAccountId: Long? = null,
        category: String? = "Makan & Minum",
        note: String? = "Guri Ramen",
    ) = DedupHash.of(instant, type, amount, accountId, toAccountId, category, note)

    @Test
    fun `identical rows hash identically`() {
        assertEquals(hash(), hash())
    }

    @Test
    fun `sub-second precision is discarded`() {
        // The export carries microseconds; two rows in the same second are the same row.
        assertEquals(hash(), hash(instant = Instant.parse("2026-08-16T18:47:34.656Z")))
    }

    @Test
    fun `a different second is a different row`() {
        assertNotEquals(hash(), hash(instant = at.plus(kotlin.time.Duration.parse("1s"))))
    }

    @Test
    fun `amount participates in the key`() {
        assertNotEquals(hash(), hash(amount = Money(187_001)))
    }

    @Test
    fun `type participates in the key`() {
        assertNotEquals(hash(), hash(type = TxnType.INCOME))
    }

    @Test
    fun `note participates in the key`() {
        assertNotEquals(hash(), hash(note = "Kaizen Ayce"))
    }

    /**
     * Regression. Without the account in the key, two real rows differing only by which
     * account paid hashed the same, so the importer dropped one and called it a
     * duplicate. With Subcategory and Description empty on all 1,200 reference rows the
     * key collapsed to (second, type, amount, category), which collides easily.
     */
    @Test
    fun `account participates in the key`() {
        assertNotEquals(
            "Same fare, same minute, different account is two transactions",
            hash(accountId = bca, note = null),
            hash(accountId = cash, note = null),
        )
    }

    @Test
    fun `destination account participates in the key`() {
        assertNotEquals(
            hash(type = TxnType.TRANSFER, toAccountId = 3L, category = null),
            hash(type = TxnType.TRANSFER, toAccountId = 4L, category = null),
        )
    }

    @Test
    fun `category is matched case-insensitively`() {
        assertEquals(hash(), hash(category = "  makan & MINUM "))
    }

    @Test
    fun `surrounding whitespace in a note is ignored`() {
        assertEquals(hash(), hash(note = "  Guri Ramen  "))
    }

    @Test
    fun `null and empty are the same absence`() {
        assertEquals(hash(category = null, note = null), hash(category = "", note = ""))
    }

    @Test
    fun `field boundaries cannot be forged by concatenation`() {
        // Without a boundary, category "AB" + note "C" would collide with "A" + "BC".
        assertNotEquals(hash(category = "AB", note = "C"), hash(category = "A", note = "BC"))
    }

    /**
     * Regression. The old key joined fields with 0x1F on the reasoning that nobody types
     * a unit separator. Imported and pasted text can contain one, and a field carrying
     * the delimiter could forge a boundary.
     */
    @Test
    fun `a separator inside a field cannot forge a boundary`() {
        val sep = 31.toChar()
        assertNotEquals(
            hash(category = "a", note = "b${sep}c"),
            hash(category = "a${sep}b", note = "c"),
        )
    }

    @Test
    fun `hash is a hex sha256`() {
        assertEquals(64, hash().length)
        assert(hash().all { it in "0123456789abcdef" })
    }
}
