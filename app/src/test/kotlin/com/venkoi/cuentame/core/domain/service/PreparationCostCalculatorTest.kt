package com.venkoi.cuentame.core.domain.service

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.*
import com.venkoi.cuentame.core.model.ingredient.*
import org.junit.Test
import java.math.BigDecimal

class PreparationCostCalculatorTest {
    private val calculator = PreparationCostCalculator()
    private fun bd(value: String) = BigDecimal(value)
    private fun assertDecimal(actual: BigDecimal?, expected: String) = assertThat(actual?.compareTo(bd(expected))).isEqualTo(0)
    private fun ingredient(id: String, cost: String?) = PreparationCostIngredientInput(
        IngredientId(id), id, "lb", cost?.let { CurrentIngredientCost.Available(bd(it)) } ?: CurrentIngredientCost.Missing)
    private fun component(id: String, ingredient: String, qty: String, delta: String? = null) =
        PreparationCostComponentInput(PreparationRecipeComponentId(id), IngredientId(ingredient), bd(qty), "lb", bd(qty), delta?.let(::bd))
    private fun recipe(id: String, output: String, status: PreparationRecipeStatus = PreparationRecipeStatus.DRAFT,
                       yield: String? = "1", baseYield: String? = yield, components: List<PreparationCostComponentInput>) =
        PreparationCostRecipeInput(PreparationRecipeId(id), IngredientId(output), status, yield?.let(::bd), baseYield?.let(::bd), "lb", components)

