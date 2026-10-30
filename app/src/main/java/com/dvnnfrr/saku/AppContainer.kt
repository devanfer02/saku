package com.dvnnfrr.saku

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dvnnfrr.saku.data.AppearanceStore
import com.dvnnfrr.saku.data.ChartStyleStore
import com.dvnnfrr.saku.data.FirstRunSeed
import com.dvnnfrr.saku.data.ReminderStore
import com.dvnnfrr.saku.data.SharedPreferencesAppearanceStore
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.ImportRepository
import com.dvnnfrr.saku.data.repository.RoomAccountRepository
import com.dvnnfrr.saku.data.repository.RoomCategoryRepository
import com.dvnnfrr.saku.data.repository.RoomImportRepository
import com.dvnnfrr.saku.data.repository.RoomTransactionRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.reminder.ReminderScheduler
import com.dvnnfrr.saku.ui.filter.SharedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.io.File

/**
 * The one place repositories are constructed.
 *
 * Deliberately hand-rolled rather than Hilt: four repositories and one database do
 * not justify an annotation processor. What matters is that it exists *before* the
 * fan-out gate. Without it each of the four parallel tracks would invent its own way
 * to reach a repository from a ViewModel, and all four would collide in `MainActivity`
 * at integration, in the one file no track owns.
 *
 * A track needing a new dependency adds it here and says so; it does not build a
 * second container.
 */
interface AppContainer {
    val transactions: TransactionRepository
    val accounts: AccountRepository
    val categories: CategoryRepository
    val imports: ImportRepository
    val appearance: AppearanceStore

    /** The Stats chart style, remembered across restarts. */
    val chartStyle: ChartStyleStore

    /** The Trans and Stats filter. Process-scoped and never persisted. */
    val filter: SharedFilter

    /** The weekly logging reminder's settings. */
    val reminders: ReminderStore

    /** Arms the weekly reminder's alarm from [reminders]. */
    val reminderScheduler: ReminderScheduler

    /** Where a picked file is copied to be read once, as the database import does. */
    val cacheDir: File
}

class RoomAppContainer(context: Context) : AppContainer {

    /** Exposed so the application can run the first-run seed against it. */
    val database: SakuDatabase by lazy { SakuDatabase.get(context) }

    private val db: SakuDatabase get() = database

    override val transactions: TransactionRepository by lazy { RoomTransactionRepository(db) }
    override val accounts: AccountRepository by lazy { RoomAccountRepository(db) }
    override val categories: CategoryRepository by lazy { RoomCategoryRepository(db) }
    override val imports: ImportRepository by lazy { RoomImportRepository(db) }

    /**
     * Not lazy. The theme reads it during the first composition, so deferring the
     * construction only moves the same work behind a lock on the main thread.
     */
    override val appearance: AppearanceStore = SharedPreferencesAppearanceStore(context)

    override val chartStyle: ChartStyleStore by lazy { ChartStyleStore(context) }

    override val filter: SharedFilter = SharedFilter()

    override val reminders: ReminderStore by lazy { ReminderStore(context) }

    override val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(context) }

    override val cacheDir: File = context.cacheDir
}

/**
 * The as-of date the seeded accounts open on.
 *
 * Deliberately far back, not the moment of install. A seeded account opens holding
 * nothing, and nothing is the same claim on any date — so the date is free, and the
 * earliest one is the only one that does not silently discard history.
 *
 * Opening at install time broke the app's whole purpose. Balances count transactions
 * on or after the as-of date, so every one of the 1,200 imported rows — all of them
 * older than the install — would have counted toward no balance. Worse, a transfer
 * dated before its account's as-of date is refused outright, and one refusal aborts
 * the batch: importing the reference export would have failed on all of it.
 *
 * The user states a real opening balance, with a real date, on the accounts screen.
 * That is the point at which an as-of date means something.
 */
internal val SEED_CUTOVER = Instant.fromEpochSeconds(0)

class SakuApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val roomContainer = RoomAppContainer(this)
        container = roomContainer

        // The seed is what makes a fresh install usable: with no account the entry
        // sheet cannot save anything. Categories are not seeded; the entry sheet
        // creates them inline. applyIfEmpty is idempotent
        // and returns on the first query when the database already has data, so this
        // costs one read per launch rather than needing a "has run" flag.
        // Re-arms the reminder on every launch. A battery saver or a force-stop drops
        // alarms without telling the app, and this is the one moment it can notice.
        scope.launch {
            roomContainer.reminderScheduler.sync(roomContainer.reminders.current())
        }

        scope.launch {
            FirstRunSeed.applyIfEmpty(
                db = roomContainer.database,
                openingAsOf = SEED_CUTOVER,
                zone = TimeZone.currentSystemDefault(),
            )
        }
    }
}

/**
 * Reaches the container from inside a [viewModelFactory] initializer.
 *
 * Usage in a feature package:
 * ```
 * companion object {
 *     val Factory = viewModelFactory {
 *         initializer { AccountsViewModel(container().accounts) }
 *     }
 * }
 * ```
 */
fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SakuApplication).container

/** Convenience so a feature package does not have to import [initializer] to see the pattern. */
inline fun <reified VM : androidx.lifecycle.ViewModel> sakuViewModelFactory(
    crossinline create: AppContainer.() -> VM,
) = viewModelFactory {
    initializer { container().create() }
}
