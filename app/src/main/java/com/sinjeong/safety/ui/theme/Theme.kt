package com.sinjeong.safety.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── 서울교통공사 느낌의 파란색 팔레트 ────────────────────────────
object AppColors {
    val Primary = Color(0xFF1E3A8A)        // 진한 남색 (메인)
    val PrimaryLight = Color(0xFF3B5BDB)
    val MetroBlue = Color(0xFF0052A4)      // 공사 CI 블루
    val Background = Color(0xFFF4F6FB)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1A1C2E)
    val TextSecondary = Color(0xFF7B8194)
    val Divider = Color(0xFFE8EBF3)

    // 세부 태그 칩 색상
    val TagSafetyBg = Color(0xFFE3F2FD); val TagSafetyFg = Color(0xFF1565C0)   // 안전교육
    val TagOpsBg = Color(0xFFE8F5E9);    val TagOpsFg = Color(0xFF2E7D32)      // 운행지시
    val TagGeneralBg = Color(0xFFF3E5F5); val TagGeneralFg = Color(0xFF7B1FA2) // 일반전달

    // 카테고리 아이콘 색상
    val CatOrange = Color(0xFFF57C00)
    val CatBlue = Color(0xFF1976D2)
    val CatGreen = Color(0xFF388E3C)
    val CatYellow = Color(0xFFF9A825)

    val NewBadge = Color(0xFFE53935)
    val OnlineGreen = Color(0xFF34C759)
}

private val LightColors = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    secondary = AppColors.PrimaryLight,
    background = AppColors.Background,
    surface = AppColors.Surface,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
    error = AppColors.NewBadge
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun SinjeongSafetyTheme(content: @Composable () -> Unit) {
    // 사내 정보 앱 특성상 라이트 테마 고정 (가독성 우선)
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
