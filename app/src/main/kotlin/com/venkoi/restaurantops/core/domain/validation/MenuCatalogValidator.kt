package com.venkoi.restaurantops.core.domain.validation

import com.venkoi.restaurantops.core.common.text.normalizeName
import com.venkoi.restaurantops.core.model.menu.Menu
import com.venkoi.restaurantops.core.model.menu.MenuCategory
import com.venkoi.restaurantops.core.model.menu.MenuPlacement
import java.math.BigDecimal

sealed interface MenuCatalogValidationFailure {
    data object MenuNameRequired : MenuCatalogValidationFailure
    data object InvalidDefaultCashDiscountPercent : MenuCatalogValidationFailure
    data object NegativePublicationRevision : MenuCatalogValidationFailure
    data object CategoryNameRequired : MenuCatalogValidationFailure
    data object NegativeCategorySortOrder : MenuCatalogValidationFailure
    data object DuplicateCategoryName : MenuCatalogValidationFailure
    data object CategoryDoesNotBelongToMenu : MenuCatalogValidationFailure
    data object NegativePlacementSortOrder : MenuCatalogValidationFailure
    data object PlacementDoesNotBelongToMenu : MenuCatalogValidationFailure
    data object PlacementCategoryDoesNotBelongToMenu : MenuCatalogValidationFailure
    data object DuplicateMenuRecipePlacement : MenuCatalogValidationFailure
}

/** Pure rules for one mutable menu definition. No repository existence checks are performed. */
object MenuCatalogValidator {
    private val oneHundred = BigDecimal("100")

    fun canonicalize(menu: Menu): Menu = menu.copy(
        name = menu.name.trim(),
        normalizedName = menu.name.normalizeName(),
        description = menu.description?.trim()?.takeIf(String::isNotEmpty)
    )

    fun canonicalize(category: MenuCategory): MenuCategory = category.copy(
        name = category.name.trim(),
        normalizedName = category.name.normalizeName()
    )

    fun validateMenu(menu: Menu): List<MenuCatalogValidationFailure> = buildList {
        if (menu.name.normalizeName().isBlank()) add(MenuCatalogValidationFailure.MenuNameRequired)
        if (menu.defaultCashDiscountPercent < BigDecimal.ZERO ||
            menu.defaultCashDiscountPercent >= oneHundred
        ) {
            add(MenuCatalogValidationFailure.InvalidDefaultCashDiscountPercent)
        }
        if (menu.publicationRevision < 0) add(MenuCatalogValidationFailure.NegativePublicationRevision)
    }

    fun validateCategories(
        menu: Menu,
        categories: List<MenuCategory>
    ): List<MenuCatalogValidationFailure> = buildList {
        categories.forEach { category ->
            if (category.name.normalizeName().isBlank()) add(MenuCatalogValidationFailure.CategoryNameRequired)
            if (category.sortOrder < 0) add(MenuCatalogValidationFailure.NegativeCategorySortOrder)
            if (category.menuId != menu.id) add(MenuCatalogValidationFailure.CategoryDoesNotBelongToMenu)
        }
        if (categories.groupingBy { it.name.normalizeName() }.eachCount().any { (name, count) ->
                name.isNotBlank() && count > 1
            }
        ) {
            add(MenuCatalogValidationFailure.DuplicateCategoryName)
        }
    }

    fun validatePlacements(
        menu: Menu,
        categories: List<MenuCategory>,
        placements: List<MenuPlacement>
    ): List<MenuCatalogValidationFailure> = buildList {
        val categoriesById = categories.associateBy { it.id }
        placements.forEach { placement ->
            if (placement.sortOrder < 0) add(MenuCatalogValidationFailure.NegativePlacementSortOrder)
            if (placement.menuId != menu.id) add(MenuCatalogValidationFailure.PlacementDoesNotBelongToMenu)
            val category = categoriesById[placement.categoryId]
            if (category == null || category.menuId != placement.menuId) {
                add(MenuCatalogValidationFailure.PlacementCategoryDoesNotBelongToMenu)
            }
        }
        if (placements.filter { it.menuId == menu.id }
                .groupingBy { it.menuRecipeId }
                .eachCount()
                .any { it.value > 1 }
        ) {
            add(MenuCatalogValidationFailure.DuplicateMenuRecipePlacement)
        }
    }

    fun validate(
        menu: Menu,
        categories: List<MenuCategory>,
        placements: List<MenuPlacement>
    ): List<MenuCatalogValidationFailure> =
        validateMenu(menu) +
            validateCategories(menu, categories) +
            validatePlacements(menu, categories, placements)
}
