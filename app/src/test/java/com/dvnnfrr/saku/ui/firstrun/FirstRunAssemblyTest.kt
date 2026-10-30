package com.dvnnfrr.saku.ui.firstrun

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")

    private fun account(
        id: Long,
        name: String,
        type: AccountType = AccountType.BANK,
        sortOrder: Int = id.toInt(),
        archived: Boolean = false,
        opening: Long = 0,
    ) = Account(
        id = id,
        name = name,
        type = type,
        colorSlot = ColorSlot(0),
        sortOrder = sortOrder,
        archived = archived,
        openingBalance = Money(opening),
        openingAsOf = Instant.fromEpochSeconds(0),
        lastReconciled = null,
        balance = Money(opening),
    )

    private fun category(name: String, kind: CategoryKind, archived: Boolean = false) =
        Category(id = 1, name = name, kind = kind, colorSlot = ColorSlot(0), sortOrder = 0, archived = archived)

    private val cash = account(1, "Cash", AccountType.CASH, sortOrder = 0)
    private val card = account(2, "Card", AccountType.BANK, sortOrder = 1)

    // ---- Gate --------------------------------------------------------------------------

    @Test
    fun `setup is offered only to an unused install`() {
        assertTrue(shouldOfferFirstRun(completed = false, ledgerHasRows = false))
        assertFalse(shouldOfferFirstRun(completed = false, ledgerHasRows = true))
        assertFalse(shouldOfferFirstRun(completed = true, ledgerHasRows = false))
        assertFalse(shouldOfferFirstRun(completed = true, ledgerHasRows = true))
    }

    @Test
    fun `the as-of date is the start of today in the device zone`() {
        val lateEvening = LocalDateTime(2026, 10, 30, 23, 50).toInstant(zone)

        assertEquals(LocalDateTime(2026, 10, 30, 0, 0).toInstant(zone), startOfToday(lateEvening, zone))
        assertEquals(LocalDate(2026, 10, 30), today(lateEvening, zone))
    }

    // ---- Which accounts are listed -----------------------------------------------------

    @Test
    fun `live accounts are listed in sort order with empty amounts`() {
        val drafts = accountDraftsFrom(listOf(card, cash))

        assertEquals(listOf("Cash", "Card"), drafts.map { it.name })
        assertEquals(listOf(AccountType.CASH, AccountType.BANK), drafts.map { it.type })
        assertTrue(drafts.all { it.amountDigits.isEmpty() && !it.isAdded })
    }

    @Test
    fun `archived accounts are not listed`() {
        val old = account(3, "Old", archived = true)

        assertEquals(listOf("Cash", "Card"), accountDraftsFrom(listOf(cash, card, old)).map { it.name })
    }

    @Test
    fun `an opening balance already written is shown, a liability as owed`() {
        val written = account(4, "Kartu", AccountType.LIABILITY, opening = -300_000)

        assertEquals("300000", accountDraftsFrom(listOf(written)).single().amountDigits)
    }

    // ---- Amounts -----------------------------------------------------------------------

    @Test
    fun `the amount field keeps digits only, without leading zeros`() {
        assertEquals("1500000", amountDigitsFrom("1.500.000"))
        assertEquals("250", amountDigitsFrom("-0250"))
        assertEquals("", amountDigitsFrom("000"))
        assertEquals(15, amountDigitsFrom("9".repeat(20)).length)
    }

    @Test
    fun `an empty amount is zero`() {
        val draft = AccountDraft("k", 1, "Cash", AccountType.CASH)

        assertEquals(Money.ZERO, draft.opening)
    }

    @Test
    fun `a liability is stored as a negative opening balance`() {
        val owed = AccountDraft("k", null, "Kartu", AccountType.LIABILITY, amountDigits = "750000")
        val held = owed.copy(type = AccountType.CASH)

        assertEquals(Money(-750_000), owed.opening)
        assertEquals(Money(750_000), held.opening)
    }

    // ---- Validation --------------------------------------------------------------------

    @Test
    fun `a blank name is a problem`() {
        val drafts = accountDraftsFrom(listOf(cash)) + AccountDraft(addedKey(0), null, "  ", AccountType.BANK)

        assertEquals(mapOf(addedKey(0) to DraftProblem.NAME_MISSING), validateAccountDrafts(drafts, emptyList()))
    }

    @Test
    fun `a duplicate name flags the later row, ignoring case and whitespace`() {
        val drafts = accountDraftsFrom(listOf(cash, card)).map {
            if (it.name == "Card") it.copy(name = " cash ") else it
        }

        assertEquals(
            mapOf(existingKey(2) to DraftProblem.NAME_TAKEN),
            validateAccountDrafts(drafts, emptyList()),
        )
    }

    @Test
    fun `an archived account's name is taken`() {
        val drafts = listOf(AccountDraft(addedKey(0), null, "BCA", AccountType.BANK))

        assertEquals(
            mapOf(addedKey(0) to DraftProblem.NAME_TAKEN),
            validateAccountDrafts(drafts, archivedNames(listOf(account(9, "bca", archived = true)))),
        )
    }

    @Test
    fun `untouched seeded rows are valid`() {
        assertTrue(validateAccountDrafts(accountDraftsFrom(listOf(cash, card)), emptyList()).isEmpty())
    }

    @Test
    fun `problems stay hidden until the user tries to move on`() {
        val drafts = listOf(AccountDraft(addedKey(0), null, "", AccountType.BANK))
        fun state(show: Boolean) = assembleSetup(
            step = SetupStep.ACCOUNTS, drafts = drafts, showProblems = show, accounts = emptyList(),
            picks = CategoryPicks(), categories = emptyList(), today = LocalDate(2026, 10, 30),
            saving = false, refusal = null, finished = false,
        )

        assertFalse(state(show = false).hasProblems)
        assertTrue(state(show = true).hasProblems)
    }

    // ---- What gets written -------------------------------------------------------------

    @Test
    fun `an untouched account still gets its opening balance, and nothing else`() {
        val writes = planAccountWrites(accountDraftsFrom(listOf(cash)), listOf(cash))

        assertEquals(listOf(AccountWrite.Update(1, rename = null, retype = null, opening = Money.ZERO)), writes)
    }

    @Test
    fun `a rename and a retype are written only when changed`() {
        val drafts = accountDraftsFrom(listOf(card)).map {
            it.copy(name = " BCA ", type = AccountType.BANK, amountDigits = "4100000")
        }

        assertEquals(
            listOf(AccountWrite.Update(2, rename = "BCA", retype = null, opening = Money(4_100_000))),
            planAccountWrites(drafts, listOf(card)),
        )
    }

    @Test
    fun `a retype to liability writes a negative opening`() {
        val drafts = accountDraftsFrom(listOf(card)).map {
            it.copy(type = AccountType.LIABILITY, amountDigits = "200000")
        }

        assertEquals(
            listOf(AccountWrite.Update(2, rename = null, retype = AccountType.LIABILITY, opening = Money(-200_000))),
            planAccountWrites(drafts, listOf(card)),
        )
    }

    @Test
    fun `an added row becomes an add, in row order`() {
        val drafts = accountDraftsFrom(listOf(cash)) +
            AccountDraft(addedKey(0), null, " Gopay ", AccountType.BANK, amountDigits = "150000")

        val writes = planAccountWrites(drafts, listOf(cash))

        assertEquals(AccountWrite.Add("Gopay", AccountType.BANK, Money(150_000)), writes[1])
        assertEquals(2, writes.size)
    }

    @Test
    fun `a row whose account vanished is dropped, not recreated`() {
        assertTrue(planAccountWrites(accountDraftsFrom(listOf(cash)), emptyList()).isEmpty())
    }

    // ---- Categories --------------------------------------------------------------------

    private val food = CategoryChoice("Food", CategoryKind.EXPENSE)
    private val transport = CategoryChoice("Transport", CategoryKind.EXPENSE)

    @Test
    fun `nothing is ticked at first`() {
        assertTrue(categoryOptions(CategoryPicks(), CategoryKind.EXPENSE, emptyList()).none { it.ticked })
        assertTrue(planCategoryWrites(CategoryPicks(), emptyList()).isEmpty())
    }

    @Test
    fun `ticked suggestions and typed names are written, nothing else`() {
        val picks = CategoryPicks()
            .toggle(food)
            .toggle(transport)
            .add(" Freelance ", CategoryKind.INCOME)

        assertEquals(
            listOf(food, transport, CategoryChoice("Freelance", CategoryKind.INCOME)),
            planCategoryWrites(picks, emptyList()),
        )
    }

    @Test
    fun `unticking takes a choice back`() {
        assertTrue(planCategoryWrites(CategoryPicks().toggle(food).toggle(food), emptyList()).isEmpty())
    }

    @Test
    fun `typing a suggestion's name ticks the suggestion instead of adding a twin`() {
        val picks = CategoryPicks().add("  food", CategoryKind.EXPENSE)

        assertTrue(picks.custom.isEmpty())
        assertEquals(listOf(food), planCategoryWrites(picks, emptyList()))
    }

    @Test
    fun `the same name may exist once per kind`() {
        val picks = CategoryPicks()
            .add("Bonus", CategoryKind.INCOME)
            .add("bonus", CategoryKind.INCOME)
            .add("Bonus", CategoryKind.EXPENSE)

        assertEquals(2, planCategoryWrites(picks, emptyList()).size)
    }

    @Test
    fun `a blank typed name changes nothing`() {
        assertEquals(CategoryPicks(), CategoryPicks().add("   ", CategoryKind.EXPENSE))
    }

    @Test
    fun `a live category of the same name is shown ticked and not written again`() {
        val existing = listOf(category("food", CategoryKind.EXPENSE))
        val picks = CategoryPicks().toggle(food)

        val option = categoryOptions(picks, CategoryKind.EXPENSE, existing).first { it.choice == food }
        assertTrue(option.ticked && option.exists)
        assertTrue(planCategoryWrites(picks, existing).isEmpty())
    }

    @Test
    fun `an archived category of the same name is left to findOrCreate`() {
        val existing = listOf(category("Food", CategoryKind.EXPENSE, archived = true))

        assertEquals(listOf(food), planCategoryWrites(CategoryPicks().toggle(food), existing))
    }
}
