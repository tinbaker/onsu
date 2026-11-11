package com.onsu.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Lựa chọn giao diện trong Cài đặt. */
enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * Bộ màu của một giao diện. Tách khỏi `object MixtapeTokens` để đổi theme không
 * phải sửa hàng trăm chỗ gọi `MixtapeTokens.X`.
 */
data class MixtapePalette(
    val isDark: Boolean,
    val bgBase: Color,
    val bgSurface: Color,
    val bgRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accentPrimary: Color,
    val sliderTrack: Color,
    val stateSuccess: Color,
    val stateWarn: Color,
    val stateDanger: Color,
    val sourceLocal: Color,
    val sourceOnline: Color
)

val DarkPalette = MixtapePalette(
    isDark = true,
    bgBase = Color(0xFF0B0B0F),
    bgSurface = Color(0xFF15151C),
    bgRaised = Color(0xFF1F1F29),
    textPrimary = Color(0xFFF2F2F5),
    textSecondary = Color(0xFFA0A0AE),
    textDisabled = Color(0xFF5A5A66),
    accentPrimary = Color(0xFF7C5CFF),
    sliderTrack = Color(0xFF3A3A47),
    stateSuccess = Color(0xFF3DD68C),
    stateWarn = Color(0xFFFFB020),
    stateDanger = Color(0xFFFF5C5C),
    sourceLocal = Color(0xFF3DD68C),
    sourceOnline = Color(0xFF5C9DFF)
)

val LightPalette = MixtapePalette(
    isDark = false,
    bgBase = Color(0xFFF6F6FA),
    bgSurface = Color(0xFFFFFFFF),
    bgRaised = Color(0xFFEDEDF4),
    textPrimary = Color(0xFF14141A),
    textSecondary = Color(0xFF5E5E6E),
    textDisabled = Color(0xFF9C9CAA),
    accentPrimary = Color(0xFF6244E8),
    sliderTrack = Color(0xFFD8D8E2),
    stateSuccess = Color(0xFF0E9F68),
    stateWarn = Color(0xFFB87300),
    stateDanger = Color(0xFFD93B3B),
    sourceLocal = Color(0xFF0E9F68),
    sourceOnline = Color(0xFF2F6FE0)
)

val LocalMixtapePalette = staticCompositionLocalOf { DarkPalette }

/**
 * Điểm truy cập màu duy nhất cho toàn app.
 *
 * Các màu là getter đọc `LocalMixtapePalette`, nên đổi theme sẽ recompose đúng chỗ
 * mà không cần khởi động lại Activity.
 */
object MixtapeTokens {
    val BgBase: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.bgBase
    val BgSurface: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.bgSurface
    val BgRaised: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.bgRaised
    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.textPrimary
    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.textSecondary
    val TextDisabled: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.textDisabled
    val AccentPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.accentPrimary
    val SliderThumb: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.accentPrimary
    val SliderTrack: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.sliderTrack
    val StateSuccess: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.stateSuccess
    val StateWarn: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.stateWarn
    val StateDanger: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.stateDanger
    val SourceLocal: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.sourceLocal
    val SourceOnline: Color
        @Composable @ReadOnlyComposable get() = LocalMixtapePalette.current.sourceOnline

    val Space1: Dp = 4.dp
    val Space2: Dp = 8.dp
    val Space3: Dp = 12.dp
    val Space4: Dp = 16.dp
    val Space6: Dp = 24.dp
    val Space8: Dp = 32.dp

    val MinTouchTarget: Dp = 48.dp
    val TrackRowHeight: Dp = 64.dp
    val MiniPlayerHeight: Dp = 64.dp
}

private fun typographyFor(palette: MixtapePalette) = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = palette.textPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = palette.textPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = palette.textPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = palette.textSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = palette.textSecondary
    )
)

private val DarkTypography = typographyFor(DarkPalette)
private val LightTypography = typographyFor(LightPalette)

val MixtapeTypography: Typography
    @Composable @ReadOnlyComposable
    get() = if (LocalMixtapePalette.current.isDark) DarkTypography else LightTypography

val MixtapeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.accentPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkPalette.bgRaised,
    onPrimaryContainer = DarkPalette.textPrimary,
    background = DarkPalette.bgBase,
    onBackground = DarkPalette.textPrimary,
    surface = DarkPalette.bgSurface,
    onSurface = DarkPalette.textPrimary,
    surfaceVariant = DarkPalette.bgRaised,
    onSurfaceVariant = DarkPalette.textSecondary,
    outline = DarkPalette.sliderTrack,
    error = DarkPalette.stateDanger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.accentPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPalette.bgRaised,
    onPrimaryContainer = LightPalette.textPrimary,
    background = LightPalette.bgBase,
    onBackground = LightPalette.textPrimary,
    surface = LightPalette.bgSurface,
    onSurface = LightPalette.textPrimary,
    surfaceVariant = LightPalette.bgRaised,
    onSurfaceVariant = LightPalette.textSecondary,
    outline = LightPalette.sliderTrack,
    error = LightPalette.stateDanger,
    onError = Color.White
)

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun MixtapeTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val palette = if (isDark) DarkPalette else LightPalette

    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                // Nền sáng cần icon thanh trạng thái màu tối, và ngược lại.
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalMixtapePalette provides palette) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography = MixtapeTypography,
            shapes = MixtapeShapes,
            content = content
        )
    }
}
