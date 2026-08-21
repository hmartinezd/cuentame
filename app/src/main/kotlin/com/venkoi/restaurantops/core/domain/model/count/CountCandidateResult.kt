package com.venkoi.restaurantops.core.domain.model.count

import com.venkoi.restaurantops.core.model.ingredient.Ingredient
import java.math.BigDecimal

data class CountCandidateResult(
    val activeCandidates: List<Ingredient>,
    val missingActiveCandidates: List<Ingredient>,
    val archivedBalanceWarnings: List<ArchivedCountCandidate>
)

data class ArchivedCountCandidate(
    val ingredientId: String,
    val name: String,
    val expectedBalanceBase: BigDecimal
)
