package app.gabrielaraujo.gymtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = TC.Indigo500,
    onPrimary = TC.White,
    secondary = TC.Indigo400,
    background = TC.Background,
    onBackground = TC.White,
    surface = TC.Slate900,
    onSurface = TC.White,
    surfaceVariant = TC.Slate800,
    onSurfaceVariant = TC.Slate300,
    error = TC.Red400,
)

@Composable
fun GymTrackTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = GymTrackTypography,
        content = content,
    )
}
