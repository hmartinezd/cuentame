package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.model.waste.WasteEvent
import kotlinx.coroutines.flow.Flow

interface WasteDraftRepository {
    fun observeDraftEvents(): Flow<List<WasteEvent>>
    suspend fun getDraftEvent(id: WasteEventId): WasteEvent?
    suspend fun saveDraft(event: WasteEvent)
    suspend fun deleteDraft(id: WasteEventId)
}
