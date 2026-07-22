package com.miara.cuentame.core.database.repository

import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.database.dao.WasteDao
import com.miara.cuentame.core.database.mapper.toDomain
import com.miara.cuentame.core.database.mapper.toEntity
import com.miara.cuentame.core.domain.repository.WasteDraftRepository
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.waste.WasteEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomWasteDraftRepository @Inject constructor(
    private val wasteDao: WasteDao
) : WasteDraftRepository {

    override fun observeDraftEvents(): Flow<List<WasteEvent>> {
        return wasteDao.observeEvents().map { entities ->
            entities.filter { it.status == DocumentStatus.DRAFT.name }.map { it.toDomain() }
        }
    }

    override suspend fun getDraftEvent(id: WasteEventId): WasteEvent? {
        return wasteDao.getById(id.value)?.toDomain()
    }

    override suspend fun saveDraft(event: WasteEvent) {
        if (event.status != DocumentStatus.DRAFT) {
            throw IllegalArgumentException("Only DRAFT waste events can be saved via this repository")
        }
        wasteDao.upsert(event.toEntity())
    }

    override suspend fun deleteDraft(id: WasteEventId) {
        wasteDao.deleteDraftEvent(id.value)
    }
}
