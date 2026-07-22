package com.miara.cuentame.feature.onboarding.model

import com.miara.cuentame.R

data class SetupTemplate(
    val key: String,
    val labelResId: Int,
    val defaultSelected: Boolean = false
)

object OnboardingTemplates {
    val SUGGESTED_AREAS = listOf(
        SetupTemplate("area.walk_in_cooler", R.string.template_area_walk_in, true),
        SetupTemplate("area.freezer", R.string.template_area_freezer, true),
        SetupTemplate("area.dry_storage", R.string.template_area_dry_storage, true),
        SetupTemplate("area.bar", R.string.template_area_bar, false),
        SetupTemplate("area.prep_area", R.string.template_area_prep, false),
        SetupTemplate("area.kitchen_line", R.string.template_area_line, false)
    )

    val SUGGESTED_CATEGORIES = listOf(
        SetupTemplate("category.meat", R.string.template_cat_meat),
        SetupTemplate("category.seafood", R.string.template_cat_seafood),
        SetupTemplate("category.dairy", R.string.template_cat_dairy),
        SetupTemplate("category.produce", R.string.template_cat_produce),
        SetupTemplate("category.dry_goods", R.string.template_cat_dry),
        SetupTemplate("category.beverages", R.string.template_cat_beverages),
        SetupTemplate("category.alcohol", R.string.template_cat_alcohol),
        SetupTemplate("category.bakery", R.string.template_cat_bakery),
        SetupTemplate("category.cleaning", R.string.template_cat_cleaning),
        SetupTemplate("category.other", R.string.template_cat_other)
    )
}
