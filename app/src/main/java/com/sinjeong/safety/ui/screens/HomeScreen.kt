package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.sinjeong.safety.BuildConfig
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
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
import com.sinjeong.safety.data.effectiveDate
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
    // 올린 시각이 아니라 '자료 날짜'로 판단한다.
    // 과거 자료를 오늘 올려도 NEW가 붙어 최신 공지를 밀어내지 않도록.
    val base = effectiveDate?.toDate()?.time ?: return false
    val within3Days = System.currentTimeMillis() - base < 3L * 24 * 60 * 60 * 1000
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
    onRegulationClick: () -> Unit,
    onAskClick: () -> Unit,
    onQuestionsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val posts by vm.filteredPosts.collectAsState()
    // 아카이브 목록도 여기서 구독한다. LazyColumn 안에서는 collectAsState를 쓸 수 없다.
    val archiveYears by vm.archive.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val favoritesOnly by vm.showFavoritesOnly.collectAsState()
    val weatherWarning by vm.weatherWarning.collectAsState()
    val readIds by vm.readIds.collectAsState()
    val listState = rememberLazyListState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    // 지난 자료 보기 모드 · 펼친 '연도-월' 키 목록
    var showArchive by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }

    // 전환 버튼이 목록 아래에 있으므로, 누르면 결과가 시작되는 위치로 올려준다.
    // (헤더·배너·검색·카테고리·규정물어보기 5개 다음이 목록의 첫 항목)
    LaunchedEffect(showArchive) {
        listState.animateScrollToItem(if (showArchive) 5 else 0)
    }

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
                    weatherWarning = weatherWarning,
                    onShieldClick = { if (isAdmin) showLogoutDialog = true else onLoginClick() },
                    onSettingsClick = onSettingsClick
                )
            }
            // 기상특보 발효 중이면 배지 (없으면 아무것도 안 보임)
            weatherWarning?.let { warning ->
                item { WeatherWarningBanner(warning) }
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

            // 규정에 물어보기 (게시글 검색바와 헷갈리지 않게 카테고리 아래에 둔다)
            item {
                AskEntryBanner(
                    onClick = onAskClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // 질의응답 (규정에 물어보기 바로 아래 — 물어볼 곳 두 개를 붙여 둔다)
            item {
                QuestionEntryBanner(
                    onClick = onQuestionsClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // 즐겨찾기만 모아 보기
            item {
                FavoriteFilterChip(
                    on = favoritesOnly,
                    count = favoriteIds.size,
                    onToggle = { vm.setShowFavoritesOnly(!favoritesOnly) }
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
            } else if (showArchive) {
                // 지난 자료: 연도 ▸ 월 접기 목록
                items(archiveYears, key = { it.year }) { y ->
                    ArchiveYearBlock(
                        year = y,
                        expandedKeys = expandedKeys,
                        onToggle = { k ->
                            expandedKeys = if (k in expandedKeys) expandedKeys - k else expandedKeys + k
                        },
                        readIds = readIds,
                        onPostClick = onPostClick
                    )
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


            // 피드 ↔ 지난 자료 전환 (의견 보내기 바로 위)
            item {
                ArchiveToggle(
                    showArchive = showArchive,
                    onToggle = { showArchive = !showArchive }
                )
            }

            // 목록 끝: 의견 보내기
            item { FeedbackCard() }
            item { Spacer(Modifier.height(12.dp)) }
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
private fun HeaderBar(
    isAdmin: Boolean,
    weatherWarning: String?,
    onShieldClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
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
        // 캘린더 아이콘 → 신정승무캘린더 앱 열기
        CalendarButton()
        Spacer(Modifier.width(8.dp))

        // 날씨 아이콘 — 특보 발효 중이면 주황색으로 은은하게 맥박친다.
        // 빠른 점멸은 매일 보는 화면에선 금방 피로해져 무시하게 되므로 쓰지 않는다.
        var showWeatherDialog by remember { mutableStateOf(false) }
        val warnActive = weatherWarning != null
        val pulse = if (warnActive) {
            val t = rememberInfiniteTransition(label = "weatherPulse")
            t.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
                label = "pulse"
            ).value
        } else 1f
        Surface(
            shape = CircleShape,
            color = if (warnActive) Color(0xFFFF8A3D).copy(alpha = pulse * 0.28f) else Color.White,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (warnActive) Color(0xFFFF8A3D).copy(alpha = pulse) else AppColors.Divider
            ),
            modifier = Modifier.size(42.dp).clickable { showWeatherDialog = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (warnActive) "⚠️" else "⛅", fontSize = 19.sp)
            }
        }
        Spacer(Modifier.width(8.dp))

        if (showWeatherDialog) {
            AlertDialog(
                onDismissRequest = { showWeatherDialog = false },
                title = { Text(if (warnActive) "기상특보 발효 중" else "기상특보 없음") },
                text = {
                    Text(
                        if (warnActive)
                            "서울 " + weatherWarning + " 발효 중입니다.\n\n폭염 및 이례상황 발생 시 관제보고 철저!"
                        else
                            "현재 서울에 발효 중인 기상특보가 없습니다.\n(1시간 간격으로 갱신됩니다)"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showWeatherDialog = false }) { Text("확인") }
                }
            )
        }

        // 설정 아이콘 → 설정 화면
        Surface(
            shape = CircleShape,
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
            modifier = Modifier.size(42.dp).clickable(onClick = onSettingsClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "설정",
                    tint = AppColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))

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


/** 개선 의견을 앱 관리자에게 메일로 보낸다. 기기·앱 버전을 함께 담아야 문제 재현이 쉽다. */
private const val FEEDBACK_EMAIL = "kass7942@gmail.com"

@Composable
private fun FeedbackCard() {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable {
                val body = buildString {
                    append("\n\n\n───────────────\n")
                    append("아래 정보는 문제 확인용입니다. 지우지 말아주세요.\n")
                    append("앱 버전: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("기기: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("안드로이드: ${Build.VERSION.RELEASE}\n")
                }
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                    putExtra(Intent.EXTRA_SUBJECT, "[안전앱 의견] ")
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, "의견 보내기"))
                } catch (e: Exception) {
                    Toast.makeText(context, "메일 앱을 찾을 수 없어요", Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83D\uDCAC", fontSize = 18.sp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "의견 보내기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "불편한 점이나 있으면 좋겠는 기능을 알려주세요",
                    fontSize = 11.5.sp,
                    color = AppColors.TextSecondary
                )
            }
            Text("\u203A", fontSize = 16.sp, color = AppColors.TextSecondary)
        }
    }
}


/** 신정승무캘린더 앱의 패키지 이름 (플레이스토어 등록명과 동일해야 한다) */
private const val CALENDAR_PACKAGE = "com.sinjeong.crewcalendar"

/**
 * 헤더의 작은 달력 아이콘. 오늘 날짜가 숫자로 보여서 살아 있는 느낌을 준다.
 * 누르면 신정승무캘린더 앱을 열고, 설치돼 있지 않으면 플레이스토어로 안내한다.
 */
@Composable
private fun CalendarButton() {
    val context = LocalContext.current
    // 신정승무캘린더의 실제 앱 아이콘을 그대로 보여준다.
    // 두 앱이 형제 앱임을 한눈에 알 수 있고, 누르면 그 앱이 열린다는 것도 직관적이다.
    Image(
        painter = painterResource(R.drawable.icon_crewcalendar),
        contentDescription = "신정승무캘린더 열기",
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable {
                val launch = context.packageManager.getLaunchIntentForPackage(CALENDAR_PACKAGE)
                if (launch != null) {
                    context.startActivity(launch)
                } else {
                    // 아직 안 깔려 있으면 스토어로 (스토어 앱이 없으면 웹으로)
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$CALENDAR_PACKAGE"))
                        )
                    } catch (e: Exception) {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=$CALENDAR_PACKAGE")
                                )
                            )
                        } catch (e2: Exception) {
                            Toast.makeText(context, "캘린더 앱을 열 수 없어요", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    )
}


/** 피드 ↔ 지난 자료 전환 버튼 */
@Composable
private fun ArchiveToggle(showArchive: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (showArchive) AppColors.Primary else Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onToggle)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (showArchive) "\uD83D\uDCC2" else "\uD83D\uDCC1", fontSize = 17.sp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (showArchive) "최신 공지로 돌아가기" else "지난 자료 보기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showArchive) Color.White else AppColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (showArchive) "연도·월별로 정리된 목록을 보고 있어요"
                    else "연도와 월로 묶어서 지난 자료를 찾아봐요",
                    fontSize = 11.5.sp,
                    color = if (showArchive) Color.White.copy(alpha = 0.85f) else AppColors.TextSecondary
                )
            }
            Text(
                if (showArchive) "\u2715" else "\u203A",
                fontSize = 16.sp,
                color = if (showArchive) Color.White else AppColors.TextSecondary
            )
        }
    }
}

