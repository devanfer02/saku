package com.dvnnfrr.saku.data.local

import androidx.room.TypeConverter
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.TxnType

/**
 * Enums are stored by name rather than ordinal so reordering a declaration cannot
 * silently reinterpret existing rows.
 */
class Converters {
    @TypeConverter fun txnTypeToString(value: TxnType): String = value.name

    @TypeConverter fun stringToTxnType(value: String): TxnType = TxnType.valueOf(value)

    @TypeConverter fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter fun categoryKindToString(value: CategoryKind): String = value.name

    @TypeConverter fun stringToCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)
}
