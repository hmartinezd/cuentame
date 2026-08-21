package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.domain.service.CalculatedPurchaseLine
import com.venkoi.restaurantops.core.domain.service.PurchaseLineCalculator
import java.math.BigDecimal
import javax.inject.Inject

class PreviewPurchaseLineUseCase @Inject constructor(
    private val lineCalculator: PurchaseLineCalculator
) {
    operator fun invoke(
        quantityEntered: BigDecimal,
        lineTotal: BigDecimal,
        optionFactorToBase: BigDecimal
    ): CalculatedPurchaseLine {
        return lineCalculator.calculate(quantityEntered, lineTotal, optionFactorToBase)
    }
}
