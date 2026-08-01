package com.miara.cuentame.core.model.catalog

import java.math.BigDecimal

data class StarterCatalogDefinition(
    val key: String,
    val version: Int,
    val categories: List<StarterCategoryDefinition>,
    val items: List<StarterItemDefinition>
)

data class StarterCategoryDefinition(
    val sourceName: String,
    val sortOrder: Int
)

data class StarterItemDefinition(
    val sourceCategoryName: String,
    val name: String,
    val baseUnitId: String,
    val baseOptionLabel: String,
    val baseOptionShortLabel: String,
    val additionalUnitOptions: List<StarterUnitOptionDefinition> = emptyList()
)

data class StarterUnitOptionDefinition(
    val displayName: String,
    val shortLabel: String,
    val factorToBase: BigDecimal,
    val isDefaultCount: Boolean,
    val isDefaultPurchase: Boolean
)
