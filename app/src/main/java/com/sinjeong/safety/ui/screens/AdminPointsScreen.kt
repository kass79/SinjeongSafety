package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.data.CrewPoints
import com.sinjeong.safety.data.PointsRepository
import com.sinjeong.safety.ui.theme.AppColors
import java.time.YearMonth

/**
 * 직원 포인트 현황 (관리자 전용).
 * 설정 화면에서 들어온다. 승무원 계정은 confirms 를 읽을 권한이 없어 애초에 항목이 안 보인다.
 *
 * 점수: 확인 1 / 퀴즈 정답 1 / 댓글 1 / 답변 2.
 */
@Composable
fun AdminPointsScreen(onBack: () -> Unit) {

    val repo = remember { PointsRepository() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var rows by remember { mutableStateOf<List<CrewPoints>?>(null) }

    // 달을 바꿀 때마다 다시 센다. 실시간으로 볼 화면이 아니라 한 번씩만 읽는다.
    LaunchedEffect(month) {
        rows = null
        rows = repo.monthlyPoints(month)
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
                Text(
                    "직원 포인트 현황",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            }

            // ── 월 이동 ──────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "이전 달",
                    tint = AppColors.Primary,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable { month = month.minusMonths(1) }
                )
                Text(
                    "${month.year}년 ${month.monthValue}월",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                // 다음 달은 아직 오지 않았으니 이번 달을 넘어가지 못하게 막는다.
                val canForward = month < YearMonth.now()
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "다음 달",
                    tint = if (canForward) AppColors.Primary else AppColors.TextHint,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(enabled = canForward) { month = month.plusMonths(1) }
                )
            }

            val data = rows
            if (data == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
                return@Column
            }
            if (data.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "이번 달 활동 기록이 없습니다",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary
                    )
                }
                return@Column
            }

            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "확인 1점 · 퀴즈 정답 1점 · 댓글 1점 · 답변 2점",
                    fontSize = 11.5.sp,
                    color = AppColors.TextHint,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Surface(color = AppColors.Surface, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        HeadCell("순위", 0.7f)
                        HeadCell("사번", 1.6f)
                        HeadCell("이름", 1.2f)
                        HeadCell("확인", 0.8f)
                        HeadCell("퀴즈", 0.8f)
                        HeadCell("댓글", 0.8f)
                        HeadCell("답변", 0.8f)
                        HeadCell("합계", 0.9f)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                itemsIndexed(data, key = { _, r -> r.empNo }) { index, row ->
                    PointsRow(rank = index + 1, row = row)
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeadCell(text: String, weight: Float) {
    Text(
        text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun PointsRow(rank: Int, row: CrewPoints) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cell("$rank", 0.7f, bold = rank <= 3, color = if (rank <= 3) AppColors.Primary else null)
        Cell(row.empNo, 1.6f)
        Cell(row.name, 1.2f)
        Cell("${row.confirms}", 0.8f)
        Cell("${row.quiz}", 0.8f)
        Cell("${row.comments}", 0.8f)
        Cell("${row.answers}", 0.8f)
        Cell("${row.total}", 0.9f, bold = true, color = AppColors.Primary)
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    color: Color? = null
) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color ?: AppColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight)
    )
}
