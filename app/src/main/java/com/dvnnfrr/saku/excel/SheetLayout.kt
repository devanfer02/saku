package com.dvnnfrr.saku.excel

/**
 * The two sheet shapes the importer understands, and the column each field sits in.
 *
 * Detection is by header row, never by which menu item the user tapped. "Import from
 * Money Manager" and "Import Excel" are one parser behind two labels: the labels set
 * expectations, and picking the wrong one costs nothing because the file itself says
 * what it is.
 */
enum class SheetLayout(val headers: List<String>) {

    /**
     * The reference export. Eleven columns, two of them named `Account`.
     *
     * Column 11 is not a second account — it repeats the IDR figure. Every one of the
     * 1,200 reference rows has it equal to column 6, which is why it can be dropped
     * rather than reconciled.
     */
    MONEY_MANAGER(
        listOf(
            "Date",
            "Account",
            "Category",
            "Subcategory",
            "Note",
            "IDR",
            "Income/Expense",
            "Description",
            "Amount",
            "Currency",
            "Account",
        ),
    ),

    /** What Saku writes, and therefore what Saku must be able to read back exactly. */
    SAKU(
        listOf(
            "Date",
            "Type",
            "Amount IDR",
            "Account",
            "To Account",
            "Category",
            "Subcategory",
            "Note",
        ),
    ),
    ;

    object MoneyManager {
        const val DATE = 0
        const val ACCOUNT = 1
        const val CATEGORY = 2
        const val SUBCATEGORY = 3
        const val NOTE = 4
        const val IDR = 5
        const val TYPE = 6

        /** Empty on all 1,200 reference rows and ignored even when it is not. */
        const val DESCRIPTION = 7

        /** Text holding the same figure as [IDR]. Cross-checked, never used as the value. */
        const val AMOUNT = 8
        const val CURRENCY = 9

        /** The duplicated header. Holds the amount again; discarded. */
        const val TRAILING_ACCOUNT = 10

        /**
         * Exports from October 2026 on insert `Tag` here, after `Income/Expense`. Saku has
         * no tags, so the column is cut before detection and every index above still holds.
         */
        const val INSERTED_TAG = 7
    }

    object Saku {
        const val DATE = 0
        const val TYPE = 1
        const val AMOUNT = 2
        const val ACCOUNT = 3
        const val TO_ACCOUNT = 4
        const val CATEGORY = 5
        const val SUBCATEGORY = 6
        const val NOTE = 7
    }
}

/** What [LayoutDetector] concluded from a header row. */
sealed interface LayoutMatch {
    data class Recognised(val layout: SheetLayout) : LayoutMatch

    /**
     * Neither shape matched. Carries the headers of both candidates and the ones the
     * file actually had, as data — the sentence built from them belongs in the UI.
     */
    data class Unrecognised(
        val found: List<String>,
        val expected: Map<SheetLayout, List<String>> =
            SheetLayout.entries.associateWith { it.headers },
    ) : LayoutMatch
}

object LayoutDetector {

    fun detect(headers: List<String>): LayoutMatch {
        val found = headers.trimTrailingBlanks()
        val layout = SheetLayout.entries.firstOrNull { it.headers.normalised() == found.normalised() }
        return if (layout != null) LayoutMatch.Recognised(layout) else LayoutMatch.Unrecognised(found)
    }

    /**
     * A sheet often carries empty cells past its last real column — a stray style, a
     * cleared cell — and fastexcel reports them as blank headers. Dropping them keeps
     * a cosmetically dirty file readable while a genuinely different column count
     * still fails.
     */
    fun withoutMoneyManagerTag(table: SheetTable): SheetTable {
        val tag = SheetLayout.MoneyManager.INSERTED_TAG
        val tagged = SheetLayout.MONEY_MANAGER.headers.toMutableList().apply { add(tag, "Tag") }
        val isTaggedExport = table.headers.trimTrailingBlanks().normalised() == tagged.normalised()
        if (!isTaggedExport) return table

        fun <T> List<T>.dropTag() = filterIndexed { index, _ -> index != tag }
        return table.copy(
            headers = table.headers.dropTag(),
            rows = table.rows.map { it.copy(cells = it.cells.dropTag()) },
        )
    }

    private fun List<String>.trimTrailingBlanks(): List<String> = dropLastWhile { it.isBlank() }

    /** Case and surrounding whitespace are not part of the layout's identity. */
    private fun List<String>.normalised(): List<String> =
        map { it.trim().replace(WHITESPACE, " ").lowercase() }

    private val WHITESPACE = Regex("\\s+")
}
