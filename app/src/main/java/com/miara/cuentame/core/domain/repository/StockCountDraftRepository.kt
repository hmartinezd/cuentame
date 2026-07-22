package com.miara.cuentame.core.domain.repository

import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.model.count.StockCount
import com.miara.cuentame.core.model.count.StockCountArea
import com.miara.cuentame.core.model.count.StockCountLine
import kotlinx.coroutines.flow.Flow

interface StockCountDraftRepository {
    fun observeDraftCounts(): Flow<List<StockCount>>
    suspend fun getDraftCount(id: StockCountId): StockCount?
    suspend fun getDraftAreas(id: StockCountId): List<StockCountArea>
    suspend fun getDraftLines(areaId: com.miara.cuentame.core.common.ids.StockCountAreaId): List<StockCountLine>
    suspend fun saveDraft(count: StockCount, areas: List<StockCountArea>, lines: Map<com.miara.cuentame.core.common.ids.StockCountAreaId, List<StockCountLine>>)
    suspend fun deleteDraft(id: StockCountId)
}
