package com.sinjeong.safety.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 설정 화면.
 * 헤더 ⚙️ 아이콘으로 들어온다. 로그인하지 않은 상태에서도 열 수 있다.
 */
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {

    val context = LocalContext.current
    val crewName by vm.crewName.collectAsState()
    val crewEmpNo by vm.crewEmpNo.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val notifyOn by vm.notificationsEnabled.collectAsState()

    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 휴대폰 설정에서 알림을 껐는지 여부. 설정 앱에 다녀오면 다시 확인한다.
    var systemNotifyOn by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotifyOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ── 상단 바 ──────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "뒤로",
                    tint = AppColors.TextPrimary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onBack)
                )
                Spacer(Modifier.width(12.dp))
                Text("설정", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {

                // ── 내 정보 ──────────────────────────────────
                SectionTitle("내 정보")
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(AppColors.Primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when {
                                        isAdmin -> "관"
                                        !crewName.isNullOrBlank() -> crewName!!.take(1)
                                        else -> "?"
                                    },
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        isAdmin -> "관리자"
                                        !crewName.isNullOrBlank() -> "${crewName} 님"
                                        else -> "로그인하지 않음"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextPrimary
                                )
                                Text(
                                    crewEmpNo ?: if (isAdmin) "관리자 계정" else "사번 정보 없음",
                                    fontSize = 13.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }

                        if (crewEmpNo != null || isAdmin) {
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showLogoutConfirm = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Logout,
                                    contentDescription = null,
                                    tint = AppColors.NewBadge,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "로그아웃",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.NewBadge
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ── 알림 ─────────────────────────────────────
                SectionTitle("알림")
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "새 글 알림",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextPrimary
                                )
                                Text(
                                    "새 안전정보가 올라오면 알려드려요",
                                    fontSize = 12.5.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                            Switch(
                                checked = notifyOn,
                                onCheckedChange = { vm.setNotificationsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppColors.Primary
                                )
                            )
                        }

                        // 앱에서는 켰는데 휴대폰 설정에서 꺼둔 경우 안내
                        if (notifyOn && !systemNotifyOn) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E2)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "휴대폰 설정에서 이 앱의 알림이 꺼져 있어요. " +
                                            "그래서 알림이 오지 않습니다.",
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF7A4F00),
                                        lineHeight = 18.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { openAppNotificationSettings(context) }) {
                                        Text(
                                            "휴대폰 알림 설정 열기",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.Primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ── 앱 정보 ──────────────────────────────────
                SectionTitle("앱 정보")
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openPlayStore(context) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "앱 버전",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                "눌러서 최신 버전 확인하기",
                                fontSize = 12.5.sp,
                                color = AppColors.TextSecondary
                            )
                        }
                        Text(
                            appVersionName(context),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    "슬기로운 승무생활 · 신정승무사업소",
                    fontSize = 12.sp,
                    color = AppColors.TextHint,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                // 저작권 표기는 이웃 앱(신정승무캘린더)과 문구를 통일한다
                Text(
                    "© 2026 KANG SUNG JIN. ALL RIGHTS RESERVED.",
                    fontSize = 11.sp,
                    color = AppColors.TextHint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("로그아웃할까요?") },
            text = {
                Text(
                    if (isAdmin) "관리자 모드가 해제됩니다."
                    else "다시 보려면 사번과 PIN을 입력해야 해요."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    vm.crewSignOut()
                    onBack()
                }) { Text("로그아웃", color = AppColors.NewBadge, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** 설치된 앱의 버전 이름 (예: 1.0.2) */
private fun appVersionName(context: Context): String = try {
    "v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: Exception) {
    "-"
}

/** 휴대폰의 앱 알림 설정 화면 열기 */
private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + context.packageName))
    }
    runCatching { context.startActivity(intent) }
}

/** 플레이스토어 앱 페이지 열기 (스토어 앱이 없으면 웹으로) */
private fun openPlayStore(context: Context) {
    val pkg = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
    )
    if (runCatching { context.startActivity(market) }.isFailure) {
        runCatching { context.startActivity(web) }
    }
}
