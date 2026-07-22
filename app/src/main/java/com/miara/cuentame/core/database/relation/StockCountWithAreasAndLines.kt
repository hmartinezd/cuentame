package com.miara.cuentame.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.miara.cuentame.core.database.entity.StockCountAreaEntity
import com.miara.cuentame.core.database.entity.StockCountEntity
import com.miara.cuentame.core.database.entity.StockCountLineEntity

data class StockCountWithAreasAndLines(
    @Embedded val count: StockCountEntity,
    @Relation(
        entity = StockCountAreaEntity::class,
        parentColumn = "id",
        entityColumn = "stockCountId"
    )
    val areas: List<StockCountAreaWithLines>
)

data class StockCountAreaWithLines(
    @Embedded val area: StockCountAreaEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "stockCountAreaId"
    )
    val lines: List<StockCountLineEntity>
)
