package com.dvnnfrr.saku.ui.receipt

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dvnnfrr.saku.container
import com.dvnnfrr.saku.data.repository.AccountRepository
import com.dvnnfrr.saku.data.repository.CategoryRepository
import com.dvnnfrr.saku.data.repository.DuplicateNameException
import com.dvnnfrr.saku.data.repository.TagRepository
import com.dvnnfrr.saku.data.repository.TransactionRepository
import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.receipt.OcrLine
import com.dvnnfrr.saku.receipt.ReceiptParser
import com.dvnnfrr.saku.ui.entry.EntryTag
import com.dvnnfrr.saku.ui.entry.minusTag
import com.dvnnfrr.saku.ui.entry.plusTag
import com.dvnnfrr.saku.ui.entry.plusTyped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The photos read so far, in the order taken, and whether one is still being read. */
private data class Photos(
    val parts: List<List<OcrLine>> = emptyList(),
    val reading: Int = 0,
    val unreadable: Boolean = false,
)

/**
 * The Receipt screen. Holds the photos' read lines and the user's [ReceiptChoices]; the
 * reading, the item rows and the check are derived in [assembleReceipt].
 *
 * The photo itself is never kept: it is read into lines and dropped (a camera photo's file
 * is deleted by the recognizer as soon as it is read).
 */
