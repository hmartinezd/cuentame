package com.miara.cuentame.feature.onboarding.model

import kotlinx.serialization.Serializable

@Serializable
data class OnboardingCustomItemDraft(
    val id: String,
    val name: String,
    val sortOrder: Int
)

@Serializable
data class OnboardingDraft(
    val formatVersion: Int = 1,
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val restaurantName: String = "",
    val currencyCode: String = "USD",
    val localeTag: String = "en-US",
    val selectedSuggestedAreaKeys: Set<String> = emptySet(),
    val customAreas: List<OnboardingCustomItemDraft> = emptyList(),
    val selectedSuggestedCategoryKeys: Set<String> = emptySet(),
    val customCategories: List<OnboardingCustomItemDraft> = emptyList()
)
