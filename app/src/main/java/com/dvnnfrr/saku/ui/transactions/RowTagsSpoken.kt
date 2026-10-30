package com.dvnnfrr.saku.ui.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.shared.ROW_TAG_LIMIT

/**
 * What TalkBack reads for a row's tags: "Tags Monthly grocery, Ramadan and 1 more" rather
 * than "hash Monthly grocery". Here and not in the shared row, which takes copy as a parameter.
 */
@Composable
fun TransactionRowUi.spokenTags(): String? {
    val (named, more) = spokenTagParts(ROW_TAG_LIMIT) ?: return null
    return if (more > 0) {
        pluralStringResource(R.plurals.row_tags_spoken_more, more, named, more)
    } else {
        stringResource(R.string.row_tags_spoken, named)
    }
}
