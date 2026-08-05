package com.miara.cuentame.core.common

/**
 * Parses a persisted string into an enum value.
 *
 * @param rawValue The raw string from the database.
 * @param unknownValue The value to return if the string is unrecognized but not blank.
 * @param absentValue The value to return if the string is null, empty, or blank.
 */
inline fun <reified T : Enum<T>> parsePersistedEnum(
    rawValue: String?,
    unknownValue: T,
    absentValue: T = unknownValue
): T {
    if (rawValue.isNullOrBlank()) return absentValue
    return enumValues<T>().firstOrNull { it.name == rawValue } ?: unknownValue
}

/**
 * Parses a persisted string into an enum value or null if the input is null.
 *
 * @param rawValue The raw string from the database.
 * @param unknownValue The value to return if the string is unrecognized but not blank.
 */
inline fun <reified T : Enum<T>> parsePersistedEnumOrNull(
    rawValue: String?,
    unknownValue: T
): T? {
    if (rawValue == null) return null
    if (rawValue.isBlank()) return unknownValue
    return enumValues<T>().firstOrNull { it.name == rawValue } ?: unknownValue
}
