package com.dvnnfrr.saku.receipt

import com.dvnnfrr.saku.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptJoinTest {

    private fun join(vararg photos: List<String>) = joinPhotos(photos.map { rowsOf(*it.toTypedArray()) })

    @Test
    fun `rows printed in both photos are kept once`() {
        val joined = join(
            listOf(
                "INDOMIE GRG SPCL 5 3.100 15.500",
                "MINYAK GORENG 2L 1 38.900 38.900",
                "BERAS PREMIUM 5KG 1 72.500 72.500",
                "SUSU UHT 1L 2 18.200 36.400",
            ),
            listOf(
                "BERAS PREMIUM 5KG 1 72.500 72.500",
                "SUSU UHT 1L 2 18.200 36.400",
                "SABUN CUCI 800ML 1 21.900 21.900",
            ),
        )
        assertEquals(5, joined.rows.size)
        assertEquals(emptyList<Int>(), joined.gapsAfterPhoto)
        val items = pickItems(joined.rows, null).items
        assertEquals(1, items.count { it.name == "BERAS PREMIUM 5KG" })
        assertEquals(1, items.count { it.name == "SUSU UHT 1L" })
    }

    @Test
    fun `an overlap read differently in each photo still joins`() {
        val joined = join(
            listOf("GULA PASIR 1KG 1 17.500 17.500", "KECAP MANIS 520ML 1 24.900 24.980", "DISKON : (3.000)"),
            listOf("GULA PAS IR 1KG 1 17.500 17.508", "KECAP MANIS 520ML 1 24.900 24.900", "DISKON (3.000)", "SAOS SAMBAL 1 12.400 12.400"),
        )
        assertEquals(emptyList<Int>(), joined.gapsAfterPhoto)
        assertEquals(4, joined.rows.size)
    }

    @Test
    fun `photos that share no row report a part that may be missing`() {
        val joined = join(
            listOf("INDOMARET", "INDOMIE GRG SPCL 5 3.100 15.500"),
            listOf("MINYAK GORENG 2L 1 38.900 38.900", "SABUN CUCI 800ML 1 21.900 21.900"),
            listOf("HARGA JUAL : 226.100", "DISKON KARTU : (20.000)", "TOTAL : 206.100"),
        )
        assertEquals(listOf(1, 2), joined.gapsAfterPhoto)
        assertEquals(7, joined.rows.size)
    }

    @Test
    fun `a repeated label with no amount does not join two photos`() {
        val joined = join(
            listOf("SUSU UHT 1L 2 18.200 36.400", "DISKON MEMBER"),
            listOf("DISKON MEMBER", "SABUN CUCI 800ML 1 21.900 21.900"),
        )
        assertEquals(listOf(1), joined.gapsAfterPhoto)
        assertEquals(4, joined.rows.size)
    }

    @Test
    fun `the same item twice inside one photo is kept and read as quantity two`() {
        val joined = join(
            listOf("BERAS PREMIUM 5KG 1 72.500 72.500", "SUSU UHT 1L 1 18.200 18.200", "SUSU UHT 1L 1 18.200 18.200"),
            listOf("SUSU UHT 1L 1 18.200 18.200", "SABUN CUCI 800ML 1 21.900 21.900"),
        )
        assertEquals(4, joined.rows.size)
        val susu = pickItems(joined.rows, null).items.single { it.name == "SUSU UHT 1L" }
        assertEquals(2, susu.quantity)
        assertEquals(Money(36_400), susu.price)
    }

    @Test
    fun `rows of later photos follow the earlier ones in height`() {
        val joined = join(listOf("A ROW 1 1.000 1.000"), listOf("B ROW 1 2.000 2.000"))
        assertEquals(true, joined.rows[1].top > joined.rows[0].bottom)
    }

    @Test
    fun `the parser reads several photos as one receipt`() {
        val parts = listOf(
            linesOf("INDOMARET", "30.10.26-18:20", "INDOMIE GRG SPCL 5 3.100 15.500", "MINYAK GORENG 2L 1 38.900 38.900"),
            linesOf("MINYAK GORENG 2L 1 38.900 38.900", "BERAS PREMIUM 5KG 1 72.500 72.500", "SUSU UHT 1L 2 18.200 36.400", "DISKON : (4.000)"),
            linesOf("SUSU UHT 1L 2 18.200 36.400", "DISKON : (4.000)", "SABUN CUCI 800ML 1 21.900 21.900"),
            linesOf("HARGA JUAL : 226.100", "DISKON KARTU : (20.000)", "TOTAL : 206.100", "KARTU DEBIT : 206.100"),
        )
        val reading = ReceiptParser.readParts(parts, TODAY)
        assertEquals(listOf(3), reading.gapsAfterPhoto)
        assertEquals(5, reading.items.size)
        assertEquals(Money(206_100), reading.total)
        assertEquals(Money(161_200), reading.itemsNet)
        assertEquals("Indomaret", reading.store)
    }

    @Test
    fun `a dot-matrix overlap with colon separators joins`() {
        val joined = join(
            listOf("1 ROKOK KRETEK 1:300 1:300", "1 KACANG ASIN 750 750", "2 SMISS COKLAT 1:690 3-380"),
            listOf("1 R0KOK KRETEK 1,300 1:300", "1 KACANG ASIN 750 750", "2 SWESS COKLAT 1:690 3-380", "1 KERUPUK 590 590"),
        )
        assertEquals(emptyList<Int>(), joined.gapsAfterPhoto)
        assertEquals(4, joined.rows.size)
    }
}
