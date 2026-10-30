package com.dvnnfrr.saku.data

import com.dvnnfrr.saku.data.local.MIGRATION_1_2_SQL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first migration Saku ships may only create. Anything that drops, deletes, updates,
 * alters or copies could touch the ledger, so it fails here before it reaches a phone.
 * Robolectric only for a real `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationSqlTest {

    private val createOnly = Regex("""^CREATE (TABLE|INDEX) IF NOT EXISTS `\w+`""")
    private val forbidden = Regex("""\b(DROP|DELETE|UPDATE|ALTER|INSERT|REPLACE|TRUNCATE)\b""", RegexOption.IGNORE_CASE)

    @Test
    fun `every statement in the 1 to 2 migration is a create`() {
        MIGRATION_1_2_SQL.forEach { sql ->
            assertTrue("Not a create: $sql", createOnly.containsMatchIn(sql))
            // Strip the foreign key actions, which name ON DELETE / ON UPDATE but write nothing.
            val body = sql.replace(Regex("""ON (UPDATE|DELETE) (NO ACTION|CASCADE|RESTRICT|SET NULL)"""), "")
            assertTrue("Writes data: $sql", !forbidden.containsMatchIn(body))
        }
    }

    @Test
    fun `the migration creates exactly what Room exported for the new tables`() {
        val (v1, v2) = createSqlByTable(1) to createSqlByTable(2)
        val added = v2.filterKeys { it !in v1 }
        val expected = added.flatMap { (table, sql) -> sql.map { it.replace("\${TABLE_NAME}", table) } }
        assertEquals(expected.sorted(), MIGRATION_1_2_SQL.sorted())
    }

    @Test
    fun `every version 1 table is unchanged in version 2`() {
        val v1 = createSqlByTable(1)
        val v2 = createSqlByTable(2)
        v1.forEach { (table, sql) -> assertEquals("Table $table changed", sql, v2[table]) }
    }

    /** Table name to its create statement and its index statements, from the exported schema. */
    private fun createSqlByTable(version: Int): Map<String, List<String>> {
        val entities = JSONObject(schemaJson(version).readText()).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).associate { i ->
            val entity = entities.getJSONObject(i)
            val indices = entity.optJSONArray("indices")
            val indexSql = (0 until (indices?.length() ?: 0)).map { indices!!.getJSONObject(it).getString("createSql") }
            entity.getString("tableName") to listOf(entity.getString("createSql")) + indexSql
        }
    }
}
