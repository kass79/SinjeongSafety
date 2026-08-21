package com.sinjeong.safety.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Timestamp
import com.sinjeong.safety.data.Attachment
import com.sinjeong.safety.data.Categories
import com.sinjeong.safety.data.LinkAttachment
import com.sinjeong.safety.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Locale

/** 작성 화면에서 새로 고른 파일 (업로드 전) */
// 출무점호 작성 화면도 같은 첨부 UI를 쓴다 — 같은 패키지라 internal 로 열어 둔다.
internal data class PendingFile(val uri: Uri, val name: String, val size: Long, val mime: String) {
    val isImage get() = mime.startsWith("image/")
    val isVideo get() = mime.startsWith("video/")
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> String.format("%.1fMB", bytes / 1048576.0)
}

fun fileTypeInfo(name: String): Pair<String, Color> = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "PDF" to Color(0xFFE53935)
    "doc", "docx" -> "DOC" to Color(0xFF1565C0)
    "xls", "xlsx" -> "XLS" to Color(0xFF2E7D32)
    "ppt", "pptx" -> "PPT" to Color(0xFFE64A19)
    "hwp", "hwpx" -> "HWP" to Color(0xFF00838F)
    "txt" -> "TXT" to Color(0xFF616161)
    "jpg", "jpeg", "png", "gif", "webp" -> "IMG" to Color(0xFF7B1FA2)
    else -> "FILE" to Color(0xFF5C6BC0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WriteScreen(
    vm: MainViewModel,
    editingPostId: String?,   // null이면 새 글
    onBack: () -> Unit
) {
    val editing = editingPostId?.let { vm.postById(it) }
    val isUploading by vm.isUploading.collectAsState()

    var category by remember { mutableStateOf(editing?.category ?: Categories.HUMAN_ERROR) }
    // 자료 날짜 — 과거 자료를 올릴 때 실제 날짜로 바꾼다. 기본값은 오늘.
    var docCal by remember {
        mutableStateOf(
            java.util.Calendar.getInstance().apply {
                // 예전 글에는 docDate가 없다. 그때는 올린 시각을 써야
                // 수정만 했는데 날짜가 오늘로 바뀌어 맨 위로 올라오는 일이 없다.
                (editing?.docDate ?: editing?.createdAt)?.toDate()?.let { time = it }
            }
        )
    }
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var content by remember { mutableStateOf(editing?.content ?: "") }

    // 첨부: 기존 유지분 + 새로 고른 파일
    var keptAttachments by remember { mutableStateOf(editing?.attachments ?: emptyList()) }
    var newFiles by remember { mutableStateOf(listOf<PendingFile>()) }
    var links by remember { mutableStateOf(editing?.links ?: emptyList()) }
    var linkInput by remember { mutableStateOf("") }

    // 사진·문서 피커
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val picked = uris.map { uri ->
            val (name, size) = vm.fileInfo(uri)
            PendingFile(uri, name, size, vm.mimeOf(uri))
        }
        newFiles = newFiles + picked
    }
    // 동영상 전용 피커 (video/* 만)
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val picked = uris.map { uri ->
            val (name, size) = vm.fileInfo(uri)
            PendingFile(uri, name, size, vm.mimeOf(uri))
        }
        newFiles = newFiles + picked
    }

    val canSave = title.isNotBlank() && content.isNotBlank() && !isUploading

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editing == null) "새 게시물 작성" else "게시물 수정",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isUploading) {
                        Icon(Icons.Default.Close, "닫기")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            vm.savePost(
                                editingId = editingPostId,
                                category = category,
                                title = title, content = content,
                                keptAttachments = keptAttachments,
                                newFileUris = newFiles.map { it.uri },
                                links = links,
                                docDate = Timestamp(docCal.time)
                            ) { onBack() }
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                color = Color.White, strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("업로드 중...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text(
                                if (editing == null) "등록" else "수정 완료",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionLabel("카테고리")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Categories.ALL.forEach { c ->
                    SelectChip(text = c, selected = category == c, onClick = { category = c })
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("자료 날짜")
            val context = LocalContext.current
            val dateText = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(docCal.time)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, AppColors.Divider),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isUploading) {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                docCal = (docCal.clone() as java.util.Calendar).apply {
                                    set(java.util.Calendar.YEAR, y)
                                    set(java.util.Calendar.MONTH, m)
                                    set(java.util.Calendar.DAY_OF_MONTH, d)
                                }
                            },
                            docCal.get(java.util.Calendar.YEAR),
                            docCal.get(java.util.Calendar.MONTH),
                            docCal.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    }
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\uD83D\uDCC5", fontSize = 15.sp)
                    Spacer(Modifier.width(9.dp))
                    Text(dateText, fontSize = 14.sp, color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f))
                    Text("변경", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "지난 자료를 올릴 때는 그 자료의 실제 날짜로 바꿔주세요. 최신 공지가 밀리지 않습니다.",
                fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 16.sp
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("제목")
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                singleLine = true, enabled = !isUploading,
                placeholder = { Text("제목을 입력하세요", color = AppColors.TextSecondary) },
                shape = RoundedCornerShape(14.dp), colors = writeFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("내용")
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                enabled = !isUploading,
                placeholder = {
                    Text(
                        "안전 정보 내용을 입력하세요.\n예) 신도림역 진입 시 신호 확인 철저 및 서행 운행",
                        color = AppColors.TextSecondary
                    )
                },
                shape = RoundedCornerShape(14.dp), colors = writeFieldColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp)
            )

            // ── 파일 · 동영상 첨부 ──────────────────────────────
            Spacer(Modifier.height(20.dp))
            SectionLabel("파일 · 동영상 첨부")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                // 사진/문서
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFC6CEE8)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isUploading) { filePicker.launch("*/*") }
                ) {
                    Column(
                        Modifier.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AttachFile, null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("사진 · 문서", color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                // 동영상
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFC6CEE8)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isUploading) { videoPicker.launch("video/*") }
                ) {
                    Column(
                        Modifier.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Movie, null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("동영상 (MP4)", color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Text(
                "사진·PDF·DOCX·HWP (20MB) · 동영상 MP4 (최대 200MB)",
                fontSize = 11.sp, color = AppColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // 첨부 목록 (기존 유지분 + 새 파일)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keptAttachments.forEach { att ->
                    when {
                        att.isImage -> ImageThumb(model = att.url) { keptAttachments = keptAttachments - att }
                        att.isVideo -> VideoChip(name = att.name, size = att.size) { keptAttachments = keptAttachments - att }
                        else -> FileChip(name = att.name, size = att.size) { keptAttachments = keptAttachments - att }
                    }
                }
                newFiles.forEach { pf ->
                    when {
                        pf.isImage -> ImageThumb(model = pf.uri) { newFiles = newFiles - pf }
                        pf.isVideo -> VideoChip(name = pf.name, size = pf.size) { newFiles = newFiles - pf }
                        else -> FileChip(name = pf.name, size = pf.size) { newFiles = newFiles - pf }
                    }
                }
            }

            // ── 링크 첨부 (유튜브·웹페이지) ──────────────────────
            Spacer(Modifier.height(20.dp))
            SectionLabel("링크 첨부")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    singleLine = true,
                    placeholder = { Text("https:// 주소 붙여넣기", color = AppColors.TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Link, null, tint = AppColors.Primary) },
                    shape = RoundedCornerShape(14.dp),
                    colors = writeFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val u = linkInput.trim()
                        if (u.startsWith("http")) {
                            links = links + LinkAttachment(url = u, title = "")
                            linkInput = ""
                        }
                    },
                    enabled = linkInput.trim().startsWith("http"),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                ) { Text("추가", fontWeight = FontWeight.Bold) }
            }
            Text(
                "유튜브 링크는 카드에 썸네일이 자동으로 표시돼요",
                fontSize = 11.sp, color = AppColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            links.forEach { link ->
                LinkRow(link = link) { links = links - link }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

// ── 첨부 이미지 썸네일 (X 버튼 포함) ────────────────────────────
@Composable
internal fun ImageThumb(model: Any, onRemove: () -> Unit) {
    Box {
        AsyncImage(
            model = model, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp))
        )
        RemoveButton(onRemove, Modifier.align(Alignment.TopEnd))
    }
}

