package com.venkoi.restaurantops.core.model.purchase

import java.time.Instant

object MatchIntegrityPolicy {

    fun validateInvariants(
        status: InvoiceLineMatchStatus,
        ingredientId: Any?,
        unitOptionId: Any?,
        inventoryAreaId: Any?,
        confirmedAt: Any?,
        mappingId: Any?
    ): String? {
        return when (status) {
            InvoiceLineMatchStatus.CONFIRMED -> {
                if (ingredientId == null || unitOptionId == null || inventoryAreaId == null || confirmedAt == null) {
                    "CONFIRMED match requires ingredient, unit option, area and confirmation timestamp"
                } else null
            }
            InvoiceLineMatchStatus.SUGGESTED,
            InvoiceLineMatchStatus.NEEDS_REVIEW -> {
                if (confirmedAt != null) {
                    "Non-CONFIRMED match must not have confirmation timestamp"
                } else null
            }
            InvoiceLineMatchStatus.UNMATCHED -> {
                if (confirmedAt != null || mappingId != null) {
                    "UNMATCHED match must not have confirmation timestamp or mapping provenance"
                } else null
            }
        }
    }

    fun isMappingCompatible(
        matchIngredientId: String?,
        matchUnitOptionId: String?,
        matchAreaId: String?,
        mappingIngredientId: String,
        mappingUnitOptionId: String?,
        mappingAreaId: String?
    ): String? {
        if (matchIngredientId != mappingIngredientId) {
            return "Incompatible mapping ingredient"
        }
        if (mappingUnitOptionId != null && matchUnitOptionId != mappingUnitOptionId) {
            return "Incompatible mapping unit option"
        }
        if (mappingAreaId != null && matchAreaId != mappingAreaId) {
            return "Incompatible mapping area"
        }
        return null
    }
}
