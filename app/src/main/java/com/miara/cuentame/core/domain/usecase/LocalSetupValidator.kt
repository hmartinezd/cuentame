package com.miara.cuentame.core.domain.usecase

import com.miara.cuentame.core.domain.repository.CompleteLocalSetupCommand
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.domain.validation.ValidationError
import java.util.Currency

class LocalSetupValidator {
    fun validate(command: CompleteLocalSetupCommand) {
        if (command.restaurantName.trim().isEmpty()) {
            throw ValidationError.InvalidName
        }
        
        try {
            Currency.getInstance(command.currencyCode)
        } catch (e: Exception) {
            throw ValidationError.InvalidCurrencyCode
        }

        if (command.localeTag !in listOf("en-US", "es-US")) {
            throw ValidationError.UnsupportedLocale
        }

        if (command.areas.isEmpty()) {
            throw ValidationError.NoActiveInventoryArea
        }

        val allAreaNames = command.areas.map { it.name.normalizeName() }
        if (allAreaNames.any { it.isBlank() }) {
            throw ValidationError.InvalidName
        }
        if (allAreaNames.size != allAreaNames.distinct().size) {
            throw ValidationError.DuplicateActiveName
        }

        val allCategoryNames = command.categories.map { it.name.normalizeName() }
        if (allCategoryNames.any { it.isBlank() }) {
            throw ValidationError.InvalidName
        }
        if (allCategoryNames.size != allCategoryNames.distinct().size) {
            throw ValidationError.DuplicateActiveName
        }
    }
}
