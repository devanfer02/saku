package com.dvnnfrr.saku.ui.navigation

import com.dvnnfrr.saku.ui.shell.MoreEntry

/**
 * The screens of first-run setup, shown before the shell and in their own graph, so no
 * bottom bar is drawn and no tab can be reached half way through.
 *
 * The four import routes mount the same screens More pushes; only the route strings are
 * separate, because they live in a different graph.
 */
enum class FirstRunRoute(val route: String) {
    WELCOME("first-run/welcome"),
    FROM_SAKU("first-run/from-saku"),
    MANUAL("first-run/manual"),
    IMPORT_MONEY_MANAGER("first-run/import-money-manager"),
    IMPORT_EXCEL("first-run/import-excel"),
    RESTORE("first-run/restore"),
    IMPORT_DATABASE("first-run/import-database"),
    ;

    /** True for a route whose screen can put rows in the ledger. */
    val isImport: Boolean get() = this in IMPORTS

    companion object {
        val START = WELCOME

        private val IMPORTS = setOf(IMPORT_MONEY_MANAGER, IMPORT_EXCEL, RESTORE, IMPORT_DATABASE)

        fun fromRoute(route: String?): FirstRunRoute? = entries.firstOrNull { it.route == route }

        /** Where a More entry offered by Import from Saku goes in this graph. */
        fun of(entry: MoreEntry): FirstRunRoute? = when (entry) {
            MoreEntry.RESTORE -> RESTORE
            MoreEntry.IMPORT_DATABASE -> IMPORT_DATABASE
            MoreEntry.IMPORT_EXCEL -> IMPORT_EXCEL
            MoreEntry.IMPORT_MONEY_MANAGER -> IMPORT_MONEY_MANAGER
            else -> null
        }
    }
}