/** 아카이브 한 해 블록: 2026년 ▸ 3월(12건) 형태로 접었다 펼친다 */
@Composable
private fun ArchiveYearBlock(
    year: MainViewModel.ArchiveYear,
    expandedKeys: Set<String>,
    onToggle: (String) -> Unit,
    readIds: Set<String>,
    onPostClick: (Post) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        // 연도 헤더
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle("y${'$'}{year.year}") }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if ("y${'$'}{year.year}" in expandedKeys) "\u25BE" else "\u25B8",
                fontSize = 13.sp, color = AppColors.Primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${'$'}{year.year}년",
                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary
            )
            Spacer(Modifier.width(7.dp))
            Text("${'$'}{year.total}건", fontSize = 12.sp, color = AppColors.TextHint)
        }
        if ("y${'$'}{year.year}" in expandedKeys) {
            year.months.forEach { m ->
                val key = "m${'$'}{year.year}-${'$'}{m.month}"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(key) }
                        .padding(start = 20.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (key in expandedKeys) "\u25BE" else "\u25B8",
                        fontSize = 12.sp, color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${'$'}{m.month}월",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("(${'$'}{m.posts.size}건)", fontSize = 12.sp, color = AppColors.TextHint)
                }
                if (key in expandedKeys) {
                    m.posts.forEach { post ->
                        ArchiveRow(post = post, isNew = post.isNew(readIds)) { onPostClick(post) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider(color = AppColors.Divider)
    }
}

/** 아카이브 목록의 한 줄 (피드 카드보다 조밀하게) */
@Composable
private fun ArchiveRow(post: Post, isNew: Boolean, onClick: () -> Unit) {
    val (bg, fg) = categoryColors(post.category)
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 2.dp, top = 3.dp, bottom = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = bg, shape = RoundedCornerShape(5.dp)) {
                Text(
                    Categories.short(post.category),
                    color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                post.title,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isNew) {
                Spacer(Modifier.width(6.dp))
                Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.NewBadge)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                post.effectiveDate?.toDate()?.let {
                    SimpleDateFormat("MM.dd", Locale.KOREA).format(it)
                } ?: "",
                fontSize = 11.sp, color = AppColors.TextHint
            )
        }
    }
}

/** 즐겨찾기만 모아 보기 칩 */
@Composable
private fun FavoriteFilterChip(on: Boolean, count: Int, onToggle: () -> Unit) {
    if (count == 0 && !on) return   // 별표한 글이 하나도 없으면 자리만 차지하므로 숨긴다

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (on) Color(0xFFF5B301) else Color.White,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (on) Color(0xFFF5B301) else AppColors.Divider
            ),
            modifier = Modifier.clickable(onClick = onToggle)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (on) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (on) Color.White else Color(0xFFF5B301),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "즐겨찾기 " + count + "건",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) Color.White else AppColors.TextPrimary
                )
            }
        }
    }
}

/** 기상특보 배지 — 발효 중일 때만 홈 상단에 나타난다 */
@Composable
private fun WeatherWarningBanner(text: String) {
    Surface(
        color = Color(0xFFFFF1E6),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠️", fontSize = 17.sp)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    "서울 " + text + " 발효 중",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8A3D00)
                )
                Text(
                    "이례상황 발생 시 관제보고 철저",
                    fontSize = 12.sp,
                    color = Color(0xFFB05E1E)
                )
            }
        }
    }
}
