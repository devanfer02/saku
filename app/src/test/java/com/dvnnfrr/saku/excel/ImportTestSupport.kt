package com.dvnnfrr.saku.excel

import kotlinx.datetime.TimeZone

/**
 * The zone the reference data was recorded in.
 *
 * Excel stores no zone, so every timestamp in a sheet is a wall-clock reading that only
 * becomes an instant once a zone is chosen. Pinning it here rather than taking the
 * machine's default is what stops these tests passing in Jakarta and failing in CI.
 */
val JAKARTA: TimeZone = TimeZone.of("Asia/Jakarta")

/** Builds a sheet in a given layout from rows of cells, for the cases the fixture lacks. */
fun sheetOf(layout: SheetLayout, vararg rows: List<CellValue>): SheetTable =
    SheetTable(
        sheetName = "test",
        headers = layout.headers,
        rows = rows.mapIndexed { index, cells -> SheetRow(index + 1, cells) },
    )
