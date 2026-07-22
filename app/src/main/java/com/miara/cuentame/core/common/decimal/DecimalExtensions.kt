package com.miara.cuentame.core.common.decimal

import java.math.BigDecimal
import java.math.RoundingMode

object DecimalConstants {
    const val QUANTITY_SCALE = 8
    const val COST_SCALE = 6
    const val MONEY_SCALE = 2
    val ROUNDING_MODE = RoundingMode.HALF_UP
}

fun BigDecimal.toStorageString(): String = this.toPlainString()

fun String.toBigDecimalValue(): BigDecimal = BigDecimal(this)

fun BigDecimal.roundToQuantity(): BigDecimal =
    this.setScale(DecimalConstants.QUANTITY_SCALE, DecimalConstants.ROUNDING_MODE)

fun BigDecimal.roundToCost(): BigDecimal =
    this.setScale(DecimalConstants.COST_SCALE, DecimalConstants.ROUNDING_MODE)

fun BigDecimal.roundToMoney(): BigDecimal =
    this.setScale(DecimalConstants.MONEY_SCALE, DecimalConstants.ROUNDING_MODE)

data class Money(
    val amount: BigDecimal,
    val currencyCode: String
)
