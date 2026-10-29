package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.data.repository.BackupSnapshot
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal

/** An account as the `Accounts` sheet holds it: everything the transaction sheet cannot. */
data class BackupAccount(
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    val openingAsOf: Instant,
    val colorSlot: ColorSlot,
    val sortOrder: Int,
    val archived: Boolean,
    val lastReconciled: Instant?,
)

data class BackupCategory(
    val name: String,
    val kind: CategoryKind,
    val colorSlot: ColorSlot,
    val sortOrder: Int,
    val archived: Boolean,
) {
    val ref: CategoryRef get() = CategoryRef(kind, nameKey(name))
}

/** A category name is unique only within its kind, so the parent is named with its kind. */
data class BackupSubcategory(
    val categoryName: String,
    val categoryKind: CategoryKind,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
) {
    val ref: SubcategoryRef
        get() = SubcategoryRef(CategoryRef(categoryKind, nameKey(categoryName)), nameKey(name))
}

/**
 * The `Backup` sheet. [zone] is the zone the timestamps were written in, so a backup made
 * in Jakarta and restored on a phone set to another zone still lands on the same instants.
 */
data class BackupInfo(
    val formatVersion: Int,
    val appVersion: String,
    val writtenAt: Instant,
    val zone: TimeZone,
)

data class BackupContents(
    val info: BackupInfo,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val subcategories: List<BackupSubcategory>,
    val transactions: ParsedSheet,
) {
    /**
     * The category a transaction row points at. The transaction sheet names a category but
     * not its kind, so the row's type decides: income rows use an income category, expense
     * rows an expense one. A transfer or adjustment may carry either, and an expense
     * category of that name wins because that is the kind import gives a transfer.
     */
    fun categoryRefOf(row: ParsedRow): CategoryRef? {
        val name = row.categoryName ?: return null
        val key = nameKey(name)
        val kind = when (row.type) {
            TxnType.INCOME -> CategoryKind.INCOME
            TxnType.EXPENSE -> CategoryKind.EXPENSE
            TxnType.TRANSFER, TxnType.ADJUSTMENT ->
                if (categories.any { it.ref == CategoryRef(CategoryKind.EXPENSE, key) }) {
                    CategoryKind.EXPENSE
                } else {
                    CategoryKind.INCOME
                }
        }
        return CategoryRef(kind, key)
    }

    fun subcategoryRefOf(row: ParsedRow): SubcategoryRef? {
        val category = categoryRefOf(row) ?: return null
        val name = row.subcategoryName ?: return null
        return SubcategoryRef(category, nameKey(name))
    }
}

/** A category's identity across databases: its kind and its normalised name. */
data class CategoryRef(val kind: CategoryKind, val key: String)

data class SubcategoryRef(val category: CategoryRef, val key: String)

/**
 * The comparison key for every name a backup matches on: trimmed, inner whitespace
 * collapsed, case-folded. "Makan  &  Minum " and "makan & minum" are one category.
 */
fun nameKey(name: String): String = name.trim().replace(WHITESPACE, " ").lowercase()

private val WHITESPACE = Regex("\\s+")

/** Why a file was not restored. Data, not prose; the screen words it. */
sealed interface BackupRefusal {
    /** Not a Saku backup, or one with a sheet removed. */
    data class MissingSheet(val sheetName: String) : BackupRefusal

    /** Written by a newer Saku. [appVersion] is the version that wrote it, when readable. */
    data class NewerFormat(val formatVersion: Int, val appVersion: String?) : BackupRefusal

    data class MissingColumn(val sheetName: String, val column: String) : BackupRefusal

    /** [row] is the workbook's own row number, so the user can find it. */
    data class InvalidCell(val sheetName: String, val row: Int, val column: String) : BackupRefusal

    data class DuplicateName(val sheetName: String, val name: String) : BackupRefusal

    /** A row names an account, category or subcategory the record sheets do not list. */
    data class UnknownName(val sheetName: String, val name: String) : BackupRefusal

    /** The first sheet is not in Saku's transaction layout. */
    data object UnrecognisedTransactions : BackupRefusal
}

sealed interface BackupReadOutcome {
    data class Read(val contents: BackupContents) : BackupReadOutcome

    data class Refused(val refusal: BackupRefusal) : BackupReadOutcome
}

