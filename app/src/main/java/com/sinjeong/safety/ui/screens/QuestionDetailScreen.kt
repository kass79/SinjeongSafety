package com.sinjeong.safety.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.Answer
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 질문 상세 + 답변.
 *
 * 답변은 하위 컬렉션이라 목록 화면의 questions 로는 알 수 없다.
 * 이 화면에서만 answersFlow 를 구독한다(들어올 때 구독, 나가면 끊긴다).
 */
@Composable
fun QuestionDetailScreen(
    vm: MainViewModel,
    questionId: String,
    onBack: () -> Unit,
    onLoginClick: () -> Unit
) {
    val questions by vm.questions.collectAsState()
    val question = questions.firstOrNull { it.id == questionId }
    val isAdmin by vm.isAdmin.collectAsState()
    val crewEmpNo by vm.crewEmpNo.collectAsState()
    val loggedIn = crewEmpNo != null || isAdmin

    // questionId 가 바뀌면 새로 구독한다. 매 재구성마다 만들면 리스너가 계속 새로 붙는다.
    val flow = remember(questionId) { vm.answersFlow(questionId) }
    val answersResult by flow.collectAsState(initial = Result.success(emptyList()))
    val answers = answersResult.getOrDefault(emptyList())

    var input by remember { mutableStateOf("") }
    var showDeleteQuestion by remember { mutableStateOf(false) }

    LaunchedEffect(questionId) { vm.viewQuestion(questionId) }

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
                    "질의응답",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (isAdmin) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "질문 삭제",
                        tint = AppColors.NewBadge,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showDeleteQuestion = true }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column {
                        Text(
                            question?.title.orEmpty(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                question?.authorName?.ifBlank { "이름 미등록" } ?: "",
                                fontSize = 12.5.sp,
                                color = AppColors.TextSecondary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                question?.createdAt.toRelative(),
                                fontSize = 12.5.sp,
                                color = AppColors.TextHint
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            question?.content.orEmpty(),
                            fontSize = 15.sp,
                            color = AppColors.TextPrimary,
                            lineHeight = 24.sp
                        )
                    }
                }

                // 사진은 세로로 쭉 — 현장 사진은 잘리면 못 알아보기 때문에 Crop 하지 않는다
                items(question?.images ?: emptyList()) { img ->
                    AsyncImage(
                        model = img.url,
                        contentDescription = img.name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "답변 ${answers.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = AppColors.Divider)
                }

                items(answers, key = { it.id }) { a ->
                    AnswerRow(
                        answer = a,
                        canDelete = isAdmin,
                        onDelete = { vm.deleteAnswer(questionId, a.id) }
                    )
                }
            }

            // ── 맨 아래 고정 입력줄 ──────────────────────────
            if (loggedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("답변을 입력하세요", color = AppColors.TextHint, fontSize = 14.sp) },
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = AppColors.Divider
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) vm.addAnswer(questionId, input) { input = "" }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = "답변 등록",
                            tint = if (input.isNotBlank()) AppColors.Primary else AppColors.TextHint
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface)
                        .clickable(onClick = onLoginClick)
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "로그인하면 답변할 수 있습니다",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                }
            }
        }
    }

    if (showDeleteQuestion) {
        AlertDialog(
            onDismissRequest = { showDeleteQuestion = false },
            title = { Text("이 질문을 삭제할까요?") },
            text = { Text("답변도 함께 지워지고 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteQuestion = false
                    vm.deleteQuestion(questionId) { onBack() }
                }) { Text("삭제", color = AppColors.NewBadge, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteQuestion = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun AnswerRow(answer: Answer, canDelete: Boolean, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    answer.authorName.ifBlank { "이름 미등록" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                if (answer.isAdmin) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = AppColors.Primary) {
                        Text(
                            "관리자",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(answer.createdAt.toRelative(), fontSize = 11.5.sp, color = AppColors.TextHint)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                answer.content,
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                lineHeight = 21.sp
            )
        }
        if (canDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "답변 삭제",
                tint = AppColors.TextHint,
                modifier = Modifier
                    .padding(start = 8.dp, top = 2.dp)
                    .size(18.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}
