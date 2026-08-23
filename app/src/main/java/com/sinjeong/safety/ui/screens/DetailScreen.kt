package com.sinjeong.safety.ui.screens

import android.content.Intent
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
import coil.compose.AsyncImage
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.Attachment
import com.sinjeong.safety.data.Categories
import com.sinjeong.safety.data.Comment
import com.sinjeong.safety.data.LinkAttachment
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        // ── 맨 아래 고정 댓글 입력줄 ──
        // 질의응답 화면과 같은 방식이다. 매니페스트가 adjustResize 라 키보드가 뜨면
        // 창 자체가 줄어들고 이 줄이 키보드 위로 올라온다(별도 imePadding 불필요).
        bottomBar = {
            if (post != null) {
                if (loggedIn) {
                    Column(Modifier.fillMaxWidth().background(Color.White)) {
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
                            .background(Color.White)
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
                color = Color.White,
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

                    // ── 확인(읽음) 버튼 ──
                    Spacer(Modifier.height(20.dp))
                    val confirmed = vm.isConfirmed(postId)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { vm.confirmRead(postId) },
                            enabled = !confirmed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (confirmed) AppColors.Primary else Color.White,
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
                                if (confirmed) "✓ 확인 완료" else "확인했습니다",
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
                        setOnPreparedListener { it.start() }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 재생 전: 어두운 배경 + 재생 버튼 + 파일명
            Box(Modifier.fillMaxSize().clickable { playing = true }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, "재생", tint = AppColors.Primary, modifier = Modifier.size(30.dp))
                }
                Text(
                    "${video.name} · ${formatSize(video.size)}",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                )
            }
        }
    }
}

// ── 링크 미리보기 카드 (유튜브면 썸네일) ───────────────────────
@Composable
private fun LinkPreviewCard(link: LinkAttachment) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
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
