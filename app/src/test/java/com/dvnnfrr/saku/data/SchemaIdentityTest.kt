package com.dvnnfrr.saku.data

import com.dvnnfrr.saku.data.local.SakuDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The database import refuses a file whose identity hash differs from
 * [SakuDatabase.IDENTITY_HASH]. Room does not expose its hash at runtime, so the constant is
 * a copy; this keeps it equal to what Room exported for [SakuDatabase.VERSION]. A schema
 * change regenerates the JSON and fails here until the constant follows.
 */
class SchemaIdentityTest {

    @Test
    fun `the identity hash constant is the one Room exported for this version`() {
        val json = schemaJson().readText()
        val exported = Regex("\"identityHash\"\\s*:\\s*\"([0-9a-f]+)\"").find(json)?.groupValues?.get(1)
        val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt()

        assertEquals(SakuDatabase.VERSION, version)
        assertEquals(exported, SakuDatabase.IDENTITY_HASH)
    }
}

/** Room's exported schema for the current version. Unit tests run from the module directory. */
fun schemaJson(): File =
    File("schemas/com.dvnnfrr.saku.data.local.SakuDatabase/${SakuDatabase.VERSION}.json")
