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

        // Validate contiguous area sort orders starting at zero
        val areaSortOrders = command.areas.map { it.sortOrder }.sorted()
        if (areaSortOrders != (0 until command.areas.size).toList()) {
            throw ValidationError.InvalidSetupState
        }

        val allCategoryNames = command.categories.map { it.name.normalizeName() }
        if (allCategoryNames.any { it.isBlank() }) {
            throw ValidationError.InvalidName
        }
        if (allCategoryNames.size != allCategoryNames.distinct().size) {
            throw ValidationError.DuplicateActiveName
        }

        // Validate contiguous category sort orders starting at zero
        val categorySortOrders = command.categories.map { it.sortOrder }.sorted()
        if (categorySortOrders != (0 until command.categories.size).toList()) {
            throw ValidationError.InvalidSetupState
        }
    }
}
