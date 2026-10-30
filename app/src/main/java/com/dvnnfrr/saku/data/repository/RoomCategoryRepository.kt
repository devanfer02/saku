package com.dvnnfrr.saku.data.repository

import androidx.room.withTransaction
import com.dvnnfrr.saku.data.local.CategoryEntity
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.SubcategoryEntity
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Subcategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val db: SakuDatabase,
) : CategoryRepository {

    private val categoryDao = db.categoryDao()
    private val subcategoryDao = db.subcategoryDao()
    private val txnDao = db.txnDao()

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveByKind(kind: CategoryKind): Flow<List<Category>> =
        categoryDao.observeActiveByKind(kind.name).map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: Long): Category? = categoryDao.byId(id)?.toDomain()

    override suspend fun createCategory(name: String, kind: CategoryKind): Long =
        db.withTransaction {
            val clean = name.trim()
            require(clean.isNotEmpty()) { "A category needs a name" }
            // Checks archived rows too. Otherwise a user could create a second
            // "Kuliah" alongside an archived one, and unarchiving surfaces a duplicate
            // that renameCategory would have rejected.
            categoryDao.findAnyByName(kind.name, clean)?.let {
                throw DuplicateNameException(it.id, it.name)
            }
            categoryDao.insert(
                CategoryEntity(
                    name = clean,
                    kind = kind,
                    colorSlot = ColorSlot.next(categoryDao.allColorSlots().map(::ColorSlot)).index,
                    sortOrder = categoryDao.nextSortOrder(kind.name),
                ),
            )
        }

    override suspend fun renameCategory(id: Long, name: String) {
        db.withTransaction {
            val clean = name.trim()
            require(clean.isNotEmpty()) { "A category needs a name" }
            val existing = categoryDao.byId(id) ?: return@withTransaction
            categoryDao.findAnyByName(existing.kind.name, clean)
                ?.takeIf { it.id != id }
                ?.let { throw DuplicateNameException(it.id, it.name) }
            categoryDao.update(existing.copy(name = clean))
        }
    }

    /** Single column, so it cannot clobber a concurrent rename the way @Update did. */
    override suspend fun recolorCategory(id: Long, slot: ColorSlot) =
        categoryDao.setColorSlot(id, slot.index)

    override suspend fun reorderCategories(orderedIds: List<Long>) {
        db.withTransaction {
            val byId = categoryDao.all().associateBy { it.id }
            val updated = orderedIds.mapIndexedNotNull { index, id ->
                byId[id]?.copy(sortOrder = index)
            }
            if (updated.isNotEmpty()) categoryDao.updateAll(updated)
        }
    }

    override suspend fun setCategoryArchived(id: Long, archived: Boolean) =
        categoryDao.setArchived(id, archived)

    override suspend fun isCategoryDeletable(id: Long): Boolean =
        txnDao.countForCategory(id) == 0

    override suspend fun deleteCategory(id: Long) {
        check(isCategoryDeletable(id)) {
            "Category $id holds transactions; archive it instead so history stays intact"
        }
        categoryDao.deleteUnused(id)
    }

    override suspend fun transactionCount(categoryId: Long): Int =
        txnDao.countForCategory(categoryId)

    /*
     * Grouped, not per row. The category screen shows a count on every row and
     * disables delete wherever there is one; asking per row is an N+1 over nineteen
     * categories to paint one list.
     */

    override suspend fun transactionCountsByCategory(): Map<Long, Int> =
        txnDao.countsByCategory().associate { it.id to it.count }

    override suspend fun allCategories(): List<Category> = categoryDao.all().map { it.toDomain() }

    override suspend fun allSubcategories(): List<Subcategory> =
        subcategoryDao.observeAll().first().map { it.toDomain() }

    /**
     * Moves every transaction across, carries subcategories over merging any name
     * clash into the existing one rather than duplicating it, then archives the source.
     *
     * All of it in one transaction. Unwrapped, process death mid-loop left the
     * transactions moved but the source still active with its subcategories half
     * migrated, a state no retry cleanly repairs.
     */
    override suspend fun mergeCategory(fromId: Long, toId: Long): Int = db.withTransaction {
        require(fromId != toId) { "Cannot merge a category into itself" }
        val from = requireNotNull(categoryDao.byId(fromId)) { "No category $fromId" }
        val to = requireNotNull(categoryDao.byId(toId)) { "No category $toId" }
        require(from.kind == to.kind) { "Can only merge categories of the same kind" }

        val moved = txnDao.reassignCategory(fromId, toId)

        var nextOrder = subcategoryDao.nextSortOrder(toId)
        subcategoryDao.forCategory(fromId).forEach { sub ->
            val clash = subcategoryDao.findActiveByName(toId, sub.name)
            if (clash == null) {
                // Renumber as they move, so migrated rows cannot collide with the
                // target's existing sortOrder values.
                subcategoryDao.update(sub.copy(categoryId = toId, sortOrder = nextOrder++))
            } else {
                txnDao.reassignSubcategory(sub.id, clash.id)
                subcategoryDao.setArchived(sub.id, true)
            }
        }

        categoryDao.setArchived(fromId, true)
        moved
    }

    override suspend fun findOrCreate(name: String, kind: CategoryKind): Long =
        db.withTransaction {
            val clean = name.trim()
            val existing = categoryDao.findAnyByName(kind.name, clean)
            if (existing != null) {
                // Unarchive rather than create a same-named twin, which would split
                // one category's history across two rows in the stats list.
                if (existing.archived) categoryDao.setArchived(existing.id, false)
                return@withTransaction existing.id
            }
            categoryDao.insert(
                CategoryEntity(
                    name = clean,
                    kind = kind,
                    colorSlot = ColorSlot.next(categoryDao.allColorSlots().map(::ColorSlot)).index,
                    sortOrder = categoryDao.nextSortOrder(kind.name),
                ),
            )
        }

    /** Import runs this per row, so it returns an id rather than throwing on a clash. */
    override suspend fun findOrCreateSubcategory(categoryId: Long, name: String): Long =
        db.withTransaction {
            val clean = name.trim()
            require(clean.isNotEmpty()) { "A subcategory needs a name" }
            subcategoryDao.findActiveByName(categoryId, clean)?.let {
                return@withTransaction it.id
            }
            subcategoryDao.insert(
                SubcategoryEntity(
                    categoryId = categoryId,
                    name = clean,
                    sortOrder = subcategoryDao.nextSortOrder(categoryId),
                ),
            )
        }
}
