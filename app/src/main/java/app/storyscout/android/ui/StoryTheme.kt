package app.storyscout.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StoryPrimary = Color(0xFF111111)
val StorySecondary = Color(0xFF686868)
val StoryError = Color(0xFFA31919)
val StoryButton = Color(0xFFEEEEEE)

private val colors = lightColorScheme(
    primary = StoryPrimary, onPrimary = Color.White, background = Color.White,
    onBackground = StoryPrimary, surface = Color.White, onSurface = StoryPrimary,
    error = StoryError, onError = Color.White,
)

@Composable
fun StoryTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
