package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.Question
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 질의응답 목록.
 *
 * 안전정보 피드와 분리된 화면이다. 글쓰기 버튼은 항상 보여주되,
 * 로그인 전이면 로그인 화면으로 보낸다 — 버튼을 숨기면 "왜 질문을 못 하지?"
 * 라는 문의가 오히려 늘기 때문이다.
 */
@Composable
fun QuestionListScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onQuestionClick: (String) -> Unit,
    onWriteClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val questions by vm.questions.collectAsState()
    val crewEmpNo by vm.crewEmpNo.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val loggedIn = crewEmpNo != null || isAdmin

    Scaffold(
        containerColor = AppColors.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (loggedIn) onWriteClick() else onLoginClick() },
                containerColor = AppColors.Primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "질문하기")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                Text("질의응답", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            }

            // 답변 여부로 거르는 칩. 관리자가 "답변대기"만 훑고 갈 수 있어야 한다.
            var filter by remember { mutableStateOf(0) }   // 0 전체 · 1 답변대기 · 2 답변완료
            val waiting = questions.count { it.answerCount == 0L }
            val answered = questions.size - waiting
            val shown = when (filter) {
                1 -> questions.filter { it.answerCount == 0L }
                2 -> questions.filter { it.answerCount > 0L }
                else -> questions
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { IntroCard() }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnswerFilterChip("전체 ${questions.size}", filter == 0) { filter = 0 }
                        AnswerFilterChip("답변대기 $waiting", filter == 1) { filter = 1 }
                        AnswerFilterChip("답변완료 $answered", filter == 2) { filter = 2 }
                    }
                }

                if (questions.isEmpty()) {
                    item { EmptyGuide() }
                } else if (shown.isEmpty()) {
                    // 질문은 있는데 이 필터에 걸리는 게 없는 경우 — 예시를 또 띄우면 헷갈린다
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("해당하는 질문이 없습니다", fontSize = 14.sp, color = AppColors.TextSecondary)
                        }
                    }
                } else {
                    items(shown, key = { it.id }) { q ->
                        QuestionCard(q) { onQuestionClick(q.id) }
                    }
                }
            }
        }
    }
}

/** 상단 안내 카드. 처음 들어온 사람이 "여기가 뭐 하는 곳인지" 한 번에 알게 한다. */
@Composable
private fun IntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "궁금한 걸 물어보세요",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "실명으로 올라갑니다. 답변은 누구나 달 수 있어요.",
                fontSize = 12.5.sp,
                color = AppColors.TextSecondary,
                lineHeight = 18.sp
            )
            Text(
                "규정 조문을 찾는 거라면 운전규정 → 물어보기가 빠릅니다.",
                fontSize = 12.5.sp,
                color = AppColors.TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

/** 답변 여부 필터 칩. material3 의 FilterChip 과 이름이 겹치지 않게 따로 둔다. */
@Composable
private fun AnswerFilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (on) AppColors.Primary else Color.White,
        border = BorderStroke(1.dp, if (on) AppColors.Primary else AppColors.Divider),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) Color.White else AppColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

/**
 * 작성자 이니셜 배지. 목록과 상세가 같은 모양을 써야 같은 사람이라는 게 눈에 들어온다.
 * 관리자 답변만 색을 뒤집어 눈에 띄게 한다.
 */
@Composable
fun AuthorInitial(name: String, admin: Boolean = false, size: Int = 38) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (admin) AppColors.Primary else AppColors.Divider),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).ifBlank { "?" },
            fontSize = (size / 2.4f).sp,
            fontWeight = FontWeight.Bold,
            color = if (admin) Color.White else AppColors.Primary
        )
    }
}

/** 질문이 하나도 없을 때. 빈 화면 대신 무엇을 물어봐도 되는지 예시로 보여준다. */
@Composable
private fun EmptyGuide() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            "아직 올라온 질문이 없습니다",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(18.dp))
        Text("이런 걸 물어볼 수 있어요", fontSize = 12.5.sp, color = AppColors.TextHint)
        Spacer(Modifier.height(8.dp))
        // 진짜 글이 아니라 예시다. 눌리지 않고 색도 흐리게 해서 오해를 막는다.
        listOf(
            "역행 불능 시 최초 조치가 뭔가요?",
            "차내 응급환자 발생하면 관제 보고 순서가 어떻게 되나요?",
            "휴게시간 중 대기 장소 규정이 어디에 있나요?"
        ).forEach { sample ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Divider.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    sample,
                    fontSize = 13.sp,
                    color = AppColors.TextHint,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "오른쪽 아래 연필 버튼으로 질문을 올릴 수 있습니다",
            fontSize = 12.sp,
            color = AppColors.TextHint
        )
    }
}

@Composable
private fun QuestionCard(q: Question, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            AuthorInitial(q.authorName)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    q.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    q.content,
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    buildString {
                        append(q.authorName.ifBlank { "이름 미등록" })
                        append(" · ").append(q.createdAt.toRelative())
                        append(" · 조회 ").append(q.views)
                        if (q.images.isNotEmpty()) append(" · 사진 ").append(q.images.size)
                    },
                    fontSize = 11.5.sp,
                    color = AppColors.TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            // 답변이 없으면 주황색으로 눈에 띄게 — 관리자가 먼저 보게 하려는 것
            val badgeColor = if (q.answerCount > 0) AppColors.Primary else AppColors.CatOrange
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.12f)
            ) {
                Text(
                    if (q.answerCount > 0) "답변 ${q.answerCount}" else "답변대기",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
