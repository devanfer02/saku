package com.dvnnfrr.saku.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a fresh install has not completed first run`() {
        assertFalse(FirstRunStore(context).isComplete())
    }

    @Test
    fun `completion survives a new store instance`() {
        FirstRunStore(context).markComplete()

        assertTrue(FirstRunStore(context).isComplete())
    }
}
