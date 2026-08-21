package com.venkoi.restaurantops.core.ocr.parser.matching

import com.venkoi.restaurantops.core.model.supplier.SupplierItemMappingKeyType
import kotlin.math.max

object PurchaseInvoiceInventoryMatcher {

    fun match(
        line: EffectiveParsedInvoiceLine,
        supplierId: String?,
        catalog: InventoryCatalog
    ): InventoryMatchResult {
        val normalizedCode = InventoryNormalization.normalizeVendorCode(line.vendorCode)
        val normalizedDesc = InventoryNormalization.normalizeDescription(line.description)
        val normalizedPackage = InventoryNormalization.normalizePackageText(line.packageText)

        // 1. Priority: Known Supplier Mapping (Vendor Code)
        if (supplierId != null && normalizedCode.isNotEmpty()) {
            val mapping = catalog.supplierMappings.find {
                it.supplierId == supplierId &&
                        it.keyType == SupplierItemMappingKeyType.VENDOR_CODE.name &&
                        it.normalizedKey == normalizedCode
            }
            if (mapping != null) {
                return InventoryMatchResult(
                    knownMapping = mapping.toCandidate(MatchReason.ConfirmedSupplierSku, 1.0f),
                    candidates = emptyList()
                )
            }
        }

        // 2. Priority: Known Supplier Mapping (Description + Package)
        if (supplierId != null && normalizedDesc.isNotEmpty()) {
            val key = "$normalizedDesc|$normalizedPackage"
            val mapping = catalog.supplierMappings.find {
                it.supplierId == supplierId &&
                        it.keyType == SupplierItemMappingKeyType.DESCRIPTION_PACKAGE.name &&
                        it.normalizedKey == key
            }
            if (mapping != null) {
                return InventoryMatchResult(
                    knownMapping = mapping.toCandidate(MatchReason.ConfirmedSupplierDescriptionPackage, 1.0f),
                    candidates = emptyList()
                )
            }
        }

        val candidates = mutableListOf<InventoryMatchCandidate>()

        // 3. Priority: Exact Ingredient Name
        if (normalizedDesc.isNotEmpty()) {
            val exactMatch = catalog.ingredients.find { it.normalizedName == normalizedDesc }
            if (exactMatch != null) {
                candidates.add(
                    createCandidate(exactMatch, normalizedPackage, MatchReason.ExactIngredientName, 0.95f)
                )
            }
        }

        // 4. Priority: Fuzzy Description
        if (normalizedDesc.isNotEmpty()) {
            val fuzzyCandidates = catalog.ingredients
                .filter { it.normalizedName != normalizedDesc }
                .map { ingredient ->
                    val similarity = calculateSimilarity(normalizedDesc, ingredient.normalizedName)
                    ingredient to similarity
                }
                .filter { it.second >= 0.4f }
                .sortedByDescending { it.second }
                .take(5)
                .map { (ingredient, similarity) ->
                    createCandidate(ingredient, normalizedPackage, MatchReason.SimilarDescription, similarity)
                }
            candidates.addAll(fuzzyCandidates)
        }

        return InventoryMatchResult(
            knownMapping = null,
            candidates = candidates.distinctBy { it.ingredientId }.sortedByDescending { it.confidence }
        )
    }

    private fun SupplierItemMappingMatchModel.toCandidate(reason: MatchReason, confidence: Float): InventoryMatchCandidate {
        return InventoryMatchCandidate(
            ingredientId = ingredientId,
            unitOptionId = unitOptionId,
            inventoryAreaId = inventoryAreaId,
            reason = reason,
            confidence = confidence,
            mappingId = id
        )
    }

    private fun createCandidate(
        ingredient: IngredientMatchModel,
        normalizedPackage: String,
        reason: MatchReason,
        baseConfidence: Float
    ): InventoryMatchCandidate {
        val compatibleUnit = if (normalizedPackage.isNotEmpty()) {
            ingredient.unitOptions.find { it.normalizedName == normalizedPackage }
        } else null
        
        val finalConfidence = if (compatibleUnit != null) {
            max(baseConfidence, 0.9f)
        } else baseConfidence

        return InventoryMatchCandidate(
            ingredientId = ingredient.id,
            unitOptionId = compatibleUnit?.id,
            inventoryAreaId = ingredient.defaultAreaId,
            reason = if (compatibleUnit != null && reason == MatchReason.SimilarDescription) MatchReason.DescriptionAndPackageMatch else reason,
            confidence = finalConfidence
        )
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        val tokens1 = s1.split(" ").filter { it.length >= 2 }.toSet()
        val tokens2 = s2.split(" ").filter { it.length >= 2 }.toSet()
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0f
        
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return intersection.toFloat() / union
    }
}
