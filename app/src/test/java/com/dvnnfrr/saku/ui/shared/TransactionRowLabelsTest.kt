package com.dvnnfrr.saku.ui.shared

import com.dvnnfrr.saku.domain.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRowLabelsTest {

    private fun labels(
        type: TxnType,
        category: String? = null,
        note: String? = null,
        accountName: String = "BCA",
        toAccountName: String? = null,
        conjunction: String = "to",
    ) = transactionRowLabels(
        type = type,
        category = category,
        note = note,
        accountName = accountName,
        toAccountName = toAccountName,
        titleFallback = type.defaultTitle,
        conjunction = conjunction,
    )

    @Test
    fun `an ordinary BCA expense reads category, then account and note`() {
        val result = labels(TxnType.EXPENSE, category = "Jajan & Hiburan", note = "Spotify Premium")

        assertEquals("Jajan & Hiburan", result.primary)
        assertEquals("BCA · Spotify Premium", result.secondary)
    }

    @Test
    fun `the account leads, then the note`() {
        val result = labels(TxnType.EXPENSE, category = "Pemberian", note = "Uang Arisan")

        // The line ellipsises from the end, so a long note is cut before the account.
        assertEquals("BCA · Uang Arisan", result.secondary)
        assertEquals("BCA, Uang Arisan", result.spokenSecondary)
    }

    @Test
    fun `a row with only an account still names it`() {
        assertEquals("BCA", labels(TxnType.EXPENSE, category = "Kuliah").secondary)
    }

    @Test
    fun `a transfer names both accounts with an arrow between them`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = "Bibit")
        assertTrue(result.showsBothAccounts)
        assertEquals("BCA → Bibit", result.secondary)
    }

    @Test
    fun `a transfer reads both accounts aloud without relying on the arrow glyph`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = "Bibit")
        assertEquals("BCA to Bibit", result.spokenSecondary)
    }

    @Test
    fun `the conjunction is a parameter, not baked-in copy`() {
        val result = labels(
            TxnType.TRANSFER,
            accountName = "BCA",
            toAccountName = "Bibit",
            conjunction = "ke",
        )
        assertEquals("BCA ke Bibit", result.spokenSecondary)
    }

    @Test
    fun `a transfer falls back to naming itself rather than showing a category`() {
        assertEquals("Transfer", labels(TxnType.TRANSFER, toAccountName = "Bibit").primary)
    }

    @Test
    fun `a transfer missing its destination shows only the source account`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = null)
        assertFalse(result.showsBothAccounts)
        assertEquals("BCA", result.secondary)
    }

    @Test
    fun `a blank destination is treated as no destination`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = "   ")
        assertFalse(result.showsBothAccounts)
        assertEquals("BCA", result.secondary)
    }

    @Test
    fun `only a transfer shows two accounts`() {
        for (type in listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.ADJUSTMENT)) {
            val result = labels(type, category = "Kerjaan", toAccountName = "Bibit")
            assertFalse("$type showed two accounts", result.showsBothAccounts)
            assertEquals("BCA", result.secondary)
        }
    }

    @Test
    fun `a transfer names both accounts, then its note`() {
        val result = labels(
            TxnType.TRANSFER,
            note = "Tabungan",
            accountName = "BCA",
            toAccountName = "RDPU",
        )

        assertEquals("BCA → RDPU · Tabungan", result.secondary)
        assertEquals("BCA to RDPU, Tabungan", result.spokenSecondary)
    }

    @Test
    fun `an expense leads with its category`() {
        assertEquals("Makan & Minum", labels(TxnType.EXPENSE, category = "Makan & Minum").primary)
    }

    @Test
    fun `a row with no category falls back to its type's name`() {
        assertEquals("Uncategorised", labels(TxnType.EXPENSE).primary)
        assertEquals("Uncategorised", labels(TxnType.INCOME).primary)
        assertEquals("Adjustment", labels(TxnType.ADJUSTMENT).primary)
        assertEquals("Transfer", labels(TxnType.TRANSFER).primary)
    }

    @Test
    fun `a blank category is treated as no category`() {
        assertEquals("Uncategorised", labels(TxnType.EXPENSE, category = "  ").primary)
    }

    @Test
    fun `a blank note leaves the account alone`() {
        val result = labels(TxnType.EXPENSE, note = " ", accountName = "BCA")
        assertEquals("BCA", result.secondary)
    }
}
