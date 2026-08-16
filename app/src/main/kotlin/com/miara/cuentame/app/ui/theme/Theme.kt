package com.miara.cuentame.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark, onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark, onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondary = BrandSecondaryDark, onSecondary = BrandOnSecondaryDark,
    secondaryContainer = BrandSecondaryContainerDark, onSecondaryContainer = BrandOnSecondaryContainerDark,
    background = BackgroundDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark, outlineVariant = OutlineVariantDark,
    error = ErrorDark, onError = BrandOnPrimaryDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
    surfaceTint = BrandPrimaryDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight, onPrimary = BrandOnPrimaryLight,
    primaryContainer = BrandPrimaryContainerLight, onPrimaryContainer = BrandOnPrimaryContainerLight,
    secondary = BrandSecondaryLight, onSecondary = BrandOnSecondaryLight,
    secondaryContainer = BrandSecondaryContainerLight, onSecondaryContainer = BrandOnSecondaryContainerLight,
    background = BackgroundLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight,
    error = ErrorLight, onError = BrandOnPrimaryLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
    surfaceTint = BrandPrimaryLight,
)

private val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object AppTheme {
    val semanticColors: SemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/** Temporary source-compatible bridge while callers migrate to the neutral name. */
@Deprecated("Use AppTheme", ReplaceWith("AppTheme(darkTheme, dynamicColor, content)"))
@Composable
fun CuentameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) = AppTheme(darkTheme, dynamicColor, content)
