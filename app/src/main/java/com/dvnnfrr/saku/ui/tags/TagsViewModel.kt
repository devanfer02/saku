package com.dvnnfrr.saku.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.repository.DuplicateNameException
import com.dvnnfrr.saku.data.repository.TagRepository
import com.dvnnfrr.saku.domain.InvalidTagNameException
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds only which tag is open and the name being typed. Rows and counts come from the
 * repository; [assembleTags] and [renameProblem] decide the rest.
 */
class TagsViewModel(private val tags: TagRepository) : ViewModel() {

    private data class Editing(val id: Long, val draft: String, val refused: RenameProblem? = null)

    private val editing = MutableStateFlow<Editing?>(null)

    val state: StateFlow<TagsUiState> = combine(tags.observeAll(), tags.observeCounts(), editing) { all, counts, open ->
        val tag = open?.let { edit -> all.firstOrNull { it.id == edit.id } }
        val edit = if (open == null || tag == null) {
            null
        } else {
            TagEditUi(
                id = tag.id,
                name = tag.name,
                draft = open.draft,
                archived = tag.archived,
                problem = open.refused ?: renameProblem(open.draft, tag.id, all),
            )
        }
        assembleTags(all, counts, edit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    fun open(id: Long) {
        val name = state.value.let { s -> (s.active + s.archived).firstOrNull { it.id == id }?.name } ?: return
        editing.value = Editing(id, name)
    }

    fun setDraft(draft: String) = editing.update { it?.copy(draft = draft, refused = null) }

    fun close() {
        editing.value = null
    }

    /** Renames and closes; a clash found only at save (another device, a race) stays open and says so. */
    fun saveName() {
        val open = editing.value ?: return
        viewModelScope.launch {
            try {
                tags.rename(open.id, open.draft)
                editing.value = null
            } catch (clash: DuplicateNameException) {
                editing.update { it?.copy(refused = RenameProblem.Taken(clash.name)) }
            } catch (_: InvalidTagNameException) {
                editing.update { it?.copy(refused = RenameProblem.Invalid) }
            }
        }
    }

    /** Archive or unarchive the open tag, then close: the row moves section, which is the feedback. */
    fun toggleArchived() {
        val edit = state.value.editing ?: return
        viewModelScope.launch {
            tags.setArchived(edit.id, !edit.archived)
            editing.value = null
        }
    }

    companion object {
        val Factory = sakuViewModelFactory { TagsViewModel(tags) }
    }
}
