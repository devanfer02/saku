package com.dvnnfrr.saku.data.repository

import androidx.room.withTransaction
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TagEntity
import com.dvnnfrr.saku.domain.InvalidTagNameException
import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.domain.TagName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tags are matched by [TagName.key] in Kotlin, not by SQL `LOWER`, which folds ASCII only.
 * The table holds tens of rows, so reading it whole per lookup costs nothing.
 */
class RoomTagRepository(private val db: SakuDatabase) : TagRepository {

    private val tagDao = db.tagDao()

    override fun observeAll(): Flow<List<Tag>> =
        tagDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeCounts(): Flow<Map<Long, Int>> =
        tagDao.observeCounts().map { rows -> rows.associate { it.id to it.count } }

    override suspend fun all(): List<Tag> = tagDao.all().map { it.toDomain() }

    override suspend fun findOrCreate(name: String): Long = db.withTransaction {
        val normalized = TagName.normalize(name) ?: throw InvalidTagNameException(name)
        val key = TagName.key(normalized)
        tagDao.all().firstOrNull { TagName.key(it.name) == key }?.id
            ?: tagDao.insert(TagEntity(name = normalized, sortOrder = tagDao.nextSortOrder()))
    }

    override suspend fun rename(id: Long, name: String) {
        db.withTransaction {
            val normalized = TagName.normalize(name) ?: throw InvalidTagNameException(name)
            val key = TagName.key(normalized)
            val clash = tagDao.all().firstOrNull { it.id != id && TagName.key(it.name) == key }
            if (clash != null) throw DuplicateNameException(clash.id, clash.name)
            tagDao.rename(id, normalized)
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = tagDao.setArchived(id, archived)
}
