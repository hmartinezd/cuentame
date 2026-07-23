package com.miara.cuentame.feature.onboarding.model

import com.miara.cuentame.R

data class SetupTemplate(
    val key: String,
    val labelResId: Int,
    val defaultName: String, // Use for validation/default
    val defaultSelected: Boolean = false
)

object OnboardingTemplates {
    val SUGGESTED_AREAS = listOf(
        SetupTemplate("area.walk_in_cooler", R.string.template_area_walk_in, "Walk-in Cooler", true),
        SetupTemplate("area.freezer", R.string.template_area_freezer, "Freezer", true),
        SetupTemplate("area.dry_storage", R.string.template_area_dry_storage, "Dry Storage", true),
        SetupTemplate("area.bar", R.string.template_area_bar, "Bar", false),
        SetupTemplate("area.prep_area", R.string.template_area_prep, "Prep Area", false),
        SetupTemplate("area.kitchen_line", R.string.template_area_line, "Kitchen Line", false)
    )

    val SUGGESTED_CATEGORIES = listOf(
        SetupTemplate("category.meat", R.string.template_cat_meat, "Meat"),
        SetupTemplate("category.seafood", R.string.template_cat_seafood, "Seafood"),
        SetupTemplate("category.dairy", R.string.template_cat_dairy, "Dairy"),
        SetupTemplate("category.produce", R.string.template_cat_produce, "Produce"),
        SetupTemplate("category.dry_goods", R.string.template_cat_dry, "Dry Goods"),
        SetupTemplate("category.beverages", R.string.template_cat_beverages, "Beverages"),
        SetupTemplate("category.alcohol", R.string.template_cat_alcohol, "Alcohol"),
        SetupTemplate("category.bakery", R.string.template_cat_bakery, "Bakery"),
        SetupTemplate("category.cleaning", R.string.template_cat_cleaning, "Cleaning Supplies"),
        SetupTemplate("category.other", R.string.template_cat_other, "Other")
    )
}
