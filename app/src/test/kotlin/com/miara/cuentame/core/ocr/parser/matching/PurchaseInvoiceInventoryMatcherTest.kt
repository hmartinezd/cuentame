package com.miara.cuentame.core.ocr.parser.matching

import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PurchaseInvoiceInventoryMatcherTest {

    private val catalog = InventoryCatalog(
        ingredients = listOf(
            IngredientMatchModel("ing-1", "Roma Tomato", "roma tomato", "area-1", 
                listOf(UnitOptionMatchModel("opt-1", "25 lb Case", "25 lb case"))),
            IngredientMatchModel("ing-2", "Cherry Tomato", "cherry tomato", "area-1", emptyList()),
            IngredientMatchModel("ing-3", "Chicken Breast", "chicken breast", "area-2", emptyList())
        ),
        supplierMappings = listOf(
            SupplierItemMappingMatchModel("map-1", "sup-A", SupplierItemMappingKeyType.VENDOR_CODE.name, "000123", "ing-1", "opt-1", "area-1")
        )
    )

    @Test
    fun `exact vendor code mapping ranks first`() {
        val line = EffectiveParsedInvoiceLine("000123", "WRONG DESCRIPTION", null, null, null, null)
        val result = PurchaseInvoiceInventoryMatcher.match(line, "sup-A", catalog)
        
        assertNotNull(result.knownMapping)
        assertEquals("ing-1", result.knownMapping?.ingredientId)
        assertEquals(MatchReason.KnownSupplierItem, result.knownMapping?.reason)
    }

    @Test
    fun `vendor code mapping is supplier-specific`() {
        val line = EffectiveParsedInvoiceLine("000123", "ROMA TOMATO", null, null, null, null)
        val result = PurchaseInvoiceInventoryMatcher.match(line, "sup-B", catalog)
        
        assertNull(result.knownMapping)
        // Should fall back to exact name match
        assertEquals("ing-1", result.candidates.first().ingredientId)
        assertEquals(MatchReason.ExactIngredientName, result.candidates.first().reason)
    }

    @Test
    fun `exact ingredient name match works without mapping`() {
        val line = EffectiveParsedInvoiceLine(null, "Roma Tomato", null, null, null, null)
        val result = PurchaseInvoiceInventoryMatcher.match(line, "sup-A", catalog)
        
        assertNull(result.knownMapping)
        assertEquals("ing-1", result.candidates.first().ingredientId)
        assertEquals(MatchReason.ExactIngredientName, result.candidates.first().reason)
    }

    @Test
    fun `fuzzy matching works for similar descriptions`() {
        val line = EffectiveParsedInvoiceLine(null, "TOMATO ROMA", null, null, null, null)
        val result = PurchaseInvoiceInventoryMatcher.match(line, "sup-A", catalog)
        
        // "TOMATO ROMA" vs "roma tomatoes" -> tokens [tomato, roma] match
        assertEquals("ing-1", result.candidates.first().ingredientId)
        assertEquals(MatchReason.SimilarDescription, result.candidates.first().reason)
    }

    @Test
    fun `package compatibility increases confidence`() {
        val line = EffectiveParsedInvoiceLine(null, "ROMA", "25 LB CASE", null, null, null)
        val result = PurchaseInvoiceInventoryMatcher.match(line, "sup-A", catalog)
        
        val best = result.candidates.first()
        assertEquals("ing-1", best.ingredientId)
        assertEquals("opt-1", best.unitOptionId)
        assertEquals(MatchReason.DescriptionAndPackageMatch, best.reason)
    }
}
