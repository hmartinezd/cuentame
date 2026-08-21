package com.venkoi.cuentame.feature.menu.viewmodel

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.model.ingredient.IngredientUnitOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class MenuUnitDefaultTest {
    private fun option(id:String,base:Boolean=false,count:Boolean=false,active:Boolean=true)=IngredientUnitOption(IngredientUnitOptionId(id),IngredientId("beef"),id,id,null,BigDecimal.ONE,base,count,false,active,Instant.EPOCH,Instant.EPOCH)
    @Test fun `default count wins regardless of list order`() { assertEquals(IngredientUnitOptionId("count"),deterministicMenuUnitDefault(listOf(option("other"),option("count",count=true),option("base",base=true)))) }
    @Test fun `base is used when no default count exists`() { assertEquals(IngredientUnitOptionId("base"),deterministicMenuUnitDefault(listOf(option("other"),option("base",base=true)))) }
    @Test fun `single active option is a convenience default`() { assertEquals(IngredientUnitOptionId("only"),deterministicMenuUnitDefault(listOf(option("old",active=false),option("only")))) }
    @Test fun `ambiguous options require explicit user choice`() { assertNull(deterministicMenuUnitDefault(listOf(option("oz"),option("case")))) }
}
