package com.dvnnfrr.saku.data

import android.content.Context
import android.content.SharedPreferences

/**
 * What the last receipt was saved with: its account and its tags, offered again on the
 * next scan (a monthly grocery trip is tagged Monthly grocery every month).
 *
 * The same shape as [ChartStyleStore] and [FirstRunStore]: two small values are not worth
 * a Room table, and nothing asks for a second implementation.
 */
class ReceiptDefaultsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastAccountId(): Long? = prefs.getLong(KEY_ACCOUNT, NONE).takeIf { it != NONE }

    /** Empty on the first scan, and after a save with no tag. */
    fun lastTagIds(): Set<Long> =
        prefs.getString(KEY_TAGS, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()

    fun remember(accountId: Long, tagIds: Set<Long>) {
        prefs.edit()
            .putLong(KEY_ACCOUNT, accountId)
            .putString(KEY_TAGS, tagIds.joinToString(","))
            .apply()
    }

    private companion object {
        const val FILE = "saku_receipt"
        const val KEY_ACCOUNT = "account"
        const val KEY_TAGS = "tags"
        const val NONE = -1L
    }
}
