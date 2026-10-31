package com.dvnnfrr.saku.data.repository

import com.dvnnfrr.saku.data.local.ItemWordEntity
import com.dvnnfrr.saku.receipt.LearnedWords
import kotlinx.datetime.Instant

/** The `item_word` rows as the learned map: words by printed token, whole names by item key. */
fun List<ItemWordEntity>.toLearnedWords(): LearnedWords = LearnedWords(
    words = filterNot { it.isWholeName }.associate { it.printed to it.readable },
    names = filter { it.isWholeName }.associate { it.printed to it.readable },
)

fun LearnedWords.toEntities(at: Instant): List<ItemWordEntity> =
    words.map { (printed, readable) -> ItemWordEntity(printed, readable, isWholeName = false, at.epochSeconds) } +
        names.map { (printed, readable) -> ItemWordEntity(printed, readable, isWholeName = true, at.epochSeconds) }
