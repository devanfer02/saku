package com.dvnnfrr.saku.domain

import kotlin.math.absoluteValue

/**
 * An amount in whole rupiah.
 *
 * The sen is obsolete and every row in a year of reference data is a whole
 * number, so there is no subunit and no floating point anywhere in the app. Storing
 * a [Long] means aggregates cannot accumulate rounding drift.
 */
@JvmInline
value class Money(val rupiah: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(rupiah + other.rupiah)

    operator fun minus(other: Money): Money = Money(rupiah - other.rupiah)

    operator fun unaryMinus(): Money = Money(-rupiah)

    override fun compareTo(other: Money): Int = rupiah.compareTo(other.rupiah)

    val isZero: Boolean get() = rupiah == 0L

    val isPositive: Boolean get() = rupiah > 0L

    val isNegative: Boolean get() = rupiah < 0L

    val absolute: Money get() = Money(rupiah.absoluteValue)

    override fun toString(): String = "Money($rupiah)"

    companion object {
        val ZERO = Money(0L)
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.rupiah })

fun Long.toMoney(): Money = Money(this)
