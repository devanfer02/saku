package com.dvnnfrr.saku.ui.receipt

import com.dvnnfrr.saku.data.repository.NewTransaction
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.sum
import com.dvnnfrr.saku.receipt.ReceiptReading
import com.dvnnfrr.saku.receipt.LearnedWords
import com.dvnnfrr.saku.receipt.itemKey
import com.dvnnfrr.saku.receipt.learnedWords
import com.dvnnfrr.saku.receipt.readableItemName
import com.dvnnfrr.saku.receipt.spreadOverItems
import com.dvnnfrr.saku.ui.entry.EntryTag
import com.dvnnfrr.saku.ui.entry.entryTagsOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

/*
 * Pure assembly for the Receipt screen: a reading and the user's choices in, the screen's
 * state and the rows to save out. Tested on the JVM without a database or a ViewModel.
 * See openspec/changes/archive/2026-11-04-receipt-items/design.md, sections 5 to 7.
 */

/** How the receipt is recorded. A reading with no items allows [ONE_ROW] only. */
enum class RecordAs { ONE_ROW, PER_ITEM }

/**
 * Everything the user chose on the Receipt screen. Item choices are keyed by [itemKey], not
 * by position, so they survive Add next part, which reads the items again.
 */
data class ReceiptChoices(
    val recordAs: RecordAs = RecordAs.PER_ITEM,
    val categoryByItem: Map<String, Long> = emptyMap(),
    /** Names the user typed over Saku's readable name, by the printed name's item key. */
    val nameByItem: Map<String, String> = emptyMap(),
    /** A chip tapped on the total; null keeps the total Saku picked. */
    val total: Money? = null,
    val oneRowCategoryId: Long? = null,
    /** Null keeps the store as the note. */
    val oneRowNote: String? = null,
    val gapRow: Boolean = false,
    val gapCategoryId: Long? = null,
    val accountId: Long? = null,
    /** Null keeps the tags of the last receipt save. */
    val tags: List<EntryTag>? = null,
)

/** What the last receipt save used, offered as this one's defaults. */
data class ReceiptDefaults(val accountId: Long? = null, val tagIds: Set<Long> = emptySet())

/** One item as the screen lists it and as it would be saved. */
data class ReceiptItemUi(
    /** The printed name's [itemKey]; the user's choices for this item are kept under it. */
    val key: String,
    /** The name to show and save: the user's edit, else [draft]. */
    val name: String,
    /** The name as printed on the receipt (`INDOMIE GRG SPCL`). */
    val printed: String = name,
    /** The readable name Saku made from [printed] (`Indomie Goreng Special`). */
    val draft: String = name,
    val quantity: Int,
    /** The line price for [quantity], as printed. */
    val price: Money,
    val discount: Money,
    /** What the row saves: net price less its share of receipt discounts, plus additions. */
    val amount: Money,
    val categoryId: Long?,
    val categoryName: String?,
    /** True when the category came from note history rather than from a pick here. */
    val fromHistory: Boolean,
) {
    /** The note saved: the name, with ` ×n` when more than one was bought. */
    val note: String get() = if (quantity > 1) "$name ×$quantity" else name

    val renamed: Boolean get() = name != draft
}

/** The items' sum after all discounts against the receipt total. */
data class ReceiptCheck(val itemsNet: Money, val total: Money) {
    /** What the items do not account for; negative when they add up to more than the total. */
    val difference: Money get() = total - itemsNet
    val matches: Boolean get() = difference.isZero
}

