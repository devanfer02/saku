package com.dvnnfrr.saku.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.TxnType

/**
 * The complete colour token set for one theme.
 *
 * Light and dark are written out in full and neither is derived from the other, so a
 * token can never exist in one theme and be missing from the other — the compiler
 * refuses a [SakuColors] that leaves a field unset.
 *
 * [accent] is blue in both themes and carries brand and interactive state only. Money
 * direction rides on [income], [expense] and [transfer] so that a figure's meaning never
 * depends on chrome.
 */
@Immutable
data class SakuColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    /** A divider that must read as a boundary, such as the cells of a picker grid. */
    val lineStrong: Color,
    val accent: Color,
    val accentWash: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    /** Exactly [ColorSlot.COUNT] entries; the index is the [ColorSlot.index]. */
    /**
     * For a segment that owns no slot — the stats screen's aggregated "Other".
     * Deliberately not `surfaceAlt`: that is a share bar's unfilled track, so a bar
     * painted in it would be invisible.
     */
    val chartNeutral: Color,
    /** Attention without destruction. Distinct from `expense`, which means error. */
    val warning: Color,
    /**
     * The two shares of net worth on the Accounts split bar. Apart in hue, blue and
     * red-orange, so they read apart in both themes and for colour-blind eyes; the orange
     * sits 17 degrees of hue off `warning` so a share never reads as an alarm. Each is at
     * least 3:1 against `surface` and `background`.
     */
    val netSpendable: Color,
    val netInvested: Color,
    /** Ink for a control that is present but cannot be used. */
    val inkDisabled: Color,
    /**
     * The welcome screen's illustration, and nothing else: a blue, a yellow and a red, each
     * with a pale wash for the shapes behind. Decorative, so no contrast floor applies, and
     * they never sit beside a figure. The red is held at least 15 degrees of OKLCH hue off
     * `expense` and the yellow off `warning`, both at lower chroma, so neither reads as a
     * loss or an alarm. Named by hue because the hue is the content.
     */
    val artBlue: Color,
    val artBlueWash: Color,
    val artYellow: Color,
    val artYellowWash: Color,
    val artRed: Color,
    val artRedWash: Color,
    val chart: List<Color>,
) {
    init {
        require(chart.size == ColorSlot.COUNT) {
            "Chart palette must hold exactly ${ColorSlot.COUNT} slots, had ${chart.size}"
        }
    }
}

/**
 * Both palettes were run through a colour-vision validator rather than eyeballed: each
 * passes a lightness band, a chroma floor, an adjacent-pair separation threshold and a
 * contrast floor against its own chart surface. Do not substitute values without
 * re-running that check.
 *
 * Slot order is shared between the themes, so an entity pinned to slot 1 keeps its
 * identity when the theme flips.
 */
val LightSakuColors = SakuColors(
    background = Color(0xFFF4F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFE9EDF6),
    ink = Color(0xFF0E1626),
    ink2 = Color(0xFF59637A),
    ink3 = Color(0xFF8B94A8),
    line = Color(0xFFDDE3EF),
    lineStrong = Color(0xFFB9C2D4),
    accent = Color(0xFF1B5AD9),
    accentWash = Color(0xFFE6EDFC),
    income = Color(0xFF0B7A5B),
    expense = Color(0xFFBF3B2E),
    transfer = Color(0xFF59637A),
    chartNeutral = Color(0xFF9AA4B8),
    warning = Color(0xFF9A6207),
    netSpendable = Color(0xFF1B5AD9),
    netInvested = Color(0xFFC9541A),
    inkDisabled = Color(0xFFB3BBCB),
    artBlue = Color(0xFF3F78E0),
    artBlueWash = Color(0xFFD8E3FA),
    artYellow = Color(0xFFEFC650),
    artYellowWash = Color(0xFFFBEDC4),
    artRed = Color(0xFFDE6479),
    artRedWash = Color(0xFFF9D5DB),
    chart = listOf(
        Color(0xFF1B5AD9),
        Color(0xFFC2741C),
        Color(0xFF00846B),
        Color(0xFFD45E96),
        Color(0xFFB08A00),
        Color(0xFF4B3BA8),
        Color(0xFF2F93C7),
        Color(0xFFA6392F),
    ),
)

val DarkSakuColors = SakuColors(
    background = Color(0xFF0A0F1E),
    surface = Color(0xFF131A2C),
    surfaceAlt = Color(0xFF1C2540),
    ink = Color(0xFFE9EDF7),
    ink2 = Color(0xFF98A3BC),
    ink3 = Color(0xFF6A7591),
    line = Color(0xFF232D48),
    lineStrong = Color(0xFF3A4668),
    accent = Color(0xFF6C9BF0),
    accentWash = Color(0xFF17233F),
    income = Color(0xFF3BC492),
    expense = Color(0xFFF0736A),
    transfer = Color(0xFF98A3BC),
    chartNeutral = Color(0xFF6E7A94),
    warning = Color(0xFFD79A3C),
    netSpendable = Color(0xFF6C9BF0),
    netInvested = Color(0xFFF08A52),
    inkDisabled = Color(0xFF4C566E),
    artBlue = Color(0xFF5A86E0),
    artBlueWash = Color(0xFF1A2A50),
    artYellow = Color(0xFFD9B44A),
    artYellowWash = Color(0xFF3A3420),
    artRed = Color(0xFFCC6680),
    artRedWash = Color(0xFF3D1E2A),
    chart = listOf(
        Color(0xFF5688DD),
        Color(0xFFB0651C),
        Color(0xFF2FA882),
        Color(0xFFA8437F),
        Color(0xFF9C8933),
        Color(0xFF9265D2),
        Color(0xFF3E97BE),
        Color(0xFFA85A3C),
    ),
)

/** The categorical colour pinned to [slot] in the active theme. */
fun SakuColors.forSlot(slot: ColorSlot): Color = chart[slot.index]

/** The colour for a segment or chip, where null means it owns no slot. */
fun SakuColors.forSlotOrNeutral(slot: ColorSlot?): Color =
    if (slot == null) chartNeutral else chart[slot.index]

/**
 * The semantic colour for a transaction's direction. Never returns [SakuColors.accent]:
 * the accent means "interactive", and reusing it here would make an amount's direction
 * depend on chrome.
 */
fun SakuColors.forDirection(type: TxnType): Color = when (type) {
    TxnType.INCOME -> income
    TxnType.EXPENSE -> expense
    TxnType.TRANSFER, TxnType.ADJUSTMENT -> transfer
}
