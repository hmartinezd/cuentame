package com.miara.cuentame.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity

data class IngredientWithUnitOptions(
    @Embedded val ingredient: IngredientEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "ingredientId"
    )
    val unitOptions: List<IngredientUnitOptionEntity>
)
