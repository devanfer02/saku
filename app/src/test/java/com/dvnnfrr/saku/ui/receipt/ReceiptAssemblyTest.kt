package com.dvnnfrr.saku.ui.receipt

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.receipt.ReceiptItem
import com.dvnnfrr.saku.receipt.ReceiptReading
import com.dvnnfrr.saku.receipt.ReceiptSource
import com.dvnnfrr.saku.ui.entry.EntryTag
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Receipt screen's assembly: the spec scenarios of receipt-items, without a database. */
class ReceiptAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val scannedAt = LocalDateTime(2026, 10, 31, 19, 45)

    private val makan = category(1, "Makan & Minum")
    private val kos = category(2, "Kebutuhan Kos")
    private val cash = account(1, "Cash")
    private val card = account(2, "Card")
    private val grocery = Tag(5, "Monthly grocery", 0, archived = false)

    private fun category(id: Long, name: String) = Category(id, name, CategoryKind.EXPENSE, ColorSlot(0), id.toInt(), false)

    private fun account(id: Long, name: String, archived: Boolean = false) = Account(
        id, name, AccountType.entries.first(), ColorSlot(0), id.toInt(), archived,
        Money.ZERO, Instant.fromEpochSeconds(0), null, Money.ZERO,
    )

    /** The alignment page example: six items, an item discount, a card discount, 206.100 paid. */
    private val cardDiscount = ReceiptReading(
        total = Money(206_100),
        otherAmounts = listOf(Money(226_100), Money(250_000)),
        date = LocalDate(2026, 10, 30),
        store = "Indomaret",
        unplaced = listOf("TERIMA KASIH"),
        items = listOf(
            ReceiptItem("INDOMIE GRG SPCL", 5, Money(15_500)),
            ReceiptItem("MINYAK GORENG 2L", 1, Money(38_900)),
            ReceiptItem("BERAS PREMIUM 5KG", 1, Money(72_500)),
            ReceiptItem("SUSU UHT 1L", 2, Money(36_400), Money(4_000)),
            ReceiptItem("SABUN CUCI 800ML", 1, Money(21_900)),
            ReceiptItem("DETERJEN 1.8KG", 1, Money(44_900)),
        ),
        receiptDiscounts = listOf(Money(20_000)),
    )

    private fun assemble(
        reading: ReceiptReading = cardDiscount,
        choices: ReceiptChoices = ReceiptChoices(),
        dictionary: Map<String, Long> = emptyMap(),
        accounts: List<Account> = listOf(cash, card),
        defaults: ReceiptDefaults = ReceiptDefaults(),
    ) = assembleReceipt(
        reading = reading,
        choices = choices,
        dictionary = dictionary,
        categories = listOf(makan, kos),
        accounts = accounts,
        allTags = listOf(grocery),
        defaults = defaults,
        photos = 1,
        readingPhoto = false,
    )

    @Test
    fun `per item spreads the card discount and the rows add up to what was paid`() {
        val state = assemble(choices = ReceiptChoices(accountId = card.id))
        val rows = receiptRows(state, scannedAt, zone)

        assertEquals(
            listOf(14_129L, 35_459L, 66_087L, 29_534L, 19_963L, 40_928L),
            rows.map { it.amount.rupiah },
        )
        assertEquals(206_100L, rows.sumOf { it.amount.rupiah })
        assertEquals("Susu UHT 1L ×2", rows[3].note)
        assertTrue(rows.all { it.accountId == card.id && it.type == TxnType.EXPENSE })
        assertTrue(state.check!!.matches)
    }

    @Test
    fun `rows land on the receipt's day at the scan's time`() {
        val rows = receiptRows(assemble(), scannedAt, zone)

        assertEquals(LocalDateTime(2026, 10, 30, 19, 45).toInstant(zone), rows.first().occurredAt)
    }

    @Test
    fun `a date in the future or over a year back saves today`() {
        val today = LocalDate(2026, 10, 31)

        assertEquals(today, receiptDay(LocalDate(2026, 11, 2), today))
        assertEquals(today, receiptDay(LocalDate(2025, 10, 31), today))
        assertEquals(LocalDate(2025, 11, 1), receiptDay(LocalDate(2025, 11, 1), today))
        assertEquals(today, receiptDay(null, today))
    }

    @Test
    fun `a known item takes its category from history, and a pick overrides it`() {
        val known = assemble(dictionary = mapOf("MINYAK GORENG 2L" to makan.id))
        val minyak = known.items[1]
        assertEquals(makan.id, minyak.categoryId)
        assertEquals("Makan & Minum", minyak.categoryName)
        assertEquals("Minyak Goreng 2L", minyak.name)
        assertTrue(minyak.fromHistory)
        assertNull(known.items[5].categoryId)

        val picked = assemble(
            dictionary = mapOf("MINYAK GORENG 2L" to makan.id),
            choices = ReceiptChoices(categoryByItem = mapOf("MINYAK GORENG 2L" to kos.id)),
        )
        assertEquals(kos.id, picked.items[1].categoryId)
        assertFalse(picked.items[1].fromHistory)
    }

    @Test
    fun `a dictionary category that was archived is not offered`() {
        val state = assembleReceipt(
            cardDiscount, ReceiptChoices(), mapOf("MINYAK GORENG 2L" to 9L),
            listOf(makan), listOf(cash), emptyList(), ReceiptDefaults(), 1, false,
        )

        assertNull(state.items[1].categoryId)
    }

    @Test
    fun `a part not photographed shows what is missing, and the gap row is off by default`() {
        val found = cardDiscount.copy(
            items = cardDiscount.items.dropLast(1),
            receiptDiscounts = emptyList(),
            total = Money(206_100),
        )
        val state = assemble(reading = found)

        assertEquals(Money(181_200), state.check!!.itemsNet)
        assertEquals(Money(24_900), state.gap)
        assertTrue(state.gapRowOffered)
        assertEquals(5, receiptRows(state, scannedAt, zone).size)

        val withGap = assemble(reading = found, choices = ReceiptChoices(gapRow = true, gapCategoryId = kos.id))
        val rows = receiptRows(withGap, scannedAt, zone)
        assertEquals(6, rows.size)
        assertEquals(Money(24_900), rows.last().amount)
        assertEquals("Indomaret other items", rows.last().note)
        assertEquals(kos.id, rows.last().categoryId)
        assertEquals(206_100L, rows.sumOf { it.amount.rupiah })
    }

    @Test
    fun `items over the total offer no gap row`() {
        val state = assemble(reading = cardDiscount.copy(total = Money(200_000)))

        assertEquals(Money(-6_100), state.check!!.difference)
        assertNull(state.gap)
        assertFalse(state.gapRowOffered)
    }

    @Test
    fun `one row saves the total under the category with the largest share and the store as note`() {
        val state = assemble(
            choices = ReceiptChoices(recordAs = RecordAs.ONE_ROW),
            dictionary = mapOf("MINYAK GORENG 2L" to makan.id, "BERAS PREMIUM 5KG" to kos.id),
        )
        val rows = receiptRows(state, scannedAt, zone)

        assertEquals(1, rows.size)
        assertEquals(Money(206_100), rows.single().amount)
        assertEquals("Indomaret", rows.single().note)
        assertEquals(kos.id, rows.single().categoryId)
    }

    @Test
    fun `a reading with no items records one row only`() {
        val screenshot = ReceiptReading(Money(46_882), emptyList(), LocalDate(2026, 10, 15), "Kopi Tuku Cipete", emptyList())
        val state = assemble(reading = screenshot, choices = ReceiptChoices(recordAs = RecordAs.PER_ITEM))

        assertFalse(state.canRecordPerItem)
        assertEquals(RecordAs.ONE_ROW, state.recordAs)
        assertNull(state.check)
        assertEquals(1, state.unsetCount)
        assertEquals(Money(46_882), receiptRows(state, scannedAt, zone).single().amount)
    }

    @Test
    fun `a known source is shown, and an order with no restaurant notes the app`() {
        val order = ReceiptReading(Money(63_100), emptyList(), null, null, emptyList(), source = ReceiptSource.GOFOOD)
        val state = assemble(reading = order)

        assertEquals("GoFood", state.source)
        assertEquals("GoFood", state.oneRowNote)
        assertNull(assemble(reading = order.copy(source = null)).source)
        assertEquals("Warung Bu Tini", assemble(reading = order.copy(store = "Warung Bu Tini")).oneRowNote)
    }

    @Test
    fun `a chip replaces the total in one tap and the old total becomes a chip`() {
        val state = assemble(choices = ReceiptChoices(total = Money(226_100)))

        assertEquals(Money(226_100), state.total)
        assertEquals(listOf(Money(206_100), Money(250_000)), state.chips)
        assertEquals(Money(20_000), state.check!!.difference)
    }

    @Test
    fun `with no total, one row saves the items' sum`() {
        val state = assemble(reading = cardDiscount.copy(total = null), choices = ReceiptChoices(recordAs = RecordAs.ONE_ROW))

        assertNull(state.check)
        assertEquals(Money(206_100), state.oneRowAmount)
    }

    @Test
    fun `use for all unset fills only what is unset, then nothing is unset`() {
        val before = assemble(
            dictionary = mapOf("MINYAK GORENG 2L" to makan.id),
            choices = ReceiptChoices(gapRow = true),
        )
        assertEquals(5, before.unsetCount)

        val after = assemble(
            dictionary = mapOf("MINYAK GORENG 2L" to makan.id),
            choices = ReceiptChoices(gapRow = true).fillUnset(before, kos.id),
        )
        assertEquals(0, after.unsetCount)
        assertEquals(makan.id, after.items[1].categoryId)
        assertEquals(kos.id, after.items[0].categoryId)
    }

    @Test
    fun `the account and tags default to the last receipt's`() {
        val state = assemble(defaults = ReceiptDefaults(accountId = card.id, tagIds = setOf(grocery.id)))

        assertEquals(card, state.account)
        assertEquals(listOf(EntryTag(grocery.id, grocery.name)), state.tags)
    }

    @Test
    fun `an archived last account falls back to the first active one, and cleared tags stay cleared`() {
        val state = assemble(
            accounts = listOf(cash, account(2, "Card", archived = true)),
            defaults = ReceiptDefaults(accountId = 2, tagIds = setOf(grocery.id)),
            choices = ReceiptChoices(tags = emptyList()),
        )

        assertEquals(cash, state.account)
        assertEquals(emptyList<EntryTag>(), state.tags)
    }

    @Test
    fun `the one row note gains a line Saku could not place`() {
        assertEquals("Indomaret, TERIMA KASIH", appendToNote("Indomaret", "TERIMA KASIH"))
        assertEquals("TERIMA KASIH", appendToNote(" ", "TERIMA KASIH"))
    }

    @Test
    fun `items show readable names, and a row saved under the printed name still finds its category`() {
        val printed = cardDiscount.copy(items = listOf(ReceiptItem("INDOMIE GRG SPCL", 5, Money(15_500))), receiptDiscounts = emptyList())
        val state = assemble(reading = printed, dictionary = mapOf("INDOMIE GRG SPCL" to makan.id))

        val item = state.items.single()
        assertEquals("Indomie Goreng Special", item.name)
        assertEquals("Indomie Goreng Special ×5", item.note)
        assertEquals(makan.id, item.categoryId)
        assertTrue(item.fromHistory)
    }

    @Test
    fun `an edited name is saved and teaches its changed word`() {
        val state = assemble(choices = ReceiptChoices(nameByItem = mapOf("BERAS PREMIUM 5KG" to "Buris Premium 5KG")))

        assertEquals("Buris Premium 5KG", state.items[2].note)
        assertEquals(mapOf("BERAS" to "Buris"), learnedFrom(state).words)
        assertTrue(learnedFrom(assemble()).isEmpty)
    }

    @Test
    fun `a learned word is used on the next receipt`() {
        val state = assembleReceipt(
            cardDiscount, ReceiptChoices(), emptyMap(), listOf(makan), listOf(cash), emptyList(),
            ReceiptDefaults(), 1, false, learned = com.dvnnfrr.saku.receipt.LearnedWords(words = mapOf("BERAS" to "Buris")),
        )

        assertEquals("Buris Premium 5KG", state.items[2].name)
    }
}
