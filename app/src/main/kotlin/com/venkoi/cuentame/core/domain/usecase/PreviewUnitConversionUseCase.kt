package com.venkoi.cuentame.core.domain.usecase

import com.venkoi.cuentame.core.domain.service.StandardUnitConverter
import com.venkoi.cuentame.core.model.inventory.UnitOfMeasure
import java.math.BigDecimal
import javax.inject.Inject

class PreviewUnitConversionUseCase @Inject constructor(
    private val standardUnitConverter: StandardUnitConverter
) {
    /**
     * Preview conversion from a source unit to a target (base) unit.
     */
    fun preview(
        quantity: BigDecimal,
        sourceUnit: UnitOfMeasure,
        targetUnit: UnitOfMeasure
    ): BigDecimal {
        return standardUnitConverter.convert(quantity, sourceUnit, targetUnit)
    }
}