/**
 * Builds and parses the backup workbook.
 *
 * Sheet one is [ExportSheet]'s transaction sheet, unchanged, so the plain importer reads a
 * backup exactly as it reads an export. The record sheets after it carry what that sheet
 * cannot: opening balances, types, colours, sort orders, archived flags, reconcile dates
 * and the subcategory tree. Everything is keyed by name, never by id, for the same reason
 * the export is.
 *
 * Record sheets are read by header name, not position, and unknown columns are ignored.
 * Adding a column later is therefore not a format change; changing what a column means is,
 * and that is what [FORMAT_VERSION] is for.
 */
object BackupSheets {

    const val FORMAT_VERSION = 1

    const val ACCOUNTS = "Accounts"
    const val CATEGORIES = "Categories"
    const val SUBCATEGORIES = "Subcategories"
    const val BACKUP = "Backup"

    private object AccountColumns {
        const val NAME = "Name"
        const val TYPE = "Type"
        const val OPENING_BALANCE = "Opening balance IDR"
        const val OPENING_AS_OF = "Opening as of"
        const val COLOUR = "Colour slot"
        const val SORT_ORDER = "Sort order"
        const val ARCHIVED = "Archived"
        const val LAST_RECONCILED = "Last reconciled"

        val ALL = listOf(NAME, TYPE, OPENING_BALANCE, OPENING_AS_OF, COLOUR, SORT_ORDER, ARCHIVED, LAST_RECONCILED)
    }

    private object CategoryColumns {
        const val NAME = "Name"
        const val KIND = "Kind"
        const val COLOUR = "Colour slot"
        const val SORT_ORDER = "Sort order"
        const val ARCHIVED = "Archived"

        val ALL = listOf(NAME, KIND, COLOUR, SORT_ORDER, ARCHIVED)
    }

    private object SubcategoryColumns {
        const val CATEGORY = "Category"
        const val CATEGORY_KIND = "Category kind"
        const val NAME = "Name"
        const val SORT_ORDER = "Sort order"
        const val ARCHIVED = "Archived"

        val ALL = listOf(CATEGORY, CATEGORY_KIND, NAME, SORT_ORDER, ARCHIVED)
    }

    private object InfoFields {
        const val FIELD = "Field"
        const val VALUE = "Value"
        const val FORMAT = "Format version"
        const val APP_VERSION = "App version"
        const val WRITTEN_AT = "Written at"
        const val ZONE = "Time zone"
    }

    // -- Writing ------------------------------------------------------------------

    /** The five sheets in file order: transactions first, the `Backup` sheet last. */
    fun build(
        snapshot: BackupSnapshot,
        appVersion: String,
        writtenAt: Instant,
        zone: TimeZone,
    ): List<SheetTable> {
        val accountNames = snapshot.accounts.associate { it.id to it.name }
        val categoriesById = snapshot.categories.associateBy { it.id }

        val transactions = ExportSheet.build(
            transactions = snapshot.transactions,
            accountNames = accountNames,
            categoryNames = snapshot.categories.associate { it.id to it.name },
            subcategoryNames = snapshot.subcategories.associate { it.id to it.name },
            zone = zone,
        )

        val accounts = sheet(ACCOUNTS, AccountColumns.ALL, snapshot.accounts.map { account ->
            listOf(
                CellValue.Text(account.name),
                CellValue.Text(account.type.token),
                CellValue.Number(BigDecimal.valueOf(account.openingBalance.rupiah)),
                timestamp(account.openingAsOf, zone),
                CellValue.Number(BigDecimal.valueOf(account.colorSlot.index.toLong())),
                CellValue.Number(BigDecimal.valueOf(account.sortOrder.toLong())),
                CellValue.Flag(account.archived),
                account.lastReconciled?.let { timestamp(it, zone) } ?: CellValue.Empty,
            )
        })

        val categories = sheet(CATEGORIES, CategoryColumns.ALL, snapshot.categories.map { category ->
            listOf(
                CellValue.Text(category.name),
                CellValue.Text(category.kind.token),
                CellValue.Number(BigDecimal.valueOf(category.colorSlot.index.toLong())),
                CellValue.Number(BigDecimal.valueOf(category.sortOrder.toLong())),
                CellValue.Flag(category.archived),
            )
        })

        val subcategories = sheet(
            SUBCATEGORIES,
            SubcategoryColumns.ALL,
            snapshot.subcategories.map { subcategory ->
                val parent = categoriesById.getValue(subcategory.categoryId)
                listOf(
                    CellValue.Text(parent.name),
                    CellValue.Text(parent.kind.token),
                    CellValue.Text(subcategory.name),
                    CellValue.Number(BigDecimal.valueOf(subcategory.sortOrder.toLong())),
                    CellValue.Flag(subcategory.archived),
                )
            },
        )

        val info = sheet(
            BACKUP,
            listOf(InfoFields.FIELD, InfoFields.VALUE),
            listOf(
                listOf(
                    CellValue.Text(InfoFields.FORMAT),
                    CellValue.Number(BigDecimal.valueOf(FORMAT_VERSION.toLong())),
                ),
                listOf(CellValue.Text(InfoFields.APP_VERSION), CellValue.Text(appVersion)),
                listOf(CellValue.Text(InfoFields.WRITTEN_AT), timestamp(writtenAt, zone)),
                listOf(CellValue.Text(InfoFields.ZONE), CellValue.Text(zone.id)),
            ),
        )

        return listOf(transactions, accounts, categories, subcategories, info)
    }

