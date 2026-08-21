package com.venkoi.restaurantops.core.domain.validation

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.common.ids.MenuCategoryId
import com.venkoi.restaurantops.core.common.ids.MenuId
import com.venkoi.restaurantops.core.common.ids.MenuPlacementId
import com.venkoi.restaurantops.core.common.ids.MenuRecipeId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.model.menu.Menu
import com.venkoi.restaurantops.core.model.menu.MenuCategory
import com.venkoi.restaurantops.core.model.menu.MenuPlacement
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test

class MenuCatalogValidatorTest {
    @Test
    fun `valid menu accepts three percent discount and initial publication revision`() {
        assertThat(MenuCatalogValidator.validateMenu(menu())).isEmpty()
    }

    @Test
    fun `menu name is canonicalized with shared name normalization`() {
        val canonical = MenuCatalogValidator.canonicalize(
            menu().copy(name = "  DINNER   Menu  ", description = "  Evening service  ")
        )

        assertThat(canonical.name).isEqualTo("DINNER   Menu")
        assertThat(canonical.normalizedName).isEqualTo("dinner menu")
        assertThat(canonical.description).isEqualTo("Evening service")
    }

    @Test
    fun `blank menu name is rejected`() {
        assertThat(MenuCatalogValidator.validateMenu(menu().copy(name = " \t ")))
            .contains(MenuCatalogValidationFailure.MenuNameRequired)
    }

    @Test
    fun `discount must be at least zero and less than one hundred`() {
        assertThat(MenuCatalogValidator.validateMenu(menu().copy(defaultCashDiscountPercent = BigDecimal.ZERO))).isEmpty()
        listOf("-1", "100", "150").forEach { value ->
            assertThat(
                MenuCatalogValidator.validateMenu(
                    menu().copy(defaultCashDiscountPercent = BigDecimal(value))
                )
            ).contains(MenuCatalogValidationFailure.InvalidDefaultCashDiscountPercent)
        }
    }

    @Test
    fun `negative publication revision is rejected`() {
        assertThat(MenuCatalogValidator.validateMenu(menu().copy(publicationRevision = -1)))
            .contains(MenuCatalogValidationFailure.NegativePublicationRevision)
    }

    @Test
    fun `valid categories allow tied non-negative sort orders`() {
        val menu = menu()
        val categories = listOf(
            category("appetizers", menu.id, "Appetizers", 10),
            category("entrees", menu.id, "Entrees", 10)
        )

        assertThat(MenuCatalogValidator.validateCategories(menu, categories)).isEmpty()
    }

    @Test
    fun `invalid category fields and ownership are rejected`() {
        val menu = menu()
        val categories = listOf(
            category("blank", menu.id, "  ", -1),
            category("other", MenuId("other-menu"), "Sides", 0)
        )

        assertThat(MenuCatalogValidator.validateCategories(menu, categories)).containsAtLeast(
            MenuCatalogValidationFailure.CategoryNameRequired,
            MenuCatalogValidationFailure.NegativeCategorySortOrder,
            MenuCatalogValidationFailure.CategoryDoesNotBelongToMenu
        )
    }

    @Test
    fun `equivalent category names by whitespace and case are duplicates`() {
        val menu = menu()
        val categories = listOf(
            category("one", menu.id, "Appetizers", 0),
            category("two", menu.id, "  APPETIZERS  ", 10)
        )

        assertThat(MenuCatalogValidator.validateCategories(menu, categories))
            .contains(MenuCatalogValidationFailure.DuplicateCategoryName)
        assertThat(MenuCatalogValidator.canonicalize(categories[1]).normalizedName)
            .isEqualTo("appetizers")
    }

    @Test
    fun `valid placements allow distinct recipes and tied sort orders`() {
        val menu = menu()
        val category = category("entrees", menu.id, "Entrees", 0)
        val placements = listOf(
            placement("one", menu.id, category.id, "recipe-a", 10),
            placement("two", menu.id, category.id, "recipe-b", 10)
        )

        assertThat(MenuCatalogValidator.validatePlacements(menu, listOf(category), placements)).isEmpty()
    }

    @Test
    fun `placement category and menu ownership plus sort order are validated`() {
        val menu = menu()
        val otherMenuId = MenuId("other-menu")
        val otherCategory = category("other-category", otherMenuId, "Other", 0)
        val placements = listOf(
            placement("wrong-menu", otherMenuId, otherCategory.id, "recipe-a", 0),
            placement("wrong-category", menu.id, otherCategory.id, "recipe-b", -1)
        )

        assertThat(MenuCatalogValidator.validatePlacements(menu, listOf(otherCategory), placements)).containsAtLeast(
            MenuCatalogValidationFailure.PlacementDoesNotBelongToMenu,
            MenuCatalogValidationFailure.PlacementCategoryDoesNotBelongToMenu,
            MenuCatalogValidationFailure.NegativePlacementSortOrder
        )
    }

    @Test
    fun `same recipe cannot be placed twice in one menu`() {
        val menu = menu()
        val first = category("first", menu.id, "First", 0)
        val second = category("second", menu.id, "Second", 10)
        val placements = listOf(
            placement("one", menu.id, first.id, "recipe-a", 0),
            placement("two", menu.id, second.id, "recipe-a", 0)
        )

        assertThat(MenuCatalogValidator.validatePlacements(menu, listOf(first, second), placements))
            .contains(MenuCatalogValidationFailure.DuplicateMenuRecipePlacement)
    }

    @Test
    fun `same recipe can be placed once in each of two menus`() {
        val dinner = menu("dinner")
        val delivery = menu("delivery")
        val dinnerCategory = category("dinner-entrees", dinner.id, "Entrees", 0)
        val deliveryCategory = category("delivery-entrees", delivery.id, "Entrees", 0)

        assertThat(
            MenuCatalogValidator.validatePlacements(
                dinner,
                listOf(dinnerCategory),
                listOf(placement("dinner-placement", dinner.id, dinnerCategory.id, "recipe-a", 0))
            )
        ).isEmpty()
        assertThat(
            MenuCatalogValidator.validatePlacements(
                delivery,
                listOf(deliveryCategory),
                listOf(placement("delivery-placement", delivery.id, deliveryCategory.id, "recipe-a", 0))
            )
        ).isEmpty()
    }

    private fun menu(id: String = "dinner") = Menu(
        id = MenuId(id),
        restaurantId = RestaurantId("restaurant"),
        name = "Dinner Menu",
        normalizedName = "dinner menu",
        description = null,
        defaultCashDiscountPercent = BigDecimal("3.00"),
        publicationRevision = 0,
        archivedAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun category(id: String, menuId: MenuId, name: String, sortOrder: Int) = MenuCategory(
        id = MenuCategoryId(id),
        menuId = menuId,
        name = name,
        normalizedName = name.lowercase(),
        sortOrder = sortOrder
    )

    private fun placement(
        id: String,
        menuId: MenuId,
        categoryId: MenuCategoryId,
        recipeId: String,
        sortOrder: Int
    ) = MenuPlacement(
        id = MenuPlacementId(id),
        menuId = menuId,
        categoryId = categoryId,
        menuRecipeId = MenuRecipeId(recipeId),
        sortOrder = sortOrder
    )
}
