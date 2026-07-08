package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.AttachFile
import coil.compose.AsyncImage
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.R
import com.sinjeong.safety.data.Categories
import com.sinjeong.safety.data.Post
import com.sinjeong.safety.data.Tags
import com.sinjeong.safety.ui.theme.AppColors
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

// ── 시간 표시 유틸 ──────────────────────────────────────────────
fun Timestamp?.toRelative(): String {
    this ?: return "방금 전"
    val diffMin = (System.currentTimeMillis() - toDate().time) / 60000
    return when {
        diffMin < 1 -> "방금 전"
        diffMin < 60 -> "${diffMin}분 전"
        diffMin < 60 * 24 -> "${diffMin / 60}시간 전"
        diffMin < 60 * 24 * 7 -> "${diffMin / (60 * 24)}일 전"
        else -> SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(toDate())
    }
}

fun Post.isNew(readIds: Set<String>): Boolean {
    val created = createdAt?.toDate()?.time ?: return false
    val within3Days = System.currentTimeMillis() - created < 3L * 24 * 60 * 60 * 1000
    return within3Days && id !in readIds
}

@Composable
fun categoryColors(category: String): Pair<Color, Color> = when (category) {
    Categories.HUMAN_ERROR -> Color(0xFFFDF0E3) to Color(0xFFF57C00)
    Categories.EDU_VIDEO -> Color(0xFFE9F1FD) to Color(0xFF1976D2)
    Categories.REGULATION -> Color(0xFFE9F6ED) to Color(0xFF388E3C)
    Categories.NOTICE -> Color(0xFFFBF4DC) to Color(0xFFC79A00)
    else -> AppColors.Background to AppColors.TextSecondary
}

// ── 홈 화면 ─────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onPostClick: (Post) -> Unit,
    onLoginClick: () -> Unit,
    onWriteClick: () -> Unit,
    onRegulationClick: () -> Unit
) {
    val posts by vm.filteredPosts.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val readIds by vm.readIds.collectAsState()
    val listState = rememberLazyListState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.Background,
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = onWriteClick,
                    containerColor = AppColors.Primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text("글쓰기", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                HeaderBar(
                    isAdmin = isAdmin,
                    onShieldClick = { if (isAdmin) showLogoutDialog = true else onLoginClick() }
                )
            }
            item { MascotBanner() }
            item {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery
                )
            }
            item {
                CategoryRow(
                    selected = selectedCategory,
                    onToggle = { cat ->
                        if (cat == Categories.REGULATION) onRegulationClick()
                        else vm.toggleCategory(cat)
                    },
                    onSelectAll = vm::selectAll
                )
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Primary)
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("게시물이 없습니다", color = AppColors.TextSecondary)
                        if (searchQuery.isNotBlank()) {
                            Text(
                                "'$searchQuery' 검색 결과가 없어요",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        isNew = post.isNew(readIds),
                        onClick = { onPostClick(post) }
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("관리자 모드") },
            text = { Text("${vm.adminEmail ?: ""}\n로그아웃하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { vm.logout(); showLogoutDialog = false }) {
                    Text("로그아웃", color = AppColors.NewBadge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("취소") }
            }
        )
    }
}

// ── 상단 헤더: 마스코트 아이콘 + 사업소명 + 초록점 + 방패 ────────
@Composable
private fun HeaderBar(isAdmin: Boolean, onShieldClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.mascot_hello),
            contentDescription = "마스코트",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFE8F0FC))
                .border(2.dp, Color.White, RoundedCornerShape(15.dp))
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "신정승무사업소",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.Primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AppColors.OnlineGreen)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (isAdmin) "관리자님 (관리자 모드)" else "실시간 안전정보 공유중",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        }
        // 방패 아이콘 → 관리자 로그인 / 로그아웃
        Surface(
            shape = CircleShape,
            color = if (isAdmin) AppColors.Primary else Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
            modifier = Modifier.size(42.dp).clickable(onClick = onShieldClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isAdmin) Icons.Outlined.Logout else Icons.Outlined.AdminPanelSettings,
                    contentDescription = "관리자",
                    tint = if (isAdmin) Color.White else AppColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ── 마스코트 배너: 컴팩트 와이드 (이미지 + 텍스트 오버레이) ────
@Composable
private fun MascotBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        // 배너 이미지에 이미 "슬기로운 승무생활" 문구가 포함되어 있음
        Image(
            painter = painterResource(R.drawable.banner_main),
            contentDescription = "슬기로운 승무생활 - 오늘도 안전운행 출발!",
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.matchParentSize()
        )
    }
}