    private fun sheet(name: String, headers: List<String>, rows: List<List<CellValue>>) =
        SheetTable(
            sheetName = name,
            headers = headers,
            rows = rows.mapIndexed { index, cells -> SheetRow(number = index + 1, cells = cells) },
        )

    private fun timestamp(at: Instant, zone: TimeZone): CellValue =
        CellValue.Timestamp(at.toLocalDateTime(zone).toJavaLocalDateTime())

    // -- Reading ------------------------------------------------------------------

    /**
     * The `Backup` sheet is read first and a newer format is refused before any other sheet
     * is interpreted: a version 1 reader cannot know what a version 2 column means, and
     * guessing would restore the wrong thing quietly.
     */
    fun parse(sheets: List<SheetTable>): BackupReadOutcome = try {
        BackupReadOutcome.Read(read(sheets))
    } catch (refused: Refused) {
        BackupReadOutcome.Refused(refused.refusal)
    }

    /** Carries a refusal out of the nested readers; never escapes [parse]. */
    private class Refused(val refusal: BackupRefusal) : RuntimeException(null, null, false, false)

    private fun refuse(refusal: BackupRefusal): Nothing = throw Refused(refusal)

    private fun read(sheets: List<SheetTable>): BackupContents {
        fun named(name: String): SheetTable =
            sheets.firstOrNull { nameKey(it.sheetName) == nameKey(name) }
                ?: refuse(BackupRefusal.MissingSheet(name))

        val info = readInfo(named(BACKUP))
        val transactionSheet = sheets.firstOrNull() ?: refuse(BackupRefusal.MissingSheet(ExportSheet.SHEET_NAME))
        val accountTable = named(ACCOUNTS)
        val categoryTable = named(CATEGORIES)
        val subcategoryTable = named(SUBCATEGORIES)

        val accounts = readAccounts(accountTable, info.zone)
        val categories = readCategories(categoryTable)
        val subcategories = readSubcategories(subcategoryTable)

        val parsed = ImportParser.parse(transactionSheet, info.zone)
        val transactions = (parsed as? ParseOutcome.Parsed)?.sheet
            ?.takeIf { it.layout == SheetLayout.SAKU }
            ?: refuse(BackupRefusal.UnrecognisedTransactions)

        val contents = BackupContents(info, accounts, categories, subcategories, transactions)
        checkReferences(contents)
        return contents
    }

    private fun readInfo(table: SheetTable): BackupInfo {
        val columns = Columns(table)
        val field = columns.require(InfoFields.FIELD)
        val value = columns.require(InfoFields.VALUE)
        val rows = table.rows.associateBy { row -> row.textAt(field)?.let(::nameKey) }

        fun rowFor(name: String): SheetRow =
            rows[nameKey(name)] ?: refuse(BackupRefusal.MissingColumn(BACKUP, name))

        val appVersion = rows[nameKey(InfoFields.APP_VERSION)]?.let { text(it, value) }

        val formatRow = rowFor(InfoFields.FORMAT)
        val format = wholeNumber(formatRow, value)?.takeIf { it >= 1 }
            ?: refuse(BackupRefusal.InvalidCell(BACKUP, formatRow.number, InfoFields.FORMAT))
        if (format > FORMAT_VERSION) refuse(BackupRefusal.NewerFormat(format.toInt(), appVersion))

        val zoneRow = rowFor(InfoFields.ZONE)
        val zone = text(zoneRow, value)?.let { runCatching { TimeZone.of(it) }.getOrNull() }
            ?: refuse(BackupRefusal.InvalidCell(BACKUP, zoneRow.number, InfoFields.ZONE))

        val writtenRow = rowFor(InfoFields.WRITTEN_AT)
        val writtenAt = writtenRow.timestampAt(value)?.toInstantAtSecond(zone)
            ?: refuse(BackupRefusal.InvalidCell(BACKUP, writtenRow.number, InfoFields.WRITTEN_AT))

        return BackupInfo(
            formatVersion = format.toInt(),
            appVersion = appVersion.orEmpty(),
            writtenAt = writtenAt,
            zone = zone,
        )
    }

