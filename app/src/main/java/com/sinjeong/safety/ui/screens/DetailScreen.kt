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
import coil.compose.AsyncImage
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.Attachment
import com.sinjeong.safety.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: MainViewModel,
    postId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val posts by vm.posts.collectAsState()
    val post = posts.find { it.id == postId }
    val isAdmin by vm.isAdmin.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    if (isAdmin && post != null) {
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
                        val (bg, fg) = tagColors(post.tag)
                        Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                post.tag, color = fg, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = AppColors.Background,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                post.category, color = AppColors.TextSecondary, fontSize = 12.sp,
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

                    Text(
                        post.content,
                        fontSize = 16.sp,
                        lineHeight = 28.sp,
                        color = AppColors.TextPrimary
                    )

                    // ── 첨부 이미지 갤러리 ──
                    val images = post.attachments.filter { it.isImage }
                    val docs = post.attachments.filter { !it.isImage }
                    if (images.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        AttachmentGallery(images)
                    }
                    // ── 첨부 문서 목록 ──
                    if (docs.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        docs.forEach { doc ->
                            AttachmentFileRow(doc)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
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
