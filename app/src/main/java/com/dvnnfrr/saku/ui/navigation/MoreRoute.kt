package com.dvnnfrr.saku.ui.navigation

import com.dvnnfrr.saku.ui.shell.MoreEntry

/**
 * The screens reached from the More menu.
 *
 * These are pushed onto the back stack rather than being tabs: there are seven of
 * them and a bottom bar holds four, and none is somewhere the user lives. Because
 * they are pushed, the More tab has to stay lit while one is open — see
 * [SakuDestination.owning].
 *
 * [MoreEntry.APPEARANCE] and [MoreEntry.REMINDER] have no route. Each is a dialog over the menu, not a
 * destination, so opening it must not cost a back press to undo.
 */
enum class MoreRoute(val route: String, val entry: MoreEntry) {
    CATEGORIES("more/categories", MoreEntry.CATEGORIES),
    IMPORT_MONEY_MANAGER("more/import-money-manager", MoreEntry.IMPORT_MONEY_MANAGER),
    IMPORT_EXCEL("more/import-excel", MoreEntry.IMPORT_EXCEL),
    EXPORT_EXCEL("more/export-excel", MoreEntry.EXPORT_EXCEL),
    IMPORT_HISTORY("more/import-history", MoreEntry.IMPORT_HISTORY),
    BACKUP("more/backup", MoreEntry.BACKUP),
    RESTORE("more/restore", MoreEntry.RESTORE),
    IMPORT_DATABASE("more/import-database", MoreEntry.IMPORT_DATABASE),
    ;

    companion object {
        fun of(entry: MoreEntry): MoreRoute? = entries.firstOrNull { it.entry == entry }

        fun fromRoute(route: String?): MoreRoute? = entries.firstOrNull { it.route == route }
    }
}
