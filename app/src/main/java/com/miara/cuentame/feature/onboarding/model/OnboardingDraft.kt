package com.miara.cuentame.feature.onboarding.model

import kotlinx.serialization.Serializable

@Serializable
data class OnboardingDraft(
    val formatVersion: Int = 1,
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val restaurantName: String = "",
    val currencyCode: String = "USD",
    val localeTag: String = "en-US",
    val selectedSuggestedAreaKeys: Set<String> = emptySet(),
    val customAreaNames: List<String> = emptyList(),
    val selectedSuggestedCategoryKeys: Set<String> = emptySet(),
    val customCategoryNames: List<String> = emptyList()
)
