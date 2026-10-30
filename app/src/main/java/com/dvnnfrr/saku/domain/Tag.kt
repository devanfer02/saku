package com.dvnnfrr.saku.domain

/**
 * A label cutting across categories: "Monthly grocery" on the rice under Makan & Minum and
 * on the soap under Kebutuhan Kos. A row carries none, one or several. Never deleted; an
 * archived tag stays on its rows and in the filter but is not offered for new tagging.
 */
data class Tag(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
)

/** The one implementation of the tag name rules (design.md, decision 2). */
object TagName {

    const val MAX_LENGTH = 30

    /** Joins several names in one spreadsheet cell, which is why a name may not hold it. */
    const val SEPARATOR = ";"

    /**
     * [raw] trimmed with inner spaces collapsed, or null when that is empty, longer than
     * [MAX_LENGTH] or holds [SEPARATOR]. Case is kept: display shows what was first typed.
     */
    fun normalize(raw: String): String? {
        val name = raw.trim().replace(WHITESPACE, " ")
        val valid = name.isNotEmpty() && name.length <= MAX_LENGTH && SEPARATOR !in name
        return name.takeIf { valid }
    }

    /** The comparison key: two names with one key are one tag. */
    fun key(name: String): String = name.trim().replace(WHITESPACE, " ").lowercase()

    /** The cell text for [names]: "Monthly grocery; Trip Bali". */
    fun join(names: List<String>): String = names.joinToString("$SEPARATOR ")

    /** A cell back into names, dropping blanks, invalid names and repeats by [key]. */
    fun split(cell: String?): List<String> =
        cell.orEmpty()
            .split(SEPARATOR)
            .mapNotNull(::normalize)
            .distinctBy(::key)

    private val WHITESPACE = Regex("\\s+")
}

/** Thrown when a typed or renamed tag name breaks [TagName.normalize]. */
class InvalidTagNameException(val name: String) : IllegalArgumentException("\"$name\" is not a valid tag name")
