package com.miara.cuentame.core.common.text

import java.util.Locale

fun String.normalizeName(): String {
    return this.trim()
        .replace("\\s+".toRegex(), " ")
        .lowercase(Locale.ROOT)
}
