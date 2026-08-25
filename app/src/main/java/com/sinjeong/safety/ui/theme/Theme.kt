package com.sinjeong.safety.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── 서울교통공사 느낌의 파란색 팔레트 (라이트/다크 겸용) ──────────────
//
// 화면 수십 곳이 `AppColors.Primary` 처럼 정적 프로퍼티를 그대로 읽는다.
// 그래서 프로퍼티를 State 기반 게터로 바꿔 두면, 모드가 바뀌는 순간
// 그 색을 읽던 컴포저블이 알아서 다시 그려진다(화면 코드는 손대지 않음).
object AppColors {
    /** 0=시스템 따라감, 1=항상 라이트, 2=항상 다크 (기기별 저장) */
    private val modeState = mutableStateOf(0)

    /** 시스템 다크 여부 — SinjeongSafetyTheme 이 넣어준다 */
    private val systemDark = mutableStateOf(false)

    val isDark: Boolean
        get() = when (modeState.value) {
            1 -> false
            2 -> true
            else -> systemDark.value
        }

    var mode: Int
        get() = modeState.value
        set(v) { modeState.value = v }

    internal fun updateSystemDark(d: Boolean) { systemDark.value = d }

    val Primary: Color get() = if (isDark) Color(0xFF8FA8E8) else Color(0xFF1E3A8A)
    val PrimaryLight: Color get() = if (isDark) Color(0xFFA9BCF0) else Color(0xFF3B5BDB)
    val MetroBlue: Color get() = if (isDark) Color(0xFF6E9FE0) else Color(0xFF0052A4)
    val Background: Color get() = if (isDark) Color(0xFF121319) else Color(0xFFF4F6FB)
    val Surface: Color get() = if (isDark) Color(0xFF1C1E27) else Color(0xFFFFFFFF)
    val TextPrimary: Color get() = if (isDark) Color(0xFFE4E6F0) else Color(0xFF1A1C2E)
    val TextSecondary: Color get() = if (isDark) Color(0xFF9BA2B8) else Color(0xFF7B8194)
    val TextHint: Color get() = if (isDark) Color(0xFF6E748A) else Color(0xFFA6ABBA)
    val Divider: Color get() = if (isDark) Color(0xFF2E3140) else Color(0xFFE8EBF3)

    // 세부 태그 칩 색상 — 다크에서는 배경을 짙게, 글자를 밝게
    val TagSafetyBg: Color get() = if (isDark) Color(0xFF17304A) else Color(0xFFE3F2FD)
    val TagSafetyFg: Color get() = if (isDark) Color(0xFF8FC3F5) else Color(0xFF1565C0)
    val TagOpsBg: Color get() = if (isDark) Color(0xFF15331E) else Color(0xFFE8F5E9)
    val TagOpsFg: Color get() = if (isDark) Color(0xFF87D69A) else Color(0xFF2E7D32)
    val TagGeneralBg: Color get() = if (isDark) Color(0xFF2C1C39) else Color(0xFFF3E5F5)
    val TagGeneralFg: Color get() = if (isDark) Color(0xFFD5A6EE) else Color(0xFF7B1FA2)

    // 카테고리 아이콘 색상
    val CatOrange: Color get() = if (isDark) Color(0xFFFFB25E) else Color(0xFFF57C00)
    val CatBlue: Color get() = if (isDark) Color(0xFF6FA8E8) else Color(0xFF1976D2)
    val CatGreen: Color get() = if (isDark) Color(0xFF6FC97C) else Color(0xFF388E3C)
    val CatYellow: Color get() = if (isDark) Color(0xFFFFCF4D) else Color(0xFFF9A825)

    val NewBadge: Color get() = if (isDark) Color(0xFFFF8A80) else Color(0xFFE53935)
    val OnlineGreen: Color get() = if (isDark) Color(0xFF4ADE80) else Color(0xFF34C759)
}

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun SinjeongSafetyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // 저장된 선택을 앱 시작 시 한 번만 읽어 온다.
    remember {
        AppColors.mode = context
            .getSharedPreferences("safety_prefs", Context.MODE_PRIVATE)
            .getInt("dark_mode", 0)
    }

    val sysDark = isSystemInDarkTheme()
    SideEffect { AppColors.updateSystemDark(sysDark) }

    val dark = AppColors.isDark

    // 어두운 배경에서는 상태표시줄 아이콘을 밝게 (창을 못 가져오면 조용히 넘어간다)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            runCatching {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    // 화면 대부분이 AppColors 를 직접 쓰므로 여기서는 핵심 색만 맞춘다.
    val scheme = if (dark) {
        darkColorScheme(
            primary = AppColors.Primary,
            onPrimary = Color(0xFF10182E),
            secondary = AppColors.PrimaryLight,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onBackground = AppColors.TextPrimary,
            onSurface = AppColors.TextPrimary,
            error = AppColors.NewBadge
        )
    } else {
        lightColorScheme(
            primary = AppColors.Primary,
            onPrimary = Color.White,
            secondary = AppColors.PrimaryLight,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onBackground = AppColors.TextPrimary,
            onSurface = AppColors.TextPrimary,
            error = AppColors.NewBadge
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}
