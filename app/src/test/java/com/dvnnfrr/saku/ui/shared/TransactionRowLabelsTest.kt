package com.dvnnfrr.saku.ui.shared

import com.dvnnfrr.saku.domain.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRowLabelsTest {

    private fun labels(
        type: TxnType,
        category: String? = null,
        subcategory: String? = null,
        note: String? = null,
        accountName: String = "BCA",
        toAccountName: String? = null,
        conjunction: String = "to",
    ) = transactionRowLabels(
        type = type,
        category = category,
        subcategory = subcategory,
        note = note,
        accountName = accountName,
        toAccountName = toAccountName,
        titleFallback = type.defaultTitle,
        conjunction = conjunction,
    )

    @Test
    fun `a transfer names both accounts with an arrow between them`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = "Bibit")
        assertTrue(result.showsBothAccounts)
        assertEquals("BCA → Bibit", result.accountLine)
    }

    @Test
    fun `a transfer reads both accounts aloud without relying on the arrow glyph`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = "Bibit")
        assertEquals("BCA to Bibit", result.spokenAccountLine)
    }

    @Test
    fun `the conjunction is a parameter, not baked-in copy`() {
        val result = labels(
            TxnType.TRANSFER,
            accountName = "BCA",
            toAccountName = "Bibit",
            conjunction = "ke",
        )
        assertEquals("BCA ke Bibit", result.spokenAccountLine)
    }

    @Test
    fun `a transfer falls back to naming itself rather than showing a category`() {
        assertEquals("Transfer", labels(TxnType.TRANSFER, toAccountName = "Bibit").primary)
    }

    @Test
    fun `a transfer missing its destination shows only the source account`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = null)
        assertFalse(result.showsBothAccounts)
        assertEquals("BCA", result.accountLine)
        assertFalse(result.accountLine.contains("→"))
    }

    @Test
    fun `a blank destination is treated as no destination`() {
        val result = labels(TxnType.TRANSFER, accountName = "BCA", toAccountName = "   ")
        assertFalse(result.showsBothAccounts)
        assertEquals("BCA", result.accountLine)
    }

    @Test
    fun `only a transfer shows two accounts`() {
        for (type in listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.ADJUSTMENT)) {
            val result = labels(type, category = "Kerjaan", toAccountName = "Bibit")
            assertFalse("$type showed two accounts", result.showsBothAccounts)
            assertEquals("BCA", result.accountLine)
        }
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
    fun `the subcategory rides the account line, not the note line`() {
        val result = labels(
            TxnType.EXPENSE,
            category = "Pemberian",
            subcategory = "Rutin keluarga",
            note = "Agustus",
            accountName = "BCA",
        )

        // The spec asks for an account line reading swatch, account, subcategory.
        // Folding the subcategory in beside the note let a long note ellipsise away
        // the one field the old app never captured.
        assertEquals("Agustus", result.secondary)
        assertEquals("Rutin keluarga", result.subcategory)
        assertEquals("BCA · Rutin keluarga", result.accountLine)
        assertEquals("BCA, Rutin keluarga", result.spokenAccountLine)
    }

    @Test
    fun `a transfer names both accounts and still carries its subcategory`() {
        val result = labels(
            TxnType.TRANSFER,
            subcategory = "Rutin keluarga",
            accountName = "BCA",
            toAccountName = "RDPU",
        )

        assertEquals("BCA → RDPU · Rutin keluarga", result.accountLine)
        assertEquals("BCA to RDPU, Rutin keluarga", result.spokenAccountLine)
    }

    @Test
    fun `the second line is the note alone`() {
        assertNull(labels(TxnType.EXPENSE, subcategory = "Rutin keluarga").secondary)
        assertEquals("Agustus", labels(TxnType.EXPENSE, note = "Agustus").secondary)
    }

    @Test
    fun `no note leaves no second line`() {
        assertNull(labels(TxnType.EXPENSE, category = "Kuliah").secondary)
    }

    @Test
    fun `a blank subcategory does not reach the account line`() {
        val result = labels(TxnType.EXPENSE, subcategory = "   ", accountName = "BCA")

        assertNull(result.subcategory)
        assertEquals("BCA", result.accountLine)
    }
}
