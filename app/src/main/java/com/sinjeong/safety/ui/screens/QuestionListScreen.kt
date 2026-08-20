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
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
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

            if (questions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "아직 올라온 질문이 없습니다",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(questions, key = { it.id }) { q ->
                        QuestionCard(q) { onQuestionClick(q.id) }
                    }
                }
            }
        }
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
        Column(Modifier.padding(16.dp)) {
            Text(
                q.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                q.content,
                fontSize = 13.sp,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    q.authorName.ifBlank { "이름 미등록" },
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.width(6.dp))
                Text(q.createdAt.toRelative(), fontSize = 12.sp, color = AppColors.TextHint)
                Spacer(Modifier.width(6.dp))
                if (q.images.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = "사진 있음",
                        tint = AppColors.TextHint,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                // 답변이 없으면 주황색으로 눈에 띄게 — 관리자가 먼저 보게 하려는 것
                if (q.answerCount > 0) {
                    Text(
                        "답변 ${q.answerCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                } else {
                    Text(
                        "답변 대기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.CatOrange
                    )
                }
            }
        }
    }
}

/** 홈 화면 진입 배너. 규정에 물어보기 배너와 같은 모양·여백을 쓴다. */
@Composable
fun QuestionEntryBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFEEF3FF),
        border = BorderStroke(1.dp, Color(0xFFD5DEF6)),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("승무원 질의응답", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.Primary)
                Spacer(Modifier.height(2.dp))
                Text("궁금한 걸 묻고 서로 답한다",
                    fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 16.sp)
            }
            Text("›", fontSize = 17.sp, color = AppColors.Primary)
        }
    }
}
