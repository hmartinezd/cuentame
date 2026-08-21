package com.venkoi.restaurantops.core.domain.service

import java.math.BigDecimal
import java.math.MathContext

const val LARGE_COUNT_VARIANCE_RATIO = "0.50"

fun hasLargeCountVariance(expected: BigDecimal?, counted: BigDecimal): Boolean {
    if (expected == null || expected <= BigDecimal.ZERO) return false
    val ratio = counted.subtract(expected).abs().divide(expected, MathContext.DECIMAL128)
    return ratio >= BigDecimal(LARGE_COUNT_VARIANCE_RATIO)
}
