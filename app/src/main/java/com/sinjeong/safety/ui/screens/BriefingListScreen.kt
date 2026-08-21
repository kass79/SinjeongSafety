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
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(briefings, key = { it.id }) { b ->
                        val open = expandedId == b.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AppColors.Surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
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
                                            "지적 ${b.items.size}건",
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