data class ReceiptUiState(
    /** How many photos have been read into this receipt. */
    val photos: Int = 0,
    /** True while a photo is being read. */
    val reading: Boolean = false,
    /** Set when the last photo had no text Saku could read. */
    val unreadable: Boolean = false,
    val store: String? = null,
    /** The app or shop the screen's labels named (`GoFood`, `Indomaret`); null when unknown. */
    val source: String? = null,
    val date: LocalDate? = null,
    val total: Money? = null,
    /** Other amounts on the receipt, to put in the total's place in one tap. */
    val chips: List<Money> = emptyList(),
    val items: List<ReceiptItemUi> = emptyList(),
    /** Null without both a total and items. */
    val check: ReceiptCheck? = null,
    /** `n` where photos `n` and `n + 1` share no row. */
    val gapsAfterPhoto: List<Int> = emptyList(),
    val recordAs: RecordAs = RecordAs.ONE_ROW,
    val gapRow: Boolean = false,
    val gapCategoryId: Long? = null,
    val oneRowAmount: Money? = null,
    val oneRowCategoryId: Long? = null,
    val oneRowNote: String = "",
    val unplaced: List<String> = emptyList(),
    val account: Account? = null,
    val tags: List<EntryTag> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
) {
    val canRecordPerItem: Boolean get() = items.isNotEmpty()

    /** The gap a gap row would save, when the items fall short of the total. */
    val gap: Money? get() = check?.difference?.takeIf { it.isPositive }

    val gapRowOffered: Boolean get() = recordAs == RecordAs.PER_ITEM && gap != null

    /** Categories still to choose before saving: items, the gap row, or the one row. */
    val unsetCount: Int get() = when (recordAs) {
        RecordAs.PER_ITEM -> items.count { it.categoryId == null } +
            (if (gapRowOffered && gapRow && gapCategoryId == null) 1 else 0)
        RecordAs.ONE_ROW -> if (oneRowCategoryId == null) 1 else 0
    }

    /** How many rows Save writes. */
    val rowCount: Int get() = when (recordAs) {
        RecordAs.PER_ITEM -> items.size + (if (gapRowOffered && gapRow) 1 else 0)
        RecordAs.ONE_ROW -> if (oneRowAmount?.isPositive == true) 1 else 0
    }

    val canSave: Boolean get() = !reading && account != null && rowCount > 0

    fun categoryName(id: Long?): String? = categories.firstOrNull { it.id == id }?.name
}

/**
 * The screen state for [reading] under [choices].
 *
 * - An item's category is the user's pick, else the dictionary's (from note history).
 * - Amounts spread the receipt discounts and additions over the items by largest
 *   remainder, so the rows add up to exactly what the items net to.
 * - One row saves the total (or a tapped chip, or the items' sum when no total was read)
 *   under the category holding the largest share of the items, unless one is picked.
 * - The account is the pick, else the last receipt's, else the first active one.
 */
fun assembleReceipt(
    reading: ReceiptReading,
    choices: ReceiptChoices,
    dictionary: Map<String, Long>,
    categories: List<Category>,
    accounts: List<Account>,
    allTags: List<Tag>,
    defaults: ReceiptDefaults,
    photos: Int,
    readingPhoto: Boolean,
    unreadable: Boolean = false,
    learned: LearnedWords = LearnedWords.NONE,
): ReceiptUiState {
    val active = categories.filterNot { it.archived }
    val activeIds = active.map { it.id }.toSet()
    val nameOf = { id: Long? -> active.firstOrNull { it.id == id }?.name }

    val spread = spreadOverItems(
        nets = reading.items.map { it.net },
        discount = reading.receiptDiscounts.sum(),
        additions = reading.additions.sum(),
    )
    val items = reading.items.mapIndexed { i, item ->
        val key = itemKey(item.name)
        val draft = readableItemName(item.name, learned)
        val name = choices.nameByItem[key]?.trim()?.takeIf { it.isNotEmpty() } ?: draft
        val picked = choices.categoryByItem[key]?.takeIf { it in activeIds }
        // By the readable name first; the printed one still finds rows saved before
        // readable names, or before a word was learned.
        val known = (dictionary[itemKey(name)] ?: dictionary[key])?.takeIf { it in activeIds }
        val categoryId = picked ?: known
        ReceiptItemUi(
            key = key,
            name = name,
            printed = item.name,
            draft = draft,
            quantity = item.quantity,
            price = item.price,
            discount = item.discount,
            amount = spread[i],
            categoryId = categoryId,
            categoryName = nameOf(categoryId),
            fromHistory = picked == null && known != null,
        )
    }

    val total = choices.total ?: reading.total
    val chips = (listOfNotNull(reading.total) + reading.otherAmounts).distinct().filterNot { it == total }
    val check = if (total != null && items.isNotEmpty()) ReceiptCheck(reading.itemsNet, total) else null
    val recordAs = if (items.isEmpty()) RecordAs.ONE_ROW else choices.recordAs

    val oneRowAmount = total ?: reading.itemsNet.takeIf { items.isNotEmpty() }
    val oneRowCategory = choices.oneRowCategoryId?.takeIf { it in activeIds } ?: largestShare(items)

    val usable = accounts.filterNot { it.archived }
    val account = usable.firstOrNull { it.id == choices.accountId }
        ?: usable.firstOrNull { it.id == defaults.accountId }
        ?: usable.firstOrNull()
    val tags = choices.tags ?: entryTagsOf(defaults.tagIds, allTags.filterNot { it.archived })

    return ReceiptUiState(
        photos = photos,
        reading = readingPhoto,
        unreadable = unreadable,
        store = reading.store,
        source = reading.source?.label,
        date = reading.date,
        total = total,
        chips = chips.take(MAX_CHIPS),
        items = items,
        check = check,
        gapsAfterPhoto = reading.gapsAfterPhoto,
        recordAs = recordAs,
        gapRow = choices.gapRow,
        gapCategoryId = choices.gapCategoryId?.takeIf { it in activeIds },
        oneRowAmount = oneRowAmount,
        oneRowCategoryId = oneRowCategory,
        // An order screen that names no restaurant still says where it came from: `GoFood`.
        oneRowNote = choices.oneRowNote ?: reading.store ?: reading.source?.label.orEmpty(),
        unplaced = reading.unplaced,
        account = account,
        tags = tags,
        categories = active,
        accounts = usable,
    )
}

