package com.venkoi.restaurantops.core.common.decimal

import java.math.BigDecimal

fun BigDecimal.toCanonicalDecimalString(): String =
    if (compareTo(BigDecimal.ZERO) == 0) "0" else stripTrailingZeros().toPlainString()
