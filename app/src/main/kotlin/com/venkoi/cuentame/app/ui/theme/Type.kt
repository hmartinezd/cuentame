package com.venkoi.cuentame.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displaySmall = appTextStyle(FontWeight.SemiBold, 36, 44, -0.5f),
    headlineLarge = appTextStyle(FontWeight.SemiBold, 30, 38),
    headlineMedium = appTextStyle(FontWeight.SemiBold, 26, 34),
    headlineSmall = appTextStyle(FontWeight.SemiBold, 22, 30),
    titleLarge = appTextStyle(FontWeight.SemiBold, 20, 28),
    titleMedium = appTextStyle(FontWeight.SemiBold, 16, 24, 0.1f),
    titleSmall = appTextStyle(FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = appTextStyle(FontWeight.Normal, 16, 24, 0.15f),
    bodyMedium = appTextStyle(FontWeight.Normal, 14, 21, 0.15f),
    bodySmall = appTextStyle(FontWeight.Normal, 12, 18, 0.2f),
    labelLarge = appTextStyle(FontWeight.SemiBold, 14, 20, 0.1f),
    labelMedium = appTextStyle(FontWeight.Medium, 12, 17, 0.3f),
    labelSmall = appTextStyle(FontWeight.Medium, 11, 16, 0.4f),
)

private fun appTextStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

@Deprecated("Use AppTypography", ReplaceWith("AppTypography"))
val Typography = AppTypography
