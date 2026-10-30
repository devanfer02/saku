package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money

/**
 * Shares [amount] over [weights] in proportion, in whole rupiah: each share is floored,
 * then the rupiah left over go one each to the largest remainders, ties to the earlier
 * weight. The shares always add up to [amount] exactly.
 *
 * The same largest-remainder method as `wholePercents`, but over rupiah rather than
 * percent points; see design.md (receipt-items) section 4. Weights at or below zero get
 * no share; if none is positive, every weight counts as one.
 */
fun spreadShares(amount: Long, weights: List<Long>): List<Long> {
    if (weights.isEmpty()) return emptyList()
    val positive = weights.map { it.coerceAtLeast(0L) }
    val used = if (positive.sum() > 0L) positive else weights.map { 1L }
    val whole = used.sum()
    val sign = if (amount < 0L) -1L else 1L
    val size = amount * sign

    val floors = used.map { Math.multiplyExact(size, it) / whole }
    val remainders = used.map { Math.multiplyExact(size, it) % whole }
    val left = size - floors.sum()
    val bonus = remainders.indices
        .sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
        .take(left.toInt())
        .toSet()
    return floors.mapIndexed { i, share -> sign * (share + if (i in bonus) 1L else 0L) }
}

/**
 * What each item costs once the receipt's discounts and additions are shared out:
 * net price minus its share of [discount], plus its share of [additions] (tax or service
 * added on top). The results add up to `sum(nets) - discount + additions` exactly.
 */
fun spreadOverItems(nets: List<Money>, discount: Money, additions: Money = Money.ZERO): List<Money> {
    val weights = nets.map { it.rupiah }
    val cuts = spreadShares(discount.rupiah, weights)
    val adds = spreadShares(additions.rupiah, weights)
    return nets.indices.map { i -> Money(nets[i].rupiah - cuts[i] + adds[i]) }
}