// ── 검색창 ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("제목 또는 내용으로 검색...", color = AppColors.TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.Primary) },
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = AppColors.Primary,
            unfocusedBorderColor = AppColors.Divider
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

// ── 4개 카테고리 카드 ───────────────────────────────────────────
private data class CategoryUi(val name: String, val short: String, val icon: ImageVector, val color: Color)

@Composable
private fun CategoryRow(selected: String?, onToggle: (String) -> Unit, onSelectAll: () -> Unit) {
    val cats = listOf(
        CategoryUi(Categories.HUMAN_ERROR, "인적오류", Icons.Default.Warning, Color(0xFFF57C00)),
        CategoryUi(Categories.EDU_VIDEO, "교육영상", Icons.Default.PlayCircle, Color(0xFF1976D2)),
        CategoryUi(Categories.REGULATION, "운전규정", Icons.Default.MenuBook, Color(0xFF388E3C)),
        CategoryUi(Categories.NOTICE, "전달사항", Icons.Default.Campaign, Color(0xFFC79A00))
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // ── "전체" 세로 pill ──
        val allSelected = selected == null
        Surface(
            shape = RoundedCornerShape(17.dp),
            color = if (allSelected) AppColors.Primary else Color.White,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (allSelected) AppColors.Primary else AppColors.Divider
            ),
            modifier = Modifier
                .width(34.dp)
                .fillMaxHeight()
                .clickable { onSelectAll() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "전\n체",
                    color = if (allSelected) Color.White else AppColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        cats.forEach { cat ->
            val isSelected = selected == cat.name
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) cat.color else AppColors.Divider
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle(cat.name) }
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(cat.color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        cat.short,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) cat.color else AppColors.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── 게시물 카드 ─────────────────────────────────────────────────
@Composable
fun PostCard(post: Post, isNew: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (post.pinned) Color(0xFFFFFBF4) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            if (post.pinned) 1.5.dp else 1.dp,
            if (post.pinned) Color(0xFFFFD9A6) else AppColors.Divider
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.pinned) {
                    Surface(color = Color(0xFFFFF0D9), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "📌 고정",
                            color = Color(0xFFE8890C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                val (bg, fg) = categoryColors(post.category)
                Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        Categories.short(post.category),
                        color = fg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (isNew) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = AppColors.NewBadge, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "NEW",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    post.createdAt.toRelative(),
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                post.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                post.content,
                fontSize = 13.sp,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 첨부 이미지 썸네일 (최대 3장, 초과분은 +N)
            val images = post.attachments.filter { it.isImage }
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    images.take(3).forEachIndexed { i, img ->
                        if (i == 2 && images.size > 3) {
                            Box(
                                Modifier.size(74.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF16265C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+${images.size - 2}", color = Color.White,
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            AsyncImage(
                                model = img.url, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(74.dp).clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${post.category} · ${post.authorName} · 조회 ${post.views}",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
                val hasVideo = post.attachments.any { it.isVideo } || post.links.any { it.isYoutube }
                if (hasVideo) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = Color(0xFFFDEAEA), shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, null,
                                tint = Color(0xFFE53935), modifier = Modifier.size(12.dp))
                            Text(" 영상", fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFFE53935))
                        }
                    }
                }
                if (post.attachments.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = AppColors.Background, shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, null,
                                tint = AppColors.Primary, modifier = Modifier.size(12.dp))
                            Text(" ${post.attachments.size}", fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = AppColors.Primary)
                        }
                    }
                }
            }
        }
    }
}