    @Test fun fullyCostedUsesBigDecimalWithoutIntermediateRounding() {
        val result = calculator.calculate(PreparationRecipeId("r"), listOf(recipe("r", "out", components = listOf(component("c", "flour", "3.333")))),
            mapOf(IngredientId("out") to ingredient("out", null), IngredientId("flour") to ingredient("flour", "1.2345")))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.FULLY_COSTED)
        assertDecimal(result.totalBatchCost, "4.1145885")
        assertDecimal(result.costPerYieldUnit, "4.1145885")
    }

    @Test fun legitimateZeroIsCosted() {
        val result = calculator.calculate(PreparationRecipeId("r"), listOf(recipe("r", "out", components = listOf(component("c", "water", "2")))),
            mapOf(IngredientId("out") to ingredient("out", null), IngredientId("water") to ingredient("water", "0")))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.FULLY_COSTED)
        assertDecimal(result.totalBatchCost, "0")
    }

    @Test fun partialShowsKnownSubtotalButNoTotal() {
        val result = calculator.calculate(PreparationRecipeId("r"), listOf(recipe("r", "out", components = listOf(component("a", "a", "2"), component("b", "b", "4")))),
            mapOf(IngredientId("out") to ingredient("out", null), IngredientId("a") to ingredient("a", "3"), IngredientId("b") to ingredient("b", null)))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.PARTIALLY_COSTED)
        assertDecimal(result.knownCostSubtotal, "6")
        assertThat(result.totalBatchCost).isNull()
        assertThat(result.components.last().missingReason).isEqualTo(PreparationCostMissingReason.INGREDIENT_COST_MISSING)
    }

    @Test fun allMissingIsUncostedAndNegativeIsInvalid() {
        val result = calculator.calculate(PreparationRecipeId("r"), listOf(recipe("r", "out", components = listOf(component("a", "a", "1"), component("b", "b", "1")))),
            mapOf(IngredientId("out") to ingredient("out", null), IngredientId("a") to ingredient("a", null), IngredientId("b") to ingredient("b", "-1")))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.UNCOSTED)
        assertThat(result.components.last().missingReason).isEqualTo(PreparationCostMissingReason.INGREDIENT_COST_INVALID)
    }

    @Test fun yieldAndBaseYieldAreSeparate() {
        val result = calculator.calculate(PreparationRecipeId("r"), listOf(recipe("r", "out", yield = "2", baseYield = "4", components = listOf(component("c", "a", "8")))),
            mapOf(IngredientId("out") to ingredient("out", null), IngredientId("a") to ingredient("a", "1")))!!
        assertDecimal(result.costPerYieldUnit, "4")
        assertDecimal(result.costPerOutputBaseUnit, "2")
    }

    @Test fun missingOrZeroYieldKeepsBatchCost() {
        val result = calculator.calculate(PreparationRecipeId("r"), listOf(recipe("r", "out", yield = null, baseYield = "0", components = listOf(component("c", "a", "1")))),
            mapOf(IngredientId("out") to ingredient("out", null), IngredientId("a") to ingredient("a", "2")))!!
        assertDecimal(result.totalBatchCost, "2")
        assertThat(result.costPerYieldUnit).isNull()
        assertThat(result.costPerOutputBaseUnit).isNull()
        assertThat(result.yieldWarnings).hasSize(2)
    }

    @Test fun activeNestedRecipeOverridesInventoryProjection() {
        val child = recipe("child", "dough", PreparationRecipeStatus.ACTIVE, yield = "2", baseYield = "2", components = listOf(component("flour", "flour", "4")))
        val parent = recipe("parent", "pizza", components = listOf(component("dough", "dough", "3")))
        val result = calculator.calculate(parent.id, listOf(parent, child), mapOf(
            IngredientId("pizza") to ingredient("pizza", null), IngredientId("dough") to ingredient("dough", "99"), IngredientId("flour") to ingredient("flour", "2")))!!
        assertDecimal(result.totalBatchCost, "12")
        assertThat(result.components.single().costSource).isEqualTo(PreparationCostSource.ACTIVE_PREPARATION_RECIPE)
    }

    @Test fun draftNestedRecipeIsNotAuthoritative() {
        val child = recipe("child", "dough", PreparationRecipeStatus.DRAFT, components = listOf(component("flour", "flour", "4")))
        val parent = recipe("parent", "pizza", components = listOf(component("dough", "dough", "1")))
        val result = calculator.calculate(parent.id, listOf(parent, child), mapOf(IngredientId("pizza") to ingredient("pizza", null), IngredientId("dough") to ingredient("dough", "5"), IngredientId("flour") to ingredient("flour", "2")))!!
        assertDecimal(result.totalBatchCost, "5")
        assertThat(result.components.single().costSource).isEqualTo(PreparationCostSource.INGREDIENT_AVERAGE_COST)
    }

    @Test fun nestedPartialDoesNotFallBackToPreparedIngredientProjection() {
        val child = recipe("child", "sauce", PreparationRecipeStatus.ACTIVE, components = listOf(component("missing", "tomato", "1")))
        val parent = recipe("parent", "dish", components = listOf(component("sauce", "sauce", "1")))
        val result = calculator.calculate(parent.id, listOf(parent, child), mapOf(IngredientId("dish") to ingredient("dish", null), IngredientId("sauce") to ingredient("sauce", "20"), IngredientId("tomato") to ingredient("tomato", null)))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.UNCOSTED)
        assertThat(result.components.single().missingReason).isEqualTo(PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_UNCOSTED)
    }

    @Test fun dependencyCycleReturnsTypedIncompleteReason() {
        val a = recipe("a", "aOut", PreparationRecipeStatus.ACTIVE, components = listOf(component("ab", "bOut", "1")))
        val b = recipe("b", "bOut", PreparationRecipeStatus.ACTIVE, components = listOf(component("ba", "aOut", "1")))
        val result = calculator.calculate(a.id, listOf(a, b), mapOf(IngredientId("aOut") to ingredient("aOut", "1"), IngredientId("bOut") to ingredient("bOut", "1")))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.UNCOSTED)
        assertThat(result.components.single().missingReason).isEqualTo(PreparationCostMissingReason.RECIPE_DEPENDENCY_CYCLE)
    }

    @Test fun vendorImpactSupportsPositiveNegativeZeroAndPartialCoverage() {
        val r = recipe("r", "out", components = listOf(component("a", "a", "2", ".5"), component("b", "b", "3", "-.2"), component("c", "c", "1", "0"), component("d", "d", "1")))
        val result = calculator.calculate(r.id, listOf(r), listOf("out", "a", "b", "c", "d").associate { IngredientId(it) to ingredient(it, if (it == "out") null else "1") })!!
        assertDecimal(result.priceImpact.knownSubtotal, "0.4")
        assertThat(result.priceImpact.coveredLeafCount).isEqualTo(3)
        assertThat(result.priceImpact.totalLeafCount).isEqualTo(4)
        assertThat(result.priceImpact.isComplete).isFalse()
    }

    @Test fun nestedVendorImpactScalesForFullFractionalAndMultipleBatchUsage() {
        val child = recipe("child", "sauce", PreparationRecipeStatus.ACTIVE, yield = "4", baseYield = "4",
            components = listOf(component("tomato", "tomato", "2", "1")))
        fun impactFor(quantity: String) = calculator.calculate(
            PreparationRecipeId("parent"),
            listOf(recipe("parent", "dish", components = listOf(component("sauce", "sauce", quantity))), child),
            listOf("dish", "sauce", "tomato").associate { IngredientId(it) to ingredient(it, if (it == "tomato") "1" else null) }
        )!!.priceImpact.knownSubtotal

        assertDecimal(impactFor("4"), "2")
        assertDecimal(impactFor("2"), "1")
        assertDecimal(impactFor("1"), ".5")
        assertDecimal(impactFor("10"), "5")
    }

    @Test fun nestedNegativeImpactIsScaled() {
        val child = recipe("child", "sauce", PreparationRecipeStatus.ACTIVE, baseYield = "4",
            components = listOf(component("tomato", "tomato", "2", "-1")))
        val parent = recipe("parent", "dish", components = listOf(component("sauce", "sauce", "1")))
        val result = calculator.calculate(parent.id, listOf(parent, child), listOf("dish", "sauce", "tomato")
            .associate { IngredientId(it) to ingredient(it, if (it == "tomato") "1" else null) })!!
        assertDecimal(result.priceImpact.knownSubtotal, "-.5")
    }

    @Test fun nestedPartialImpactScalesKnownSubtotalAndPreservesLeafCoverage() {
        val child = recipe("child", "sauce", PreparationRecipeStatus.ACTIVE, baseYield = "4", components = listOf(
            component("known", "known", "2", "1"), component("unknown", "unknown", "1")))
        val parent = recipe("parent", "dish", components = listOf(component("sauce", "sauce", "1")))
        val result = calculator.calculate(parent.id, listOf(parent, child), listOf("dish", "sauce", "known", "unknown")
            .associate { IngredientId(it) to ingredient(it, if (it in setOf("known", "unknown")) "1" else null) })!!
        assertDecimal(result.priceImpact.knownSubtotal, ".5")
        assertThat(result.priceImpact.coveredLeafCount).isEqualTo(1)
        assertThat(result.priceImpact.totalLeafCount).isEqualTo(2)
        assertThat(result.priceImpact.isComplete).isFalse()
    }

    @Test fun fullyCostedNestedRecipeWithoutBaseYieldHasYieldReasonAndNoImpact() {
        val child = recipe("child", "sauce", PreparationRecipeStatus.ACTIVE, baseYield = null,
            components = listOf(component("tomato", "tomato", "2", "1")))
        val parent = recipe("parent", "dish", components = listOf(component("sauce", "sauce", "1")))
        val result = calculator.calculate(parent.id, listOf(parent, child), listOf("dish", "sauce", "tomato")
            .associate { IngredientId(it) to ingredient(it, if (it == "tomato") "1" else null) })!!
        assertThat(result.components.single().missingReason)
            .isEqualTo(PreparationCostMissingReason.ACTIVE_NESTED_RECIPE_YIELD_UNAVAILABLE)
        assertThat(result.components.single().vendorPriceImpact).isNull()
        assertThat(result.priceImpact.isComplete).isFalse()
    }

    @Test fun explicitInvalidIngredientCostIsNotMissing() {
        val r = recipe("r", "out", components = listOf(component("c", "bad", "1")))
        val result = calculator.calculate(r.id, listOf(r), mapOf(
            IngredientId("out") to ingredient("out", null),
            IngredientId("bad") to PreparationCostIngredientInput(IngredientId("bad"), "bad", "lb", CurrentIngredientCost.Invalid)
        ))!!
        assertThat(result.components.single().missingReason).isEqualTo(PreparationCostMissingReason.INGREDIENT_COST_INVALID)
    }

    @Test fun missingCurrentCostKeepsKnownPositiveVendorImpact() {
        val r = recipe("r", "out", components = listOf(component("c", "missing", "4", ".5")))
        val result = calculator.calculate(r.id, listOf(r), mapOf(
            IngredientId("out") to ingredient("out", null), IngredientId("missing") to ingredient("missing", null)
        ))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.UNCOSTED)
        assertThat(result.totalBatchCost).isNull()
        assertDecimal(result.knownCostSubtotal, "0")
        assertThat(result.components.single().componentCurrentCost).isNull()
        assertThat(result.components.single().missingReason).isEqualTo(PreparationCostMissingReason.INGREDIENT_COST_MISSING)
        assertDecimal(result.components.single().vendorPriceImpact, "2")
        assertThat(result.priceImpact.coveredLeafCount).isEqualTo(1)
        assertThat(result.priceImpact.totalLeafCount).isEqualTo(1)
        assertThat(result.priceImpact.isComplete).isTrue()
    }

    @Test fun invalidCurrentCostKeepsKnownNegativeVendorImpact() {
        val r = recipe("r", "out", components = listOf(component("c", "invalid", "8", "-.25")))
        val result = calculator.calculate(r.id, listOf(r), mapOf(
            IngredientId("out") to ingredient("out", null),
            IngredientId("invalid") to PreparationCostIngredientInput(
                IngredientId("invalid"), "invalid", "lb", CurrentIngredientCost.Invalid
            )
        ))!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.UNCOSTED)
        assertThat(result.components.single().missingReason).isEqualTo(PreparationCostMissingReason.INGREDIENT_COST_INVALID)
        assertDecimal(result.components.single().vendorPriceImpact, "-2")
        assertThat(result.priceImpact.coveredLeafCount).isEqualTo(1)
        assertThat(result.priceImpact.totalLeafCount).isEqualTo(1)
    }

    @Test fun missingCurrentCostKeepsKnownZeroVendorImpactCovered() {
        val r = recipe("r", "out", components = listOf(component("c", "missing", "2", "0")))
        val result = calculator.calculate(r.id, listOf(r), mapOf(
            IngredientId("out") to ingredient("out", null), IngredientId("missing") to ingredient("missing", null)
        ))!!
        assertDecimal(result.components.single().vendorPriceImpact, "0")
        assertThat(result.priceImpact.coveredLeafCount).isEqualTo(1)
        assertThat(result.priceImpact.totalLeafCount).isEqualTo(1)
    }

    @Test fun missingCurrentCostWithoutComparisonLeavesVendorImpactUncovered() {
        val r = recipe("r", "out", components = listOf(component("c", "missing", "2")))
        val result = calculator.calculate(r.id, listOf(r), mapOf(
            IngredientId("out") to ingredient("out", null), IngredientId("missing") to ingredient("missing", null)
        ))!!
        assertThat(result.components.single().vendorPriceImpact).isNull()
        assertThat(result.priceImpact.coveredLeafCount).isEqualTo(0)
        assertThat(result.priceImpact.totalLeafCount).isEqualTo(1)
    }

    @Test fun incompleteNestedChildStillPropagatesKnownVendorImpact() {
        val child = recipe("child", "sauce", PreparationRecipeStatus.ACTIVE, baseYield = "4",
            components = listOf(component("tomato", "tomato", "5", ".4")))
        val parent = recipe("parent", "dish", components = listOf(component("sauce", "sauce", "1")))
        val result = calculator.calculate(parent.id, listOf(parent, child), listOf("dish", "sauce", "tomato")
            .associate { IngredientId(it) to ingredient(it, null) })!!
        assertThat(result.status).isEqualTo(PreparationCostStatus.UNCOSTED)
        assertThat(result.components.single().componentCurrentCost).isNull()
        assertDecimal(result.components.single().vendorPriceImpact, ".5")
        assertThat(result.priceImpact.isComplete).isTrue()
    }

    @Test fun incompleteNestedChildWithInvalidBaseYieldDoesNotPropagateImpact() {
        val child = recipe("child", "sauce", PreparationRecipeStatus.ACTIVE, baseYield = "0",
            components = listOf(component("tomato", "tomato", "5", ".4")))
        val parent = recipe("parent", "dish", components = listOf(component("sauce", "sauce", "1")))
        val result = calculator.calculate(parent.id, listOf(parent, child), listOf("dish", "sauce", "tomato")
            .associate { IngredientId(it) to ingredient(it, null) })!!
        assertThat(result.components.single().vendorPriceImpact).isNull()
        assertThat(result.priceImpact.isComplete).isFalse()
    }
}
