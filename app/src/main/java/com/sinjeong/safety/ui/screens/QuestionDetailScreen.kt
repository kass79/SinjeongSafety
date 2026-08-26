package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.style.TextOverflow
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
    // 답글 대상. null 이면 원답변을 쓰는 중이다. 깊이는 1단계까지만이라
    // 여기 담기는 건 언제나 원답변이다(답글에는 답글 버튼을 두지 않는다).
    var replyTo by remember { mutableStateOf<Answer?>(null) }

    // 원답변 아래에 그 답글을 붙여서 한 줄로 편다. Firestore 는 평평한 한 컬렉션이라
    // (createdAt 오름차순) 이 조립은 화면에서 한다. Boolean 은 "답글인가".
    // parentId 가 없는 옛 답변 문서는 기본값이 빈 문자열이라 전부 원답변으로 들어온다.
    val threaded: List<Pair<Answer, Boolean>> = run {
        val roots = answers.filter { it.parentId.isBlank() }
        val rootIds = roots.map { it.id }.toSet()
        val repliesOf = answers.filter { it.parentId.isNotBlank() }.groupBy { it.parentId }
        buildList {
            roots.forEach { root ->
                add(root to false)
                repliesOf[root.id].orEmpty().forEach { add(it to true) }
            }
            // 부모가 지워진 답글은 어느 원답변에도 못 붙는다. 그대로 두면 화면에서
            // 사라지므로(내용은 남아 있는데 안 보인다) 맨 아래에 원답변처럼 그린다.
            answers.filter { it.parentId.isNotBlank() && it.parentId !in rootIds }
                .forEach { add(it to false) }
        }
    }

    LaunchedEffect(questionId) { vm.viewQuestion(questionId) }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        // 키보드가 뜨면 이 화면 전체를 그만큼 줄인다. 안 줄이면 맨 아래 답변 입력줄이
        // 키보드에 그대로 덮인다 — targetSdk 35+ 의 edge-to-edge 강제로 매니페스트
        // adjustResize 가 더는 창을 줄여 주지 않기 때문이다.
        // 가운데 목록이 weight(1f) 라 줄어든 만큼을 목록이 흡수하고 입력줄은 키보드 위에 남는다.
        Column(Modifier.fillMaxSize().imePadding()) {

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
                            Spacer(Modifier.width(6.dp))
                            // 조회수 — 목록 카드와 같은 정보를 상세에서도 볼 수 있게
                            Text(
                                "조회 ${question?.views ?: 0}",
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
                    // 답변이 없으면 구분선 아래가 텅 비어 고장난 것처럼 보인다
                    if (answers.isEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "아직 답변이 없습니다. 아는 분이 답을 달아주세요.",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                items(threaded, key = { it.first.id }) { (a, isReply) ->
                    AnswerRow(
                        answer = a,
                        canDelete = isAdmin,
                        onDelete = { vm.deleteAnswer(questionId, a.id) },
                        // 1단계 깊이 — 답글에는 답글을 달지 않는다
                        onReply = if (loggedIn && !isReply) ({ replyTo = a }) else null,
                        isReply = isReply
                    )
                }
            }

            // ── 맨 아래 고정 입력줄 ──────────────────────────
            // 화면 전체가 이미 imePadding 을 쓰므로 여기서 또 주지 않는다(이중 패딩).
            if (loggedIn) {
                Column(Modifier.fillMaxWidth().background(AppColors.Surface)) {
                    // 답글 대상 표시줄 — 지금 누구에게 쓰는 중인지 보이지 않으면
                    // 답글 버튼을 눌러 놓고도 원답변처럼 느껴진다.
                    replyTo?.let { target ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(AppColors.Background)
                                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${target.authorName.ifBlank { "이름 미등록" }} 님에게 답글",
                                fontSize = 12.5.sp,
                                color = AppColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "✕",
                                fontSize = 14.sp,
                                color = AppColors.TextSecondary,
                                modifier = Modifier
                                    .clickable { replyTo = null }
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = {
                                Text(
                                    if (replyTo != null) "답글을 입력하세요" else "답변을 입력하세요",
                                    color = AppColors.TextHint, fontSize = 14.sp
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AppColors.Surface,
                                unfocusedContainerColor = AppColors.Surface,
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = AppColors.Divider
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (input.isNotBlank())
                                    vm.addAnswer(questionId, input, replyTo?.id ?: "") {
                                        input = ""
                                        replyTo = null
                                    }
                            },
                            enabled = input.isNotBlank()
                        ) {
                            Icon(
                                Icons.Outlined.Send,
                                contentDescription = if (replyTo != null) "답글 등록" else "답변 등록",
                                tint = if (input.isNotBlank()) AppColors.Primary else AppColors.TextHint
                            )
                        }
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
private fun AnswerRow(
    answer: Answer,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onReply: (() -> Unit)? = null,
    isReply: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 34.dp else 0.dp)
            .padding(vertical = 6.dp)
            // 세로 구분선이 줄 높이만큼 늘어나도록. 답글이 아닐 땐 굳이 재지 않는다.
            .then(if (isReply) Modifier.height(IntrinsicSize.Min) else Modifier)
    ) {
        if (isReply) {
            // 왼쪽 세로선 — 들여쓰기만으로는 답글인지 한눈에 안 들어온다.
            Box(
                Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(AppColors.Divider)
            )
            Spacer(Modifier.width(10.dp))
        }
        // 목록 카드와 같은 이니셜 배지 — 누가 쓴 글인지 훑어보기 쉽다
        AuthorInitial(answer.authorName, admin = answer.isAdmin, size = if (isReply) 26 else 32)
        Spacer(Modifier.width(10.dp))
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
            if (onReply != null) {
                Text(
                    "답글",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onReply)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
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
