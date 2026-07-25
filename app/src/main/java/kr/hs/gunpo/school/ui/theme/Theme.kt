package kr.hs.gunpo.school.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0D294F)
val SchoolBlue = Color(0xFF007AFF)
val SchoolGreen = Color(0xFF34C759)
val SchoolOrange = Color(0xFFFF9500)
val SchoolPurple = Color(0xFFAF52DE)
val GroupedBackground = Color(0xFFF2F2F7)
val GroupedCard = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = SchoolBlue,
    secondary = SchoolGreen,
    tertiary = SchoolOrange,
    background = GroupedBackground,
    surface = GroupedCard,
    surfaceVariant = Color(0xFFF2F2F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC2FF),
    secondary = Color(0xFF72DDB3),
    tertiary = Color(0xFFFFB77A),
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
)

@Composable
fun GunpoSchoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
