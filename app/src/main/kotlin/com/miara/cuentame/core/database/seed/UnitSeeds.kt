package com.miara.cuentame.core.database.seed

import com.miara.cuentame.core.database.entity.UnitEntity
import com.miara.cuentame.core.model.inventory.UnitDimension
import java.math.BigDecimal

object UnitSeeds {
    val ALL_UNITS = listOf(
        // Mass
        UnitEntity("mass_g", "Gram", "g", UnitDimension.MASS.name, BigDecimal("1"), true, 1),
        UnitEntity("mass_kg", "Kilogram", "kg", UnitDimension.MASS.name, BigDecimal("1000"), true, 2),
        UnitEntity("mass_oz", "Ounce", "oz", UnitDimension.MASS.name, BigDecimal("28.349523125"), true, 3),
        UnitEntity("mass_lb", "Pound", "lb", UnitDimension.MASS.name, BigDecimal("453.59237"), true, 4),

        // Volume
        UnitEntity("volume_ml", "Milliliter", "ml", UnitDimension.VOLUME.name, BigDecimal("1"), true, 10),
        UnitEntity("volume_l", "Liter", "L", UnitDimension.VOLUME.name, BigDecimal("1000"), true, 11),
        UnitEntity("volume_tsp_us", "Teaspoon", "tsp", UnitDimension.VOLUME.name, BigDecimal("4.92892159375"), true, 12),
        UnitEntity("volume_tbsp_us", "Tablespoon", "tbsp", UnitDimension.VOLUME.name, BigDecimal("14.78676478125"), true, 13),
        UnitEntity("volume_fl_oz_us", "Fluid ounce", "fl oz", UnitDimension.VOLUME.name, BigDecimal("29.5735295625"), true, 14),
        UnitEntity("volume_cup_us", "Cup", "cup", UnitDimension.VOLUME.name, BigDecimal("236.5882365"), true, 15),
        UnitEntity("volume_pint_us", "Pint", "pt", UnitDimension.VOLUME.name, BigDecimal("473.176473"), true, 16),
        UnitEntity("volume_quart_us", "Quart", "qt", UnitDimension.VOLUME.name, BigDecimal("946.352946"), true, 17),
        UnitEntity("volume_gallon_us", "Gallon", "gal", UnitDimension.VOLUME.name, BigDecimal("3785.411784"), true, 18),

        // Count
        UnitEntity("count_each", "Each", "ea", UnitDimension.COUNT.name, BigDecimal("1"), true, 30)
    )
}