    private fun readAccounts(table: SheetTable, zone: TimeZone): List<BackupAccount> {
        val c = Columns(table)
        val name = c.require(AccountColumns.NAME)
        val type = c.require(AccountColumns.TYPE)
        val opening = c.require(AccountColumns.OPENING_BALANCE)
        val openingAsOf = c.require(AccountColumns.OPENING_AS_OF)
        val colour = c.require(AccountColumns.COLOUR)
        val sortOrder = c.require(AccountColumns.SORT_ORDER)
        val archived = c.require(AccountColumns.ARCHIVED)
        val reconciled = c.require(AccountColumns.LAST_RECONCILED)

        val accounts = table.dataRows().map { row ->
            val cell = CellReader(ACCOUNTS, row)
            BackupAccount(
                name = cell.name(name, AccountColumns.NAME),
                type = cell.parse(type, AccountColumns.TYPE) { token -> AccountType.entries.firstOrNull { it.token.equals(token, true) } },
                openingBalance = Money(cell.long(opening, AccountColumns.OPENING_BALANCE)),
                openingAsOf = cell.instant(openingAsOf, AccountColumns.OPENING_AS_OF, zone),
                colorSlot = cell.colour(colour, AccountColumns.COLOUR),
                sortOrder = cell.int(sortOrder, AccountColumns.SORT_ORDER),
                archived = cell.flag(archived, AccountColumns.ARCHIVED),
                lastReconciled = if (row[reconciled] == CellValue.Empty) {
                    null
                } else {
                    cell.instant(reconciled, AccountColumns.LAST_RECONCILED, zone)
                },
            )
        }
        refuseDuplicates(ACCOUNTS, accounts) { it.name to nameKey(it.name) }
        return accounts
    }

    private fun readCategories(table: SheetTable): List<BackupCategory> {
        val c = Columns(table)
        val name = c.require(CategoryColumns.NAME)
        val kind = c.require(CategoryColumns.KIND)
        val colour = c.require(CategoryColumns.COLOUR)
        val sortOrder = c.require(CategoryColumns.SORT_ORDER)
        val archived = c.require(CategoryColumns.ARCHIVED)

        val categories = table.dataRows().map { row ->
            val cell = CellReader(CATEGORIES, row)
            BackupCategory(
                name = cell.name(name, CategoryColumns.NAME),
                kind = cell.kind(kind, CategoryColumns.KIND),
                colorSlot = cell.colour(colour, CategoryColumns.COLOUR),
                sortOrder = cell.int(sortOrder, CategoryColumns.SORT_ORDER),
                archived = cell.flag(archived, CategoryColumns.ARCHIVED),
            )
        }
        refuseDuplicates(CATEGORIES, categories) { it.name to it.ref }
        return categories
    }

    private fun readSubcategories(table: SheetTable): List<BackupSubcategory> {
        val c = Columns(table)
        val category = c.require(SubcategoryColumns.CATEGORY)
        val kind = c.require(SubcategoryColumns.CATEGORY_KIND)
        val name = c.require(SubcategoryColumns.NAME)
        val sortOrder = c.require(SubcategoryColumns.SORT_ORDER)
        val archived = c.require(SubcategoryColumns.ARCHIVED)

        val subcategories = table.dataRows().map { row ->
            val cell = CellReader(SUBCATEGORIES, row)
            BackupSubcategory(
                categoryName = cell.name(category, SubcategoryColumns.CATEGORY),
                categoryKind = cell.kind(kind, SubcategoryColumns.CATEGORY_KIND),
                name = cell.name(name, SubcategoryColumns.NAME),
                sortOrder = cell.int(sortOrder, SubcategoryColumns.SORT_ORDER),
                archived = cell.flag(archived, SubcategoryColumns.ARCHIVED),
            )
        }
        refuseDuplicates(SUBCATEGORIES, subcategories) { it.name to it.ref }
        return subcategories
    }

    /**
     * Two records with one name would leave restore unable to say which a transaction
     * meant, so the file is refused and the duplicate named rather than one picked.
     */
    private fun <T> refuseDuplicates(sheet: String, records: List<T>, identity: (T) -> Pair<String, Any>) {
        val seen = mutableSetOf<Any>()
        records.forEach { record ->
            val (name, key) = identity(record)
            if (!seen.add(key)) refuse(BackupRefusal.DuplicateName(sheet, name))
        }
    }