// ── 첨부 동영상 썸네일 (재생 아이콘) ────────────────────────────
@Composable
internal fun VideoChip(name: String, size: Long, onRemove: () -> Unit) {
    Box {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1C28)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Text(
                formatSize(size),
                color = Color.White, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
            )
        }
        RemoveButton(onRemove, Modifier.align(Alignment.TopEnd))
    }
}

// ── 링크 첨부 행 (유튜브면 ▶ 표시) ─────────────────────────────
@Composable
internal fun LinkRow(link: LinkAttachment, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (link.isYoutube) Color(0xFFE53935) else AppColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (link.isYoutube) Icons.Default.PlayCircle else Icons.Default.Link,
                    null, tint = Color.White, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (link.isYoutube) "YouTube 영상" else link.displayHost,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(link.url, fontSize = 11.sp, color = AppColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RemoveButton(onRemove)
        }
    }
}

// ── 첨부 문서 칩 (X 버튼 포함) ──────────────────────────────────
@Composable
internal fun FileChip(name: String, size: Long, onRemove: () -> Unit) {
    val (label, color) = fileTypeInfo(name)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 130.dp)) {
                Text(
                    name, fontSize = 12.sp, color = AppColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(formatSize(size), fontSize = 11.sp, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.width(4.dp))
            RemoveButton(onRemove)
        }
    }
}

@Composable
private fun RemoveButton(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C2F3A))
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Close, "삭제", tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        color = AppColors.TextPrimary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) AppColors.Primary else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (selected) AppColors.Primary else AppColors.Divider
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            color = if (selected) Color.White else AppColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun writeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Divider
)
