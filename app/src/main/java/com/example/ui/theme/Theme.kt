package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

data class MamoExtendedColors(
    val isDark: Boolean,
    val gold: Color,
    val goldContainer: Color,
    val profit: Color,
    val profitContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val glassSurface: Color,
    val glassBorder: Color
)

val LocalMamoColors = staticCompositionLocalOf {
    MamoExtendedColors(
        isDark = false,
        gold = GoldAccent,
        goldContainer = GoldLight,
        profit = ProfitGreen,
        profitContainer = ProfitGreenContainerLight,
        loss = LossRed,
        lossContainer = LossRedContainerLight,
        warning = WarningAmber,
        warningContainer = WarningAmberContainerLight,
        glassSurface = Color(0xEBFFFFFF),
        glassBorder = LightBorder
    )
}

private val LightColorScheme = lightColorScheme(
    primary = NavyDeep,
    onPrimary = Color.White,
    primaryContainer = NavyRoyal,
    onPrimaryContainer = Color.White,
    secondary = GoldAccent,
    onSecondary = NavyDeep,
    secondaryContainer = GoldLight,
    onSecondaryContainer = NavyDeep,
    tertiary = NavySoft,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = LossRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = GoldBright,
    onPrimary = NavyDeep,
    primaryContainer = NavyRoyal,
    onPrimaryContainer = DarkTextPrimary,
    secondary = GoldAccent,
    onSecondary = NavyDeep,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = GoldBright,
    tertiary = NavySoft,
    onTertiary = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = LossRed,
    onError = Color.White
)

private val MamoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MamoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        MamoExtendedColors(
            isDark = true,
            gold = GoldBright,
            goldContainer = DarkSurfaceVariant,
            profit = ProfitGreen,
            profitContainer = ProfitGreenContainerDark,
            loss = LossRed,
            lossContainer = LossRedContainerDark,
            warning = WarningAmber,
            warningContainer = WarningAmberContainerDark,
            glassSurface = Color(0xD9101E34),
            glassBorder = Color(0x55E5B84B)
        )
    } else {
        MamoExtendedColors(
            isDark = false,
            gold = GoldAccent,
            goldContainer = GoldLight,
            profit = ProfitGreen,
            profitContainer = ProfitGreenContainerLight,
            loss = LossRed,
            lossContainer = LossRedContainerLight,
            warning = WarningAmber,
            warningContainer = WarningAmberContainerLight,
            glassSurface = Color(0xF0FFFFFF),
            glassBorder = LightBorder
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalMamoColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MamoTypography,
            shapes = MamoShapes,
            content = content
        )
    }
}