    /** Every name a row uses must be a record the backup carries, or restore cannot rebuild it. */
    private fun checkReferences(contents: BackupContents) {
        val accounts = contents.accounts.map { nameKey(it.name) }.toSet()
        val categories = contents.categories.map { it.ref }.toSet()
        val subcategories = contents.subcategories.map { it.ref }.toSet()

        contents.subcategories.forEach { subcategory ->
            if (subcategory.ref.category !in categories) {
                refuse(BackupRefusal.UnknownName(SUBCATEGORIES, subcategory.categoryName))
            }
        }

        val sheet = contents.transactions.sheetName
        contents.transactions.rows.forEach { row ->
            if (nameKey(row.accountName) !in accounts) refuse(BackupRefusal.UnknownName(sheet, row.accountName))
            row.toAccountName?.let {
                if (nameKey(it) !in accounts) refuse(BackupRefusal.UnknownName(sheet, it))
            }
            contents.categoryRefOf(row)?.let {
                if (it !in categories) refuse(BackupRefusal.UnknownName(sheet, row.categoryName!!))
            }
            contents.subcategoryRefOf(row)?.let {
                if (it !in subcategories) refuse(BackupRefusal.UnknownName(sheet, row.subcategoryName!!))
            }
        }
    }

    private fun SheetTable.dataRows(): List<SheetRow> =
        rows.filter { row -> row.cells.any { it != CellValue.Empty } }

    /** Header name to column index, matched the way names are. */
    private class Columns(private val table: SheetTable) {
        private val index = table.headers
            .mapIndexed { position, header -> nameKey(header) to position }
            .reversed()
            .toMap()

        fun require(column: String): Int =
            index[nameKey(column)] ?: refuse(BackupRefusal.MissingColumn(table.sheetName, column))
    }

    private class CellReader(private val sheet: String, private val row: SheetRow) {
        private fun invalid(column: String): Nothing =
            refuse(BackupRefusal.InvalidCell(sheet, row.number, column))

        fun name(index: Int, column: String): String =
            text(row, index)?.trim()?.takeIf { it.isNotEmpty() } ?: invalid(column)

        fun <T> parse(index: Int, column: String, from: (String) -> T?): T =
            text(row, index)?.trim()?.let(from) ?: invalid(column)

        fun kind(index: Int, column: String): CategoryKind =
            parse(index, column) { token -> CategoryKind.entries.firstOrNull { it.token.equals(token, true) } }

        fun long(index: Int, column: String): Long = wholeNumber(row, index) ?: invalid(column)

        fun int(index: Int, column: String): Int =
            wholeNumber(row, index)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: invalid(column)

        fun colour(index: Int, column: String): ColorSlot =
            int(index, column).takeIf { it in 0 until ColorSlot.COUNT }?.let(::ColorSlot) ?: invalid(column)

        fun instant(index: Int, column: String, zone: TimeZone): Instant =
            row.timestampAt(index)?.toInstantAtSecond(zone) ?: invalid(column)

        /** A spreadsheet edited by hand may hold TRUE as text; an empty cell is not archived. */
        fun flag(index: Int, column: String): Boolean = when (val cell = row[index]) {
            is CellValue.Flag -> cell.value
            CellValue.Empty -> false
            is CellValue.Text -> when (cell.value.trim().lowercase()) {
                "true", "yes" -> true
                "false", "no", "" -> false
                else -> invalid(column)
            }

            else -> invalid(column)
        }
    }

    private fun text(row: SheetRow, index: Int): String? = when (val cell = row[index]) {
        is CellValue.Text -> cell.value
        is CellValue.Number -> cell.value.toPlainString()
        else -> null
    }

    /** Whole numbers only. A fractional rupiah or sort order means the file is not ours. */
    private fun wholeNumber(row: SheetRow, index: Int): Long? {
        val number = when (val cell = row[index]) {
            is CellValue.Number -> cell.value
            is CellValue.Text -> cell.value.trim().toBigDecimalOrNull()
            else -> null
        } ?: return null
        return runCatching { number.stripTrailingZeros().longValueExact() }.getOrNull()
    }
}

/** File-format tokens, like [TxnType.token]: stable across releases, never UI copy. */
internal val AccountType.token: String
    get() = when (this) {
        AccountType.CASH -> "Cash"
        AccountType.BANK -> "Bank"
        AccountType.INVESTMENT -> "Investment"
        AccountType.LIABILITY -> "Liability"
    }

internal val CategoryKind.token: String
    get() = when (this) {
        CategoryKind.EXPENSE -> "Expense"
        CategoryKind.INCOME -> "Income"
    }
