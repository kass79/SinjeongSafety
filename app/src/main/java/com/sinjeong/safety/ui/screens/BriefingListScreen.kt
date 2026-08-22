package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 지난 출무점호.
 *
 * 탭하면 그 자리에서 펼친다 — 상세 화면을 따로 만들 만큼 내용이 길지 않고,
 * 화면 하나를 아끼는 편이 뒤로가기 단계도 줄여준다.
 */
@Composable
fun BriefingListScreen(vm: MainViewModel, onBack: () -> Unit) {

    val briefings by vm.briefings.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    var expandedId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    // 점호 날짜(dateText)와 실제로 올린 날짜는 다를 수 있다 — 지난 자료를 뒤늦게
    // 올리는 경우가 있어서, 관리자가 그 차이를 알 수 있게 등록 시각을 따로 보여준다.
    val stampFmt = remember { SimpleDateFormat("M/d HH:mm", Locale.KOREA) }
    val dayFmt = remember { SimpleDateFormat("yyyyMMdd", Locale.KOREA) }
    val today = remember { dayFmt.format(Date()) }

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
                    "지난 출무점호",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            }

            if (briefings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("아직 등록된 출무점호가 없습니다", color = AppColors.TextSecondary)
                }
            } else {
                // 문서 id(yyyyMMdd) 앞 6자리로 월 그룹. 형식이 어긋난 id는 "기타"로 몰아 방어한다.
                // briefings 가 이미 최신순이라 groupBy(LinkedHashMap)의 순서를 그대로 쓴다.
                val byMonth = briefings.groupBy { b ->
                    if (b.id.length == 8 && b.id.all { c -> c.isDigit() }) b.id.take(6) else "기타"
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    byMonth.forEach { (ym, group) ->
                        item(key = "hdr_$ym") { MonthHeader(ym, group.size) }
                        items(group, key = { it.id }) { b ->
                            val open = expandedId == b.id
                            val stamp = b.createdAt?.let { stampFmt.format(it.toDate()) }
                            // 오늘 올린 것만 테두리로 살짝 표시 — 그 이상 강조하지 않는다.
                            val isToday = b.createdAt?.let { dayFmt.format(it.toDate()) == today } == true
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = AppColors.Surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isToday) AppColors.TagOpsFg.copy(alpha = 0.35f) else AppColors.Divider
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clickable { expandedId = if (open) null else b.id }
                                        ) {
                                            Text(
                                                b.dateText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                // createdAt 이 없는 옛 문서는 건수만 (등록 부분 생략)
                                                if (stamp != null) "지적 ${b.items.size}건 · $stamp 등록"
                                                else "지적 ${b.items.size}건",
                                                fontSize = 12.sp,
                                                color = AppColors.TextSecondary
                                            )
                                        }
                                        if (isAdmin) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "삭제",
                                                tint = AppColors.TextSecondary,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable { deleteTarget = b.id }
                                            )
                                        }
                                    }
    
                                    if (open) {
                                        Spacer(Modifier.height(10.dp))
                                        // 홈 카드와 같은 모양이어야 해서 같은 조각을 쓴다(HomeScreen.kt)
                                        b.items.forEach { BriefingItemRow(it) }
                                        BriefingExtras(b)
                                        if (b.footer.isNotBlank()) {
                                            Spacer(Modifier.height(9.dp))
                                            HorizontalDivider(color = AppColors.Divider)
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                b.footer,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.Primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("출무점호 삭제") },
            text = { Text("이 날짜의 출무점호를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBriefing(id) { }
                    deleteTarget = null
                }) { Text("삭제", color = AppColors.NewBadge) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            }
        )
    }
}

/**
 * 월 구분 헤더. 목록을 훑을 때 경계만 보이면 되므로 배경 없이 가볍게 둔다.
 */
@Composable
private fun MonthHeader(ym: String, count: Int) {
    val label = if (ym.length == 6) "${ym.take(4)}년 ${ym.substring(4).toInt()}월" else "기타"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text("${count}건", fontSize = 12.sp, color = AppColors.TextSecondary)
    }
}
