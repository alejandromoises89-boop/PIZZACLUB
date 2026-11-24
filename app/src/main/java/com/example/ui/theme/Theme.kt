package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TomatoPrimary,
    onPrimary = PureWhite,
    primaryContainer = TomatoContainer,
    onPrimaryContainer = OnTomatoContainer,
    secondary = CheeseSecondary,
    onSecondary = EspressoDark,
    secondaryContainer = CheeseContainer,
    onSecondaryContainer = OnCheeseContainer,
    tertiary = WhatsAppGreen,
    onTertiary = PureWhite,
    tertiaryContainer = WhatsAppGreenContainer,
    onTertiaryContainer = ColorTokens.DarkGreen,
    background = FlourBackground,
    onBackground = EspressoDark,
    surface = PureWhite,
    onSurface = EspressoDark,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmMutedText,
    outline = WarmOutline,
    error = DangerRed,
    onError = PureWhite,
    errorContainer = DangerRedContainer,
    onErrorContainer = DangerRed
)

private val DarkColorScheme = darkColorScheme(
    primary = TomatoPrimary,
    onPrimary = PureWhite,
    primaryContainer = TomatoPrimaryDark,
    onPrimaryContainer = PureWhite,
    secondary = CheeseSecondary,
    onSecondary = EspressoDark,
    secondaryContainer = OnCheeseContainer,
    onSecondaryContainer = CheeseContainer,
    tertiary = WhatsAppGreen,
    onTertiary = PureWhite,
    background = DarkOvenBackground,
    onBackground = FlourBackground,
    surface = DarkOvenSurface,
    onSurface = FlourBackground,
    surfaceVariant = DarkOvenSurfaceVariant,
    onSurfaceVariant = WarmOutline,
    outline = WarmMutedText,
    error = DangerRed,
    onError = PureWhite
)

private object ColorTokens {
    val DarkGreen = androidx.compose.ui.graphics.Color(0xFF0E4B27)
}

val PizzaClubShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PizzaClubTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PizzaClubTypography,
        shapes = PizzaClubShapes,
        content = content
    )
}