/** The category whose items cost the most in total, ties to the one listed first. */
private fun largestShare(items: List<ReceiptItemUi>): Long? =
    items.filter { it.categoryId != null }
        .groupBy { requireNotNull(it.categoryId) }
        .maxByOrNull { (_, rows) -> rows.sumOf { it.amount.rupiah } }
        ?.key

/**
 * The day the rows are saved on: the receipt's date when it was read and lies within the
 * last year, else [today]. A date further back is more likely a misread than a receipt
 * kept for a year.
 */
fun receiptDay(date: LocalDate?, today: LocalDate): LocalDate =
    date?.takeIf { it <= today && it > today.minus(1, DateTimeUnit.YEAR) } ?: today

/**
 * The rows Save writes, all on [receiptDay] at the scan's time of day, on the chosen
 * account. Empty when there is nothing to save. Rows with no category are written as they
 * are; the screen asks for the missing categories before it gets here.
 */
fun receiptRows(state: ReceiptUiState, scannedAt: LocalDateTime, zone: TimeZone): List<NewTransaction> {
    val account = state.account ?: return emptyList()
    val day = receiptDay(state.date, scannedAt.date)
    val at = LocalDateTime(day, scannedAt.time).toInstant(zone)
    fun row(amount: Money, categoryId: Long?, note: String?) =
        NewTransaction(at, TxnType.EXPENSE, amount, account.id, categoryId, note?.takeIf { it.isNotBlank() })

    return when (state.recordAs) {
        RecordAs.ONE_ROW -> listOfNotNull(
            state.oneRowAmount?.takeIf { it.isPositive }?.let { row(it, state.oneRowCategoryId, state.oneRowNote) },
        )
        RecordAs.PER_ITEM -> {
            val gap = state.gap?.takeIf { state.gapRowOffered && state.gapRow }
            state.items.filter { it.amount.isPositive }.map { row(it.amount, it.categoryId, it.note) } +
                listOfNotNull(gap?.let { row(it, state.gapCategoryId, gapNote(state.store)) })
        }
    }
}

/** What the user's name edits teach, for [TransactionRepository.saveAll] to store with the rows. */
fun learnedFrom(state: ReceiptUiState): LearnedWords =
    state.items.filter { it.renamed }
        .fold(LearnedWords.NONE) { learned, item -> learned + learnedWords(item.printed, item.draft, item.name) }

/** The gap row's note: `Indomaret other items`, or `Receipt other items` with no store. */
fun gapNote(store: String?): String = "${store ?: "Receipt"} other items"

/**
 * [choices] with [categoryId] set on everything still unset: every item with no category,
 * the gap row when it is on, and the one row. The "Use for all unset" answer.
 */
fun ReceiptChoices.fillUnset(state: ReceiptUiState, categoryId: Long): ReceiptChoices = copy(
    categoryByItem = categoryByItem + state.items.filter { it.categoryId == null }.associate { it.key to categoryId },
    gapCategoryId = gapCategoryId ?: categoryId.takeIf { state.gapRow },
    oneRowCategoryId = state.oneRowCategoryId ?: categoryId,
)

/** Appends a receipt line to the one row's note, after a comma when it already says something. */
fun appendToNote(note: String, line: String): String =
    if (note.isBlank()) line else "${note.trimEnd()}, $line"

private const val MAX_CHIPS = 3
