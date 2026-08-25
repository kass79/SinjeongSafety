package com.sinjeong.safety.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight as FW
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.Attachment
import com.sinjeong.safety.data.Categories
import com.sinjeong.safety.data.Comment
import com.sinjeong.safety.data.LinkAttachment
import com.sinjeong.safety.data.QuizQuestion
import com.sinjeong.safety.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: MainViewModel,
    postId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onConfirmStatus: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    val posts by vm.posts.collectAsState()
    val post = posts.find { it.id == postId }
    val isAdmin by vm.isAdmin.collectAsState()
    val crewEmpNo by vm.crewEmpNo.collectAsState()
    val loggedIn = crewEmpNo != null || isAdmin
    val favoriteIds by vm.favoriteIds.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 댓글은 하위 컬렉션이라 목록의 Post 로는 알 수 없다. 이 화면에서만 구독한다.
    // postId 가 바뀔 때만 새로 만든다 — 매 재구성마다 만들면 리스너가 계속 새로 붙는다.
    val commentFlow = remember(postId) { vm.commentsFlow(postId) }
    val commentsResult by commentFlow.collectAsState(initial = Result.success(emptyList()))
    val comments = commentsResult.getOrDefault(emptyList())
    var commentInput by remember { mutableStateOf("") }
    // 답글 대상. null 이면 원댓글을 쓰는 중이다. 깊이는 1단계까지만이라
    // 여기 담기는 건 언제나 원댓글이다(답글에는 답글 버튼을 두지 않는다).
    var replyTo by remember { mutableStateOf<Comment?>(null) }

    // ── 사고사례 퀴즈 ──
    // 관리자가 검토 중인 초안(저장 전). null 이면 검토 다이얼로그가 없다.
    var quizDraft by remember { mutableStateOf<List<QuizQuestion>?>(null) }
    // 승무원이 푸는 중인지. 중간에 닫으면 확인 처리하지 않는다.
    var showQuiz by remember { mutableStateOf(false) }
    val aiLoading by vm.aiLoading.collectAsState()

    LaunchedEffect(postId) { vm.markRead(postId) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("게시물", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    // 즐겨찾기 별 — 로그인한 사람 누구나
                    if (post != null) {
                        val isFav = favoriteIds.contains(postId)
                        IconButton(onClick = { vm.toggleFavorite(postId) }) {
                            Icon(
                                if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                                if (isFav) "즐겨찾기 해제" else "즐겨찾기",
                                tint = if (isFav) Color(0xFFF5B301) else AppColors.TextSecondary
                            )
                        }
                    }
                    if (isAdmin && post != null) {
                        IconButton(onClick = { vm.togglePin(postId, !post.pinned) }) {
                            Icon(
                                if (post.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                "고정",
                                tint = if (post.pinned) Color(0xFFE8890C) else AppColors.TextSecondary
                            )
                        }
                        IconButton(onClick = { onEdit(postId) }) {
                            Icon(Icons.Default.Edit, "수정", tint = AppColors.Primary)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "삭제", tint = AppColors.NewBadge)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        },
        // ── 맨 아래 고정 댓글 입력줄 ──
        // 질의응답 화면과 같은 방식이다. 예전에는 매니페스트의 adjustResize 가 키보드만큼
        // 창을 줄여 줘서 이 줄이 저절로 올라왔는데, targetSdk 35+ 부터 안드로이드가
        // edge-to-edge 를 강제하면서 **창이 더 이상 줄지 않는다**(36 에선 opt-out 도 없다).
        // 그래서 키보드가 이 줄을 그대로 덮어 "쓰는 내용이 안 보인다"는 신고가 나왔다.
        // 규정 검색 화면(RegulationAskScreen)이 이미 쓰는 방식대로 직접 인셋을 준다.
        bottomBar = {
            if (post != null) {
                if (loggedIn) {
                    // background 를 imePadding 앞에 둬야 키보드 위 여백까지 같은 색으로 칠해진다.
                    Column(Modifier.fillMaxWidth().background(AppColors.Surface).imePadding()) {
                        // 답글 대상 표시줄 — 지금 누구에게 쓰는 중인지 보이지 않으면
                        // 답글 버튼을 눌러 놓고도 원댓글처럼 느껴진다.
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
                                value = commentInput,
                                onValueChange = { commentInput = it },
                                placeholder = {
                                    Text(
                                        if (replyTo != null) "답글을 입력하세요" else "댓글을 입력하세요",
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
                                    if (commentInput.isNotBlank())
                                        vm.addComment(postId, commentInput, replyTo?.id ?: "") {
                                            commentInput = ""
                                            replyTo = null
                                        }
                                },
                                enabled = commentInput.isNotBlank()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    if (replyTo != null) "답글 등록" else "댓글 등록",
                                    tint = if (commentInput.isNotBlank()) AppColors.Primary
                                           else AppColors.TextHint
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
                            "로그인하면 댓글을 쓸 수 있습니다",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("삭제되었거나 존재하지 않는 게시물입니다", color = AppColors.TextSecondary)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AppColors.Surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (post.pinned) {
                            Surface(color = Color(0xFFFFF0D9), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "📌 고정", color = Color(0xFFE8890C), fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        val (bg, fg) = categoryColors(post.category)
                        Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                Categories.short(post.category), color = fg, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        post.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 30.sp,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    val fmt = SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.KOREA)
                    Text(
                        buildString {
                            append(post.authorName)
                            post.createdAt?.let { append(" · ${fmt.format(it.toDate())}") }
                            if (post.updatedAt != null && post.createdAt != null &&
                                post.updatedAt.seconds - post.createdAt.seconds > 60
                            ) append(" (수정됨)")
                            append(" · 조회 ${post.views}")
                        },
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = AppColors.Divider)
                    Spacer(Modifier.height(16.dp))

                    LinkifiedText(post.content)

                    // ── 첨부 이미지 갤러리 ──
                    val images = post.attachments.filter { it.isImage }
                    val videos = post.attachments.filter { it.isVideo }
                    val docs = post.attachments.filter { !it.isImage && !it.isVideo }
                    if (images.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        AttachmentGallery(images)
                    }
                    // ── 첨부 동영상 (인앱 재생) ──
                    if (videos.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        videos.forEach { v ->
                            VideoPlayer(v)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    // ── 링크 첨부 (유튜브 썸네일 등) ──
                    if (post.links.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        post.links.forEach { link ->
                            LinkPreviewCard(link)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    // ── 첨부 문서 목록 ──
                    if (docs.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        docs.forEach { doc ->
                            AttachmentFileRow(doc)
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    val hasQuiz = post.quiz.isNotEmpty()

                    // ── 사고사례 퀴즈 만들기 (관리자만) ──
                    // WriteScreen 의 "AI 3줄 요약" 알약과 같은 모양. 초안만 만들고 게시는 사람이 한다.
                    if (isAdmin) {
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = AppColors.TagOpsBg,
                                modifier = Modifier.clickable(enabled = !aiLoading) {
                                    vm.generateQuizDraft(post.title, post.content) { quizDraft = it }
                                }
                            ) {
                                Box(
                                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (aiLoading) {
                                        CircularProgressIndicator(
                                            color = AppColors.TagOpsFg, strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Text(
                                            if (hasQuiz) "퀴즈 다시 만들기" else "AI 퀴즈 만들기",
                                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                            color = AppColors.TagOpsFg
                                        )
                                    }
                                }
                            }
                            if (hasQuiz) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "퀴즈 삭제",
                                    fontSize = 12.sp,
                                    color = AppColors.TextHint,
                                    modifier = Modifier
                                        .clickable { vm.saveQuiz(postId, emptyList()) {} }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // ── 확인(읽음) 버튼 ──
                    Spacer(Modifier.height(20.dp))
                    val confirmed = vm.isConfirmed(postId)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            // 퀴즈가 붙은 글은 먼저 풀어야 확인 처리된다(틀려도 확인은 된다).
                            onClick = { if (hasQuiz) showQuiz = true else vm.confirmRead(postId) },
                            enabled = !confirmed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (confirmed) AppColors.Primary else AppColors.Surface,
                                contentColor = if (confirmed) Color.White else AppColors.Primary,
                                disabledContainerColor = AppColors.Primary,
                                disabledContentColor = Color.White
                            ),
                            border = if (confirmed) null
                                else androidx.compose.foundation.BorderStroke(1.5.dp, AppColors.Primary),
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when {
                                    confirmed -> "✓ 확인 완료"
                                    hasQuiz -> "퀴즈 풀고 확인"
                                    else -> "확인했습니다"
                                },
                                fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        // 관리자만 눌러서 "누가 아직 안 봤는지" 현황으로 들어간다.
                        if (isAdmin) {
                            Text(
                                "${post.confirms}명 확인 ›",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary,
                                modifier = Modifier.clickable { onConfirmStatus(postId) }
                            )
                        } else {
                            Text(
                                "${post.confirms}명 확인",
                                fontSize = 12.sp, color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // ── 댓글 ──
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = AppColors.Divider)
            Spacer(Modifier.height(14.dp))
            Text(
                "댓글 ${comments.size}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            // 댓글이 없으면 구분선 아래가 텅 비어 고장난 것처럼 보인다
            if (comments.isEmpty()) {
                Text(
                    "첫 댓글을 남겨보세요",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
            } else {
                // 원댓글 아래에 그 답글을 붙여서 그린다. Firestore 는 평평한 한 컬렉션이라
                // (createdAt 오름차순) 이 조립은 화면에서 한다.
                val roots = comments.filter { it.parentId.isBlank() }
                val rootIds = roots.map { it.id }.toSet()
                val repliesOf = comments.filter { it.parentId.isNotBlank() }.groupBy { it.parentId }
                // 부모가 지워진 답글은 어느 원댓글에도 못 붙는다. 그대로 두면 화면에서
                // 사라지므로(내용은 남아 있는데 안 보인다) 맨 아래에 원댓글처럼 그린다.
                val orphans = comments.filter {
                    it.parentId.isNotBlank() && it.parentId !in rootIds
                }

                roots.forEach { root ->
                    CommentRow(
                        comment = root,
                        canDelete = vm.canDeleteComment(root),
                        onDelete = { vm.deleteComment(postId, root) },
                        onReply = if (loggedIn) ({ replyTo = root }) else null
                    )
                    repliesOf[root.id].orEmpty().forEach { reply ->
                        CommentRow(
                            comment = reply,
                            canDelete = vm.canDeleteComment(reply),
                            onDelete = { vm.deleteComment(postId, reply) },
                            onReply = null,   // 1단계 깊이 — 답글에는 답글을 달지 않는다
                            isReply = true
                        )
                    }
                }
                orphans.forEach { c ->
                    CommentRow(
                        comment = c,
                        canDelete = vm.canDeleteComment(c),
                        onDelete = { vm.deleteComment(postId, c) },
                        onReply = null
                    )
                }
            }
        }
    }

    if (showDeleteDialog && post != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("게시물 삭제") },
            text = { Text("\"${post.title}\"\n정말 삭제하시겠습니까? 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deletePost(postId) { onBack() }
                }) { Text("삭제", color = AppColors.NewBadge, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }

    // ── AI 퀴즈 초안 검토 (관리자) ──
    quizDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { quizDraft = null },
            title = { Text("퀴즈 검토", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "AI가 만든 초안입니다. 내용을 확인하고 저장하세요.",
                        fontSize = 12.5.sp, color = AppColors.TextSecondary
                    )
                    draft.forEachIndexed { i, q ->
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "${i + 1}. ${q.q}",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        q.choices.forEachIndexed { ci, choice ->
                            val right = ci == q.answer
                            Text(
                                (if (right) "✓ " else "· ") + choice,
                                fontSize = 13.sp, lineHeight = 20.sp,
                                fontWeight = if (right) FontWeight.Bold else FontWeight.Normal,
                                color = if (right) AppColors.TagOpsFg else AppColors.TextSecondary
                            )
                        }
                        if (q.explain.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "해설 · ${q.explain}",
                                fontSize = 12.sp, lineHeight = 18.sp, color = AppColors.TextHint
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveQuiz(postId, draft) { quizDraft = null } }) {
                    Text("이대로 저장", fontWeight = FontWeight.Bold, color = AppColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { quizDraft = null }) { Text("취소") }
            }
        )
    }

    // ── 퀴즈 풀기 (승무원) ──
    if (showQuiz && post != null && post.quiz.isNotEmpty()) {
        QuizDialog(
            quiz = post.quiz,
            onDismiss = { showQuiz = false },   // 중간에 닫으면 확인 처리하지 않는다
            onDone = { correct, total ->
                showQuiz = false
                vm.confirmRead(postId, correct, total)
            }
        )
    }
}

/**
 * 사고사례 퀴즈 풀이. 한 문항씩 보여 주고, 보기를 누르면 그 자리에서 정답/오답과 해설을 편다.
 * 틀려도 막지 않는다 — 해설을 읽히는 게 목적이고, 정답 여부는 기록되어 관리자가 본다.
 * 다 풀고 [확인 완료]를 눌러야 확인 처리된다(중간에 닫으면 아무 일도 일어나지 않는다).
 */
@Composable
private fun QuizDialog(
    quiz: List<QuizQuestion>,
    onDismiss: () -> Unit,
    onDone: (Int, Int) -> Unit
) {
    var index by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var correct by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val last = index == quiz.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (finished) "퀴즈 완료" else "사고사례 퀴즈 ${index + 1}/${quiz.size}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (finished) {
                Text(
                    "${quiz.size}문제 중 ${correct}문제 정답",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary
                )
            } else {
                val q = quiz[index]
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        q.q,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp, color = AppColors.TextPrimary
                    )
                    q.choices.forEachIndexed { ci, choice ->
                        val answered = picked != null
                        val isAnswer = ci == q.answer
                        val wrongPick = picked == ci && !isAnswer
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (answered && isAnswer) AppColors.TagOpsBg else AppColors.Surface,
                            border = androidx.compose.foundation.BorderStroke(
                                if (wrongPick) 1.5.dp else 1.dp,
                                if (wrongPick) AppColors.NewBadge else AppColors.Divider
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clickable(enabled = !answered) {
                                    picked = ci
                                    if (isAnswer) correct++
                                }
                        ) {
                            Text(
                                choice,
                                fontSize = 14.sp, lineHeight = 20.sp,
                                fontWeight = if (answered && isAnswer) FontWeight.Bold
                                             else FontWeight.Normal,
                                color = when {
                                    answered && isAnswer -> AppColors.TagOpsFg
                                    wrongPick -> AppColors.NewBadge
                                    else -> AppColors.TextPrimary
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                    if (picked != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (picked == q.answer) "정답입니다" else "오답입니다",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (picked == q.answer) AppColors.TagOpsFg else AppColors.NewBadge
                        )
                        if (q.explain.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                q.explain,
                                fontSize = 13.sp, lineHeight = 20.sp, color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                finished -> TextButton(onClick = { onDone(correct, quiz.size) }) {
                    Text("확인 완료", fontWeight = FontWeight.Bold, color = AppColors.Primary)
                }
                picked != null -> TextButton(onClick = {
                    if (last) finished = true else { index++; picked = null }
                }) {
                    Text(
                        if (last) "결과 보기" else "다음 문제",
                        fontWeight = FontWeight.Bold, color = AppColors.Primary
                    )
                }
            }
        },
        dismissButton = {
            if (!finished) TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}


// ── 댓글 한 줄 ─────────────────────────────────────────────────
// 질의응답 답변(AnswerRow)과 같은 모양이다. 이니셜 배지·관리자 뱃지 규칙을 맞춰야
// 두 게시판을 오가는 사람이 같은 화면으로 읽는다.
// [isReply] 면 한 단계 들여쓰고 배지를 줄여 답글임을 보이게 한다(깊이는 1단계까지만).
// [onReply] 가 null 이면 답글 버튼을 그리지 않는다(답글 줄, 비로그인).
@Composable
private fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onReply: (() -> Unit)? = null,
    isReply: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 34.dp else 0.dp)
            .padding(vertical = 7.dp)
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
        AuthorInitial(comment.authorName, admin = comment.isAdmin, size = if (isReply) 26 else 32)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName.ifBlank { "이름 미등록" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                if (comment.isAdmin) {
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
                Text(comment.createdAt.toRelative(), fontSize = 11.5.sp, color = AppColors.TextHint)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                comment.content,
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
                Icons.Default.Delete,
                "댓글 삭제",
                tint = AppColors.TextHint,
                modifier = Modifier
                    .padding(start = 8.dp, top = 2.dp)
                    .size(18.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}

// ── 첨부 이미지 갤러리 (2열, 탭하면 원본 열기) ──────────────────
@Composable
private fun AttachmentGallery(images: List<Attachment>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        images.chunked(2).forEach { rowImages ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowImages.forEach { img ->
                    AsyncImage(
                        model = img.url,
                        contentDescription = img.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 140.dp, max = if (images.size == 1) 320.dp else 160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(img.url))
                                )
                            }
                    )
                }
                if (rowImages.size == 1 && images.size > 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── 첨부 문서 행 (탭하면 다운로드/열기) ─────────────────────────
@Composable
private fun AttachmentFileRow(doc: Attachment) {
    val context = LocalContext.current
    val (label, color) = fileTypeInfo(doc.name)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc.url)))
            }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    doc.name, fontSize = 14.sp, color = AppColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(formatSize(doc.size), fontSize = 12.sp, color = AppColors.TextSecondary)
            }
            Text("⬇", fontSize = 16.sp, color = AppColors.Primary)
        }
    }
}


// ── 첨부 동영상 인앱 재생 (탭하면 재생) ─────────────────────────
@Composable
private fun VideoPlayer(video: Attachment) {
    var playing by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    // 전체화면으로 넘어갈 때 "보던 지점"을 이어받으려면 세로 재생기 핸들이 필요하다.
    var inlineView by remember { mutableStateOf<VideoView?>(null) }
    var startAt by remember { mutableStateOf(0) }
    // 화면 방향을 돌리려면 Activity 가 있어야 한다. Compose 의 Context 는 래핑돼 있을 수
    // 있어 벗겨서 찾고, 못 찾으면(Activity 가 아니면) null 로 두고 조용히 세로로 간다.
    val context = LocalContext.current
    val activity = remember(context) {
        var c: android.content.Context? = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        c as? Activity
    }

    // ── 이어보기: 재생 위치를 기기에 저장한다(DB·새 라이브러리 없이 SharedPreferences 로 충분) ──
    // URL 은 업로드 시각+파일명이라 유일하지만 특수문자가 많아 키로 쓸 수 없다 → 해시를 키로.
    val prefs = remember(context) { context.getSharedPreferences("safety_prefs", Context.MODE_PRIVATE) }
    val posKey = remember(video.url) { "video_pos_" + video.url.hashCode() }
    // 막 시작한 영상은 이어볼 필요가 없다 — 30초 미만 지점은 저장도 복원도 하지 않는다.
    val minResumeMs = 30_000
    // 재생 전 화면에 "이어서 보기"를 띄우기 위해 저장된 지점을 한 번 읽어 둔다.
    // (재생을 시작하면 이 화면은 사라지므로 다시 읽을 일이 없다)
    val savedMs = remember(posKey) { prefs.getInt(posKey, 0) }
    // 끝까지 본 뒤 화면을 나가면 currentPosition 이 '끝'이라 지운 걸 다시 쓰게 된다. 그래서 기억해 둔다.
    val finished = remember { mutableStateOf(false) }
    fun savePos(v: VideoView?) {
        val ms = v?.currentPosition ?: 0
        if (!finished.value && ms >= minResumeMs) prefs.edit().putInt(posKey, ms).apply()
    }
    fun onFinished() {
        // 안 지우면 다음에 항상 끝부분에서 시작해 아무것도 안 보인다.
        finished.value = true
        prefs.edit().remove(posKey).apply()
    }
    // 이 화면을 벗어날 때(다른 화면 이동·앱 종료) 마지막 위치를 남긴다.
    DisposableEffect(Unit) { onDispose { savePos(inlineView) } }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .aspectRatio(16f / 9f)
    ) {
        if (playing) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.parse(video.url))
                        setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                        setOnPreparedListener {
                            // 저장된 지점이 있으면 거기서부터, 없으면 처음부터.
                            val saved = prefs.getInt(posKey, 0)
                            if (saved >= minResumeMs) seekTo(saved)
                            it.start()
                        }
                        setOnCompletionListener { onFinished() }
                        inlineView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            // MediaController 에는 재생/일시정지/탐색바만 있고 전체화면 버튼이 없어서 직접 얹는다.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable {
                        // 두 재생기가 동시에 소리를 내지 않게 세로 쪽은 멈춰 두고 위치만 넘긴다.
                        inlineView?.let { startAt = it.currentPosition; it.pause() }
                        fullscreen = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Fullscreen, "전체화면", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        } else {
            // 재생 전: 포스터(있으면) + 재생 버튼 + 파일명.
            // 포스터가 없는 옛 게시물·추출 실패 건은 예전 그대로 검은 배경으로 나온다.
            Box(Modifier.fillMaxSize().clickable { playing = true }, contentAlignment = Alignment.Center) {
                if (video.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 밝은 장면이 잡힌 포스터에서도 흰 글씨·재생 버튼이 묻히지 않게 살짝 덮는다.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                }
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, "재생", tint = AppColors.Primary, modifier = Modifier.size(30.dp))
                }
                Text(
                    // 보다 만 영상이면 파일명보다 "어디서부터 이어지는지"가 궁금하다.
                    if (savedMs >= minResumeMs)
                        "이어서 보기 · %d:%02d".format(savedMs / 60000, savedMs / 1000 % 60)
                    else "${video.name} · ${formatSize(video.size)}",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                )
            }
        }
    }

    // ── 전체화면 보기 ──
    // 가로로 눕혀 크게 보는 게 목적이라 다이얼로그를 열면서 화면을 가로로 돌린다.
    if (fullscreen) {
        // 화면 방향은 '전체화면 상태'에 묶는다. 다이얼로그 컴포지션에 묶으면(예전 코드)
        // 아래 key() 재생성 때 onDispose 가 방향을 되돌려 가로↔세로가 무한히 튕긴다 — 실측.
        // fullscreen 이 true 인 동안만 가로, 어떤 경로로 닫혀도 onDispose 가 반드시 복원한다.
        DisposableEffect(Unit) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                // UNSPECIFIED 로 되돌리면 앱 전체가 자동회전을 따라가 버린다(매니페스트 세로
                // 고정이 런타임 요청에 덮인 상태로 남는다). 영상을 한 번 본 뒤로 앱이 계속
                // 가로로 돌아가던 원인이라 명시적으로 세로로 되돌린다.
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        // 회전이 configChanges 로 처리되면서(액티비티 재시작 없음) 다이얼로그 창이
        // 회전 전 크기로 남아 화면 가운데 기둥처럼 뜨는 것을 폴드 폭에서 실측했다.
        // 방향이 바뀌면 다이얼로그만 새로 만들어 새 창 크기로 다시 잡게 한다.
        androidx.compose.runtime.key(LocalConfiguration.current.orientation) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var fsView by remember { mutableStateOf<VideoView?>(null) }
            DisposableEffect(Unit) {
                // 다이얼로그가 살아 있는 동안 세로 재생기는 반드시 멈춘 상태여야 한다.
                // 전체화면 버튼 onClick 에서도 pause 하지만, 회전으로 이 다이얼로그가
                // 다시 만들어질 땐 그 onClick 을 아무도 다시 부르지 않는다 → 여기서 한 겹 더.
                inlineView?.pause()
                onDispose {
                    // 위치 저장은 회전 재생성 때도 해야 한다 — 새 다이얼로그가 여기서 이어 시작한다.
                    // (startAt 은 바깥 state 라 새 다이얼로그의 onPrepared 가 이 값을 읽는다.
                    //  onPrepared 는 비동기라 항상 이 dispose 뒤에 돈다.)
                    val pos = fsView?.currentPosition ?: 0
                    if (pos > 0) startAt = pos
                    // 기기에도 남긴다 — 화면을 아예 나가도 이어보게.
                    savePos(fsView)
                    // 세로 재생기 재개는 '진짜로 닫힐 때'만. 회전 재생성 중엔 fullscreen 이
                    // 여전히 true 라 여기 안 들어온다 — 안 거르면 전체화면+세로가 동시에 울린다.
                    if (!fullscreen) {
                        if (pos > 0) inlineView?.seekTo(pos)
                        inlineView?.start()
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.parse(video.url))
                            // 전체화면 안에서도 되감기/빨리감기는 필요하다.
                            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                            setOnPreparedListener { mp ->
                                if (startAt > 0) seekTo(startAt)
                                mp.start()
                            }
                            setOnCompletionListener { onFinished() }
                            fsView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { fullscreen = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FullscreenExit, "축소", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
        } // key(orientation)
}

// ── 링크 미리보기 카드 (유튜브면 썸네일) ───────────────────────
@Composable
private fun LinkPreviewCard(link: LinkAttachment) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
    ) {
        if (link.isYoutube) {
            Column {
                Box {
                    AsyncImage(
                        model = link.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                    Box(
                        Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Color(0xE6E53935)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("YouTube에서 열기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                }
            }
        } else {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.Primary),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Link, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.displayHost, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(link.url, fontSize = 12.sp, color = AppColors.TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("↗", fontSize = 16.sp, color = AppColors.Primary)
            }
        }
    }
}


// ── 본문 내 URL 자동 링크 (탭하면 열림) ────────────────────────
@Composable
private fun LinkifiedText(text: String) {
    val context = LocalContext.current
    val urlRegex = Regex("""https?://[^\s]+""")
    val matches = urlRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        Text(text, fontSize = 16.sp, lineHeight = 28.sp, color = AppColors.TextPrimary)
        return
    }

    val annotated = buildAnnotatedString {
        var last = 0
        for (m in matches) {
            append(text.substring(last, m.range.first))
            pushStringAnnotation(tag = "URL", annotation = m.value)
            withStyle(SpanStyle(color = AppColors.Primary, fontWeight = FW.SemiBold,
                textDecoration = TextDecoration.Underline)) {
                append(m.value)
            }
            pop()
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }

    ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = 16.sp, lineHeight = 28.sp, color = AppColors.TextPrimary
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item)))
                }
            }
        }
    )
}
