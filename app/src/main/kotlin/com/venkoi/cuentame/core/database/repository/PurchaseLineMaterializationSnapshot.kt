package com.venkoi.cuentame.core.database.repository

import com.venkoi.cuentame.core.database.entity.PurchaseLineEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal

@Serializable
internal data class PurchaseLineMaterializationSnapshot(
    val ingredientId: String,
    val areaId: String,
    val unitOptionId: String,
    val quantityEntered: String,
    val quantityBase: String,
    val lineTotal: String,
    val unitCostBase: String
)

internal fun PurchaseLineEntity.matchesMaterializationSnapshot(snapshotJson: String?, json: Json): Boolean {
    val snapshot = runCatching {
        json.decodeFromString<PurchaseLineMaterializationSnapshot>(snapshotJson ?: return false)
    }.getOrNull() ?: return false
    return ingredientId == snapshot.ingredientId && areaId == snapshot.areaId &&
        ingredientUnitOptionId == snapshot.unitOptionId &&
        quantityEntered.sameDecimalAs(snapshot.quantityEntered) &&
        quantityBase.sameDecimalAs(snapshot.quantityBase) &&
        lineTotal.sameDecimalAs(snapshot.lineTotal) &&
        unitCostBase.sameDecimalAs(snapshot.unitCostBase)
}

private fun String.sameDecimalAs(other: String): Boolean = runCatching {
    BigDecimal(this).compareTo(BigDecimal(other)) == 0
}.getOrDefault(false)