class ReceiptViewModel(
    private val transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    private val tagRepository: TagRepository,
    private val readPhoto: suspend (Uri) -> List<OcrLine>,
    private val newCameraUri: () -> Uri,
    private val discardCameraPhoto: () -> Unit,
    private val loadDefaults: () -> ReceiptDefaults,
    private val rememberDefaults: (ReceiptDefaults) -> Unit,
    firstPhoto: Uri?,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val photos = MutableStateFlow(Photos())
    private val choices = MutableStateFlow(ReceiptChoices())
    private val categoryKind = CategoryKind.EXPENSE

    /** The time of day every saved row carries: when the scan began. */
    private val scannedAt: LocalDateTime = clock.now().toLocalDateTime(zone)
    private val defaults: ReceiptDefaults = loadDefaults()

    /** The Save prompt for missing categories is open. */
    private val _askingCategory = MutableStateFlow(false)
    val askingCategory: StateFlow<Boolean> = _askingCategory.asStateFlow()

    /** Set once the rows are written: the day Trans should open on. */
    private val _savedDay = MutableStateFlow<LocalDate?>(null)
    val savedDay: StateFlow<LocalDate?> = _savedDay.asStateFlow()

    private val readings = photos
        .map { it.parts }
        .distinctUntilChanged()
        .map { ReceiptParser.readParts(it, scannedAt.date) }

    private val lookups = combine(
        transactions.observeItemCategories(),
        categoryRepository.observeActiveByKind(categoryKind),
        accountRepository.observeActive(),
        tagRepository.observeAll(),
    ) { dictionary, categories, accounts, tags -> Lookups(dictionary, categories, accounts, tags) }

    private data class Lookups(
        val dictionary: Map<String, Long>,
        val categories: List<Category>,
        val accounts: List<Account>,
        val tags: List<Tag>,
    )

    val state: StateFlow<ReceiptUiState> =
        combine(readings, photos, choices, lookups) { reading, photos, choices, lookups ->
            assembleReceipt(
                reading = reading,
                choices = choices,
                dictionary = lookups.dictionary,
                categories = lookups.categories,
                accounts = lookups.accounts,
                allTags = lookups.tags,
                defaults = defaults,
                photos = photos.parts.size,
                readingPhoto = photos.reading > 0,
                unreadable = photos.unreadable,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState(reading = firstPhoto != null))

    /** All tags, for the tag picker, archived included so a typed archived name is reused. */
    val allTags: StateFlow<List<Tag>> =
        tagRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reads one photo at a time, so parts join in the order they were taken. */
    private val readLock = Mutex()

    init {
        firstPhoto?.let(::addPhoto)
    }

    /** Where the system camera should write the next part. */
    fun cameraUri(): Uri = newCameraUri()

    /** The camera was closed without a photo; drop the empty file it may have left. */
    fun cameraCancelled() = discardCameraPhoto()

    /** Reads [uri] as the next part of the receipt, below the parts already read. */
    fun addPhoto(uri: Uri) {
        photos.update { it.copy(reading = it.reading + 1, unreadable = false) }
        viewModelScope.launch {
            readLock.withLock {
                val lines = runCatching { readPhoto(uri) }.getOrDefault(emptyList())
                photos.update {
                    if (lines.isEmpty()) {
                        it.copy(reading = it.reading - 1, unreadable = true)
                    } else {
                        it.copy(parts = it.parts + listOf(lines), reading = it.reading - 1)
                    }
                }
            }
        }
    }

    fun setRecordAs(recordAs: RecordAs) = choices.update { it.copy(recordAs = recordAs) }

    fun setItemCategory(key: String, categoryId: Long) =
        choices.update { it.copy(categoryByItem = it.categoryByItem + (key to categoryId)) }

    fun setOneRowCategory(categoryId: Long) = choices.update { it.copy(oneRowCategoryId = categoryId) }

    fun setGapCategory(categoryId: Long) = choices.update { it.copy(gapCategoryId = categoryId) }

    fun setGapRow(on: Boolean) = choices.update { it.copy(gapRow = on) }

    /** A chip on the total: that amount becomes the total, and the old one a chip. */
    fun pickTotal(amount: Money) = choices.update { it.copy(total = amount) }

    fun addLineToNote(line: String) = choices.update {
        it.copy(oneRowNote = appendToNote(it.oneRowNote ?: state.value.oneRowNote, line))
    }

    fun setAccount(id: Long) = choices.update { it.copy(accountId = id) }

    private fun updateTags(transform: (List<EntryTag>) -> List<EntryTag>) =
        choices.update { it.copy(tags = transform(it.tags ?: state.value.tags)) }

    fun pickTag(tag: Tag) = updateTags { it.plusTag(tag) }

    fun addTypedTag(typed: String) = updateTags { it.plusTyped(typed, allTags.value) }

    fun removeTag(tag: EntryTag) = updateTags { it.minusTag(tag) }

    /**
     * Creates a category from the picker and applies it through [apply]. A name that exists
     * already applies the existing category, as the entry sheet does.
     */
    fun createCategory(name: String, apply: (Long) -> Unit) {
        viewModelScope.launch {
            val id = try {
                categoryRepository.createCategory(name, categoryKind)
            } catch (clash: DuplicateNameException) {
                clash.existingId
            }
            apply(id)
        }
    }

    /** Save: asks for missing categories first, then writes every row in one transaction. */
    fun save() {
        val current = state.value
        if (!current.canSave) return
        if (current.unsetCount > 0) {
            _askingCategory.value = true
            return
        }
        write(current)
    }

    /** The prompt's answer: [categoryId] for everything unset, then save. */
    fun useForAllUnset(categoryId: Long) {
        choices.update { it.fillUnset(state.value, categoryId) }
        _askingCategory.value = false
        viewModelScope.launch {
            // The state is derived; wait for the fill to reach it before writing.
            val filled = state.first { it.unsetCount == 0 }
            write(filled)
        }
    }

    fun dismissCategoryPrompt() {
        _askingCategory.value = false
    }

    private var writing = false

    private fun write(current: ReceiptUiState) {
        if (writing) return
        val account = current.account ?: return
        val rows = receiptRows(current, scannedAt, zone)
        if (rows.isEmpty()) return
        writing = true
        viewModelScope.launch {
            try {
                // A typed tag is created here, at Save, so leaving the screen leaves no tag.
                val tagIds = current.tags.map { it.id ?: tagRepository.findOrCreate(it.name) }.toSet()
                transactions.saveAll(rows, tagIds)
                rememberDefaults(ReceiptDefaults(account.id, tagIds))
                _savedDay.value = receiptDay(current.date, scannedAt.date)
            } finally {
                writing = false
            }
        }
    }

    companion object {
        /** The route argument holding the first photo's URI. */
        const val PHOTO_ARG = "photo"

        val Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                with(container()) {
                    ReceiptViewModel(
                        transactions = transactions,
                        categoryRepository = categories,
                        accountRepository = accounts,
                        tagRepository = tags,
                        readPhoto = receipts::read,
                        newCameraUri = receipts::newCameraUri,
                        discardCameraPhoto = receipts::clearCache,
                        loadDefaults = {
                            ReceiptDefaults(receiptDefaults.lastAccountId(), receiptDefaults.lastTagIds())
                        },
                        rememberDefaults = { receiptDefaults.remember(requireNotNull(it.accountId), it.tagIds) },
                        firstPhoto = handle.get<String>(PHOTO_ARG)?.takeIf { it.isNotEmpty() }?.let(Uri::parse),
                    )
                }
            }
        }
    }
}
