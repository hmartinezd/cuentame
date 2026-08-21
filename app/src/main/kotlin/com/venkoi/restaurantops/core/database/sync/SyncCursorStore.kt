package com.venkoi.restaurantops.core.database.sync

import com.venkoi.restaurantops.core.database.dao.SyncCursorDao
import javax.inject.Inject

class SyncCursorStore @Inject constructor(private val cursorDao: SyncCursorDao) {
    suspend fun getChangeSeq(restaurantId: String, entityType: String): Long =
        cursorDao.get(restaurantId, entityType)?.changeSeq ?: 0L
}
