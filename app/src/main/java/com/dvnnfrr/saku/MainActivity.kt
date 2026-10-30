package com.dvnnfrr.saku

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dvnnfrr.saku.ui.SakuApp

class MainActivity : ComponentActivity() {

    /**
     * Set when the weekly reminder was tapped, until Trans has opened the entry sheet.
     * A one-shot request, not screen state: it is read from the intent only on a fresh
     * launch, so rotating does not open the sheet a second time.
     */
    private var openEntry by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openEntry = intent.opensEntry()
        val container = (application as SakuApplication).container
        setContent {
            SakuApp(
                store = container.appearance,
                openEntry = openEntry,
                onEntryOpened = { openEntry = false },
            )
        }
    }

    /** The reminder tapped while Saku was already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.opensEntry()) openEntry = true
    }

    companion object {
        private const val EXTRA_OPEN_ENTRY = "com.dvnnfrr.saku.OPEN_ENTRY"

        private fun Intent.opensEntry(): Boolean = getBooleanExtra(EXTRA_OPEN_ENTRY, false)

        /**
         * Opens Saku on Trans with the entry sheet on a new transaction. Reuses a running
         * Saku through [onNewIntent] rather than stacking a second one.
         */
        fun openEntryIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_ENTRY, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
    }
}
