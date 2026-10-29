package com.dvnnfrr.saku.ui.navigation

/**
 * The screens pushed from the Trans. tab. Pushed rather than swapped in, so Trans stays
 * on the back stack with its sub-tab, month and scroll position, and back returns to it
 * exactly as it was left. The Trans. tab stays lit while one is open; see
 * [SakuDestination.owning].
 */
enum class TransRoute(val route: String) {
    SEARCH("transactions/search"),
    ;

    companion object {
        fun fromRoute(route: String?): TransRoute? = entries.firstOrNull { it.route == route }
    }
}
