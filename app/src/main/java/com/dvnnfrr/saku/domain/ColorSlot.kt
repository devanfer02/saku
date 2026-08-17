package com.dvnnfrr.saku.domain

/**
 * Index into the eight-slot categorical chart palette.
 *
 * Colour is identity, not rank: a slot is assigned when a category or account is
 * created and pinned to it thereafter, so re-sorting a chart never repaints it.
 * The light and dark palettes share this slot order, so an entity keeps its hue
 * across themes.
 */
@JvmInline
value class ColorSlot(val index: Int) {
    init {
        require(index in 0 until COUNT) { "Colour slot must be 0..${COUNT - 1}, was $index" }
    }

    companion object {
        const val COUNT = 8

        val FIRST = ColorSlot(0)

        /**
         * The slot to give the next created entity.
         *
         * Assigns the least-used slot, breaking ties by lowest index. With fewer than
         * eight entities this is simply the next unused slot; beyond eight it wraps and
         * keeps the distribution even. Counting usage rather than tracking a running
         * counter means archiving or deleting an entity frees its slot again.
         */
        fun next(existing: Iterable<ColorSlot>): ColorSlot {
            val usage = IntArray(COUNT)
            existing.forEach { usage[it.index]++ }
            var best = 0
            for (i in 1 until COUNT) {
                if (usage[i] < usage[best]) best = i
            }
            return ColorSlot(best)
        }
    }
}
