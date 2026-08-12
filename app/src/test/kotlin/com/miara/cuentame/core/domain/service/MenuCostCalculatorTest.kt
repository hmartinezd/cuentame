package com.miara.cuentame.core.domain.service

import com.miara.cuentame.core.common.ids.*
import com.miara.cuentame.core.model.ingredient.*
import com.miara.cuentame.core.model.menu.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class MenuCostCalculatorTest {
    private val calculator=MenuCostCalculator(); private val recipe=MenuRecipeId("menu")
    private fun component(id:String,ingredient:String,quantity:String,delta:BigDecimal?=null)=MenuCostComponentInput(MenuRecipeComponentId(id),IngredientId(ingredient),BigDecimal(quantity),"unit",BigDecimal(quantity),delta)
    private fun ingredient(id:String,cost:CurrentIngredientCost)=IngredientId(id) to MenuCostIngredientInput(IngredientId(id),id,"u",cost)

    @Test fun `full plate metrics retain precision`() {
        val result=calculator.calculate(recipe,BigDecimal("17"),listOf(component("1","a","2"),component("2","b","0.5")),mapOf(
            ingredient("a",CurrentIngredientCost.Available(BigDecimal("1.125"))),ingredient("b",CurrentIngredientCost.Available(BigDecimal("4")))),emptyMap(),"USD")
        assertEquals(MenuRecipeCostStatus.FULLY_COSTED,result.status); assertEquals(0,result.currentPlateCost!!.compareTo(BigDecimal("4.25")))
        assertEquals(0,result.sellingMetrics.foodCostPercent!!.compareTo(BigDecimal("25"))); assertEquals(0,result.sellingMetrics.grossProfitBeforeLaborAndOverhead!!.compareTo(BigDecimal("12.75")))
    }
    @Test fun `partial never exposes precise total or selling metrics`() {
        val result=calculator.calculate(recipe,BigDecimal.TEN,listOf(component("1","a","2"),component("2","b","1")),mapOf(
            ingredient("a",CurrentIngredientCost.Available(BigDecimal("3"))),ingredient("b",CurrentIngredientCost.Missing)),emptyMap(),"USD")
        assertEquals(MenuRecipeCostStatus.PARTIALLY_COSTED,result.status); assertEquals(BigDecimal("6"),result.knownCostSubtotal)
        assertNull(result.currentPlateCost); assertNull(result.sellingMetrics.foodCostPercent); assertNull(result.sellingMetrics.grossProfitBeforeLaborAndOverhead)
    }
    @Test fun `zero cost is valid and zero selling price is not a denominator`() {
        val result=calculator.calculate(recipe,BigDecimal.ZERO,listOf(component("1","a","3")),mapOf(ingredient("a",CurrentIngredientCost.Available(BigDecimal.ZERO))),emptyMap(),"USD")
        assertEquals(MenuRecipeCostStatus.FULLY_COSTED,result.status); assertEquals(0,result.currentPlateCost!!.compareTo(BigDecimal.ZERO)); assertNull(result.sellingMetrics.foodCostPercent)
    }
    @Test fun `uncosted raw ingredient can retain vendor impact`() {
        val result=calculator.calculate(recipe,null,listOf(component("1","a","2",BigDecimal("0.25"))),mapOf(ingredient("a",CurrentIngredientCost.Missing)),emptyMap(),"USD")
        assertEquals(MenuRecipeCostStatus.UNCOSTED,result.status); assertEquals(BigDecimal("0.50"),result.priceImpact.knownSubtotal); assertTrue(result.priceImpact.isComplete)
    }
    @Test fun `active preparation cost and batch impact are scaled to plate usage`() {
        val prep=PreparationRecipeCost(PreparationRecipeId("prep"),PreparationCostStatus.FULLY_COSTED,1,1,0,BigDecimal("16"),BigDecimal("16"),BigDecimal("128"),"oz",null,BigDecimal("0.125"),"oz",emptySet(),emptyList(),PreparationPriceImpact(BigDecimal("6.40"),1,1),standardYieldQuantityBase=BigDecimal("128"))
        val result=calculator.calculate(recipe,BigDecimal("16"),listOf(component("1","sauce","2")),mapOf(ingredient("sauce",CurrentIngredientCost.Available(BigDecimal("99")))),mapOf(IngredientId("sauce") to prep),"USD")
        assertEquals(MenuCostSource.ACTIVE_PREPARATION_RECIPE,result.components.single().source); assertEquals(BigDecimal("0.250"),result.currentPlateCost); assertEquals(0,result.priceImpact.knownSubtotal.compareTo(BigDecimal("0.10")))
    }
    @Test fun `incomplete active preparation blocks inventory projection fallback`() {
        val prep=PreparationRecipeCost(PreparationRecipeId("prep"),PreparationCostStatus.PARTIALLY_COSTED,2,1,1,BigDecimal.ONE,null,BigDecimal.TEN,"u",null,null,"u",emptySet(),emptyList(),PreparationPriceImpact(BigDecimal.ZERO,0,2),standardYieldQuantityBase=BigDecimal.TEN)
        val result=calculator.calculate(recipe,null,listOf(component("1","prepared","1")),mapOf(ingredient("prepared",CurrentIngredientCost.Available(BigDecimal("99")))),mapOf(IngredientId("prepared") to prep),"USD")
        assertNull(result.currentPlateCost); assertEquals(MenuRecipeCostMissingReason.ACTIVE_PREPARATION_PARTIAL,result.components.single().missingReason)
    }
    private fun preparedImpact(known:String,covered:Int,total:Int,yield:BigDecimal?=BigDecimal.TEN)=PreparationRecipeCost(PreparationRecipeId("prep"),PreparationCostStatus.FULLY_COSTED,1,1,0,BigDecimal.TEN,BigDecimal.TEN,yield,"u",null,BigDecimal.ONE,"u",emptySet(),emptyList(),PreparationPriceImpact(BigDecimal(known),covered,total),standardYieldQuantityBase=yield)
    @Test fun `prepared impact with no covered leaves is unknown not zero`() {
        val result=calculator.calculate(recipe,null,listOf(component("1","p","5")),mapOf(ingredient("p",CurrentIngredientCost.Available(BigDecimal.ONE))),mapOf(IngredientId("p") to preparedImpact("0",0,3)),"USD")
        val c=result.components.single();assertNull(c.vendorPriceImpact);assertEquals(0,c.impactCoveredLeafCount);assertEquals(3,c.impactTotalLeafCount);assertFalse(result.priceImpact.isComplete)
    }
    @Test fun `covered prepared zero impact remains known zero`() {
        val result=calculator.calculate(recipe,null,listOf(component("1","p","5")),mapOf(ingredient("p",CurrentIngredientCost.Available(BigDecimal.ONE))),mapOf(IngredientId("p") to preparedImpact("0",3,3)),"USD")
        assertEquals(0,result.components.single().vendorPriceImpact!!.compareTo(BigDecimal.ZERO));assertTrue(result.priceImpact.isComplete)
    }
    @Test fun `partial prepared impact scales known subtotal and preserves coverage`() {
        val result=calculator.calculate(recipe,null,listOf(component("1","p","5")),mapOf(ingredient("p",CurrentIngredientCost.Available(BigDecimal.ONE))),mapOf(IngredientId("p") to preparedImpact("6",2,3)),"USD")
        val c=result.components.single();assertEquals(0,c.vendorPriceImpact!!.compareTo(BigDecimal("3")));assertEquals(2,c.impactCoveredLeafCount);assertEquals(3,c.impactTotalLeafCount);assertFalse(result.priceImpact.isComplete)
    }
    @Test fun `invalid prepared base yield makes impact unknown`() {
        val result=calculator.calculate(recipe,null,listOf(component("1","p","5")),mapOf(ingredient("p",CurrentIngredientCost.Available(BigDecimal.ONE))),mapOf(IngredientId("p") to preparedImpact("6",3,3,BigDecimal.ZERO)),"USD")
        assertNull(result.components.single().vendorPriceImpact);assertFalse(result.priceImpact.isComplete)
    }
}
