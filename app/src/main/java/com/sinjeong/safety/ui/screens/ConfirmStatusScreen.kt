package com.sinjeong.safety.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.ConfirmReport
import com.sinjeong.safety.data.CrewConfirm
import com.sinjeong.safety.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 확인 현황 (관리자 전용).
 * 게시물 상세에서 "N명 확인 ›" 을 누르면 들어온다.
 * 승무원 계정은 confirms 하위 컬렉션을 읽을 권한이 없어 목록이 비어 보인다(규칙에서 막는다).
 */
@Composable
fun ConfirmStatusScreen(vm: MainViewModel, postId: String, onBack: () -> Unit) {

    val posts by vm.posts.collectAsState()
    val title = posts.firstOrNull { it.id == postId }?.title

    var report by remember { mutableStateOf<ConfirmReport?>(null) }
    // 한 번만 불러온다. 실시간으로 바뀔 화면이 아니고, 명단 조회가 비싸다.
    LaunchedEffect(postId) { vm.loadConfirmReport(postId) { report = it } }

    var showPending by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ── 상단 바 (설정 화면과 같은 모양) ──────────────
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
                Text("확인 현황", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)

                Spacer(Modifier.weight(1f))

                // 명단을 다 받아 오기 전에는 회색으로 두고 눌러도 아무 일 없게 한다.
                val ready = report
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = "엑셀로 내보내기",
                    tint = if (ready == null) AppColors.TextHint else AppColors.Primary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(enabled = ready != null) {
                            if (ready != null) shareConfirmCsv(context, title.orEmpty(), ready)
                        }
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Outlined.Print,
                    contentDescription = "인쇄 / PDF 저장",
                    tint = if (ready == null) AppColors.TextHint else AppColors.Primary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(enabled = ready != null) {
                            if (ready != null) printConfirmRoster(context, title.orEmpty(), ready)
                        }
                )
            }

            val data = report
            if (data == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
                return@Column
            }

            Column(Modifier.fillMaxWidth().padding(16.dp)) {

                if (!title.isNullOrBlank()) {
                    Surface(color = AppColors.Surface, shape = RoundedCornerShape(14.dp)) {
                        Text(
                            title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.fillMaxWidth().padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── 요약 ────────────────────────────────────
                val done = data.confirmed.size
                val total = data.total
                val percent = if (total > 0) done * 100 / total else 0
                Surface(color = AppColors.Surface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "확인 ${done}명 / 전체 ${total}명",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$percent%", fontSize = 14.sp, color = AppColors.Primary, fontWeight = FontWeight.Bold)
                        }
                        // 퀴즈를 푼 사람이 한 명이라도 있으면 평균 정답률을 한 줄 덧붙인다.
                        // 사람별 평균이 아니라 전체 문항 기준이다(문항 수가 글마다 다를 수 있어서).
                        val takers = data.confirmed.filter { (it.quizTotal ?: 0) > 0 }
                        if (takers.isNotEmpty()) {
                            val asked = takers.sumOf { it.quizTotal ?: 0 }
                            val got = takers.sumOf { it.quizCorrect ?: 0 }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "퀴즈 평균 정답률 ${got * 100 / asked}% (${takers.size}명 응시)",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary
                            )
                        }
                        if (data.anonymous > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "이름이 안 남은 옛 기록 ${data.anonymous}명 (앱 업데이트 전에 누른 것)",
                                fontSize = 11.sp,
                                color = AppColors.TextHint
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 탭 (TabRow는 실험적 API라 칩 두 개로 대신한다) ──
                Row(Modifier.fillMaxWidth()) {
                    StatusTab("확인 $done", !showPending, Modifier.weight(1f)) { showPending = false }
                    Spacer(Modifier.width(8.dp))
                    StatusTab("미확인 ${data.pending.size}", showPending, Modifier.weight(1f)) { showPending = true }
                }
            }

            val list = if (showPending) data.pending else data.confirmed
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (showPending) "모두 확인했습니다" else "아직 확인한 사람이 없습니다",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(list, key = { it.empNo }) { crew ->
                        CrewConfirmRow(crew, showTime = !showPending)
                    }
                }
            }
        }
    }
}

/** 확인/미확인 전환 칩 */
@Composable
private fun StatusTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) AppColors.Primary else AppColors.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else AppColors.TextSecondary
            )
        }
    }
}

private val timeFormat = SimpleDateFormat("M/d HH:mm", Locale.KOREA)

/** 한 사람 한 줄. 이름을 등록 안 한 사람도 사번으로 알아볼 수 있게 둘 다 보여 준다. */
@Composable
private fun CrewConfirmRow(crew: CrewConfirm, showTime: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(AppColors.Divider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (crew.name.isBlank()) "?" else crew.name.take(1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (crew.name.isBlank()) "이름 미등록" else crew.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (crew.name.isBlank()) AppColors.TextHint else AppColors.TextPrimary
            )
            Text(crew.empNo, fontSize = 11.sp, color = AppColors.TextSecondary)
        }
        // 퀴즈를 푼 사람만. null 은 "안 풀었다"라서 0점(다 틀림)과 구분된다.
        crew.quizTotal?.let { total ->
            val got = crew.quizCorrect ?: 0
            Text(
                "퀴즈 $got/$total",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (got == total) AppColors.TagOpsFg else AppColors.CatOrange
            )
            Spacer(Modifier.width(10.dp))
        }
        if (showTime) {
            Text(
                crew.at?.let { timeFormat.format(it.toDate()) } ?: "",
                fontSize = 11.sp,
                color = AppColors.TextHint
            )
        }
    }
}
