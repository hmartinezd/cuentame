package com.miara.cuentame.feature.onboarding.model

import kotlinx.serialization.Serializable

@Serializable
data class OnboardingItemDraft(
    val id: String,
    val templateKey: String? = null,
    val customName: String? = null,
    val isSelected: Boolean,
    val sortOrder: Int
)

@Serializable
data class OnboardingDraft(
    val formatVersion: Int = 2,
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val restaurantName: String = "",
    val currencyCode: String = "USD",
    val localeTag: String = "en-US",
    val areas: List<OnboardingItemDraft> = emptyList(),
    val categories: List<OnboardingItemDraft> = emptyList()
)
