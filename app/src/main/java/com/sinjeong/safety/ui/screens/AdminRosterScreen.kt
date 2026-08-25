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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.data.CrewRepository
import com.sinjeong.safety.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * 직원 명단 관리 (관리자 전용). 설정 > 관리 에서 들어온다.
 *
 * 명단 = assets/crew_ids.txt(APK 안 기본 명단) + config/roster(인사이동 델타).
 * 이 화면은 델타 문서만 고친다 — APK 안의 기본 명단은 앱을 새로 내야 바뀌므로 건드리지 않는다.
 * 이름은 config/rosterNames 에만 있고(관리자만 읽음), 저장소·APK 에는 절대 넣지 않는다.
 */
@Composable
fun AdminRosterScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    val repo = remember { CrewRepository() }
    val scope = rememberCoroutineScope()

    var active by remember { mutableStateOf<Set<String>?>(null) }   // null = 불러오는 중
    var removed by remember { mutableStateOf(emptySet<String>()) }
    var names by remember { mutableStateOf(emptyMap<String, String>()) }

    var showRetired by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var confirmRetire by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        active = null
        names = repo.rosterNames()
        removed = repo.removedIds() ?: emptySet()
        active = repo.effectiveRoster(context)
    }

    // 쓰기 한 번 + 목록 다시 읽기. 실패하면 사용자에게 그대로 보여준다(조용히 삼키지 않는다).
    fun perform(work: suspend () -> String) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            message = try {
                work()
            } catch (e: Exception) {
                "실패했습니다: ${e.localizedMessage}"
            }
            busy = false
            reload++
        }
    }

    val q = query.trim()
    val shown = (if (showRetired) removed else active.orEmpty())
        .filter { q.isBlank() || it.contains(q) || names[it].orEmpty().contains(q) }
        .sorted()

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
                    "직원 명단 관리",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    Modifier
                        .clickable { showAdd = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = AppColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "신입사원 등록",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                }
            }

            // ── 인원 수 + 재직/퇴직 전환 ───────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showRetired) "퇴직 ${removed.size}명"
                    else "재직 ${active?.size ?: 0}명",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                listOf("재직", "퇴직").forEachIndexed { i, label ->
                    val on = showRetired == (i == 1)
                    Surface(
                        color = if (on) AppColors.Primary else AppColors.Surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable { showRetired = i == 1 }
                    ) {
                        Text(
                            label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            // 다크에서는 Primary 가 밝은 톤이라 흰 글자가 묻힌다
                            color = when {
                                on && AppColors.isDark -> Color(0xFF10182E)
                                on -> Color.White
                                else -> AppColors.TextSecondary
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // ── 검색 (282명이라 검색 없이는 못 쓴다) ────────────
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("사번 또는 이름 검색", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.Surface,
                    unfocusedContainerColor = AppColors.Surface,
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = AppColors.Divider
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            message?.let {
                Text(
                    it,
                    fontSize = 13.sp,
                    color = AppColors.Primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            if (active == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
                return@Column
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (q.isBlank()) "명단이 비어 있습니다" else "'$q' 에 해당하는 직원이 없습니다",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary
                    )
                }
                return@Column
            }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                items(shown, key = { it }) { empNo ->
                    Surface(
                        color = AppColors.Surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                empNo,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.width(14.dp))
                            val name = names[empNo]
                            Text(
                                name ?: "이름 미등록",
                                fontSize = 14.sp,
                                color = if (name == null) AppColors.TextHint else AppColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (showRetired) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        perform {
                                            repo.unretireCrew(empNo)
                                            "$empNo 복귀 처리했습니다"
                                        }
                                    }
                                ) {
                                    Text(
                                        "복귀",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.Primary
                                    )
                                }
                            } else {
                                TextButton(
                                    enabled = !busy,
                                    onClick = { confirmRetire = empNo }
                                ) {
                                    Text(
                                        "퇴직 처리",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.NewBadge
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    // ── 신입사원 등록 ────────────────────────────────────────
    if (showAdd) {
        var newNo by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("") }
        val valid = newNo.length == 8 && newNo.all(Char::isDigit) && newName.isNotBlank()

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("신입사원 등록") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newNo,
                        onValueChange = { newNo = it.filter(Char::isDigit).take(8) },
                        label = { Text("사번 8자리") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("이름") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "이름은 관리자만 볼 수 있는 서버 명단에만 저장됩니다.",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid && !busy,
                    onClick = {
                        val no = newNo
                        val nm = newName.trim()
                        showAdd = false
                        perform {
                            if (repo.addCrew(context, no, nm)) "$nm($no) 등록했습니다"
                            else "이미 명단에 있습니다"
                        }
                    }
                ) { Text("등록", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("취소") }
            }
        )
    }

    // ── 퇴직 처리 확인 (되돌리기 번거로운 동작이라 한 번 묻는다) ──
    confirmRetire?.let { empNo ->
        AlertDialog(
            onDismissRequest = { confirmRetire = null },
            title = { Text("퇴직 처리할까요?") },
            text = {
                Text(
                    "${names[empNo] ?: "이름 미등록"}($empNo) 님을 명단에서 제외합니다.\n" +
                        "이 사번은 앱 로그인이 막히고, 확인 현황의 미확인자 집계에서도 빠집니다.\n" +
                        "잘못했다면 '퇴직' 탭에서 복귀시킬 수 있습니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRetire = null
                    perform {
                        repo.retireCrew(empNo)
                        "$empNo 퇴직 처리했습니다"
                    }
                }) {
                    Text("퇴직 처리", color = AppColors.NewBadge, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRetire = null }) { Text("취소") }
            }
        )
    }
}
