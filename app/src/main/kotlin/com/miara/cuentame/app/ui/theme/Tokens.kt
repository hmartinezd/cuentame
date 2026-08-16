package com.miara.cuentame.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object AppElevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
}

@Immutable
data class SemanticColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val info: Color, val onInfo: Color, val infoContainer: Color, val onInfoContainer: Color,
    val selected: Color, val onSelected: Color, val elevatedSurface: Color, val divider: Color,
    val disabled: Color, val onDisabled: Color,
)

internal val LightSemanticColors = SemanticColors(
    Color(0xFF216C3B), Color.White, Color(0xFFA7F2B7), Color(0xFF00210B),
    Color(0xFF805600), Color.White, Color(0xFFFFDDA1), Color(0xFF281800),
    Color(0xFF245FA6), Color.White, Color(0xFFD5E3FF), Color(0xFF001B3D),
    Color(0xFFFFDAD1), Color(0xFF3B0900), Color.White, OutlineVariantLight,
    Color(0xFFE6E0DD), Color(0xFF8A817E),
)

internal val DarkSemanticColors = SemanticColors(
    Color(0xFF8DD59C), Color(0xFF003916), Color(0xFF075227), Color(0xFFA7F2B7),
    Color(0xFFFFBA46), Color(0xFF442B00), Color(0xFF604000), Color(0xFFFFDDA1),
    Color(0xFFA7C8FF), Color(0xFF003062), Color(0xFF064786), Color(0xFFD5E3FF),
    Color(0xFF67200F), Color(0xFFFFDAD1), Color(0xFF27211E), OutlineVariantDark,
    Color(0xFF342E2B), Color(0xFF827A76),
)
