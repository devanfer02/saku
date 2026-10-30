package com.dvnnfrr.saku.ui.navigation

/**
 * The screens pushed from the Trans. tab. Pushed rather than swapped in, so Trans stays
 * on the back stack with its sub-tab, month and scroll position, and back returns to it
 * exactly as it was left. The Trans. tab stays lit while one is open; see
 * [SakuDestination.owning].
 */
enum class TransRoute(val route: String) {
    /** Search, optionally opened on a query: Price history passes an item's name. */
    SEARCH("transactions/search?$SEARCH_QUERY={$SEARCH_QUERY}"),
    ;

    companion object {
        /** The search route with [query] typed in; blank opens it empty, as the toolbar does. */
        fun search(query: String = ""): String =
            if (query.isBlank()) "transactions/search" else "transactions/search?$SEARCH_QUERY=${android.net.Uri.encode(query)}"

        fun fromRoute(route: String?): TransRoute? = entries.firstOrNull { it.route == route }
    }
}

/** The search route's query argument. */
const val SEARCH_QUERY = "q"
