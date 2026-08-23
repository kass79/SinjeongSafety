package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import android.content.Context
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
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
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
import com.sinjeong.safety.data.Briefing
import com.sinjeong.safety.data.BriefingItem
import com.sinjeong.safety.data.Categories
import com.sinjeong.safety.data.Post
import com.sinjeong.safety.data.effectiveDate
import com.sinjeong.safety.data.Tags
import com.sinjeong.safety.data.WeatherNow
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
    onQuestionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBriefingWrite: () -> Unit,
    onBriefingList: () -> Unit
) {
    val posts by vm.filteredPosts.collectAsState()
    val briefings by vm.briefings.collectAsState()
    // 아카이브 목록도 여기서 구독한다. LazyColumn 안에서는 collectAsState를 쓸 수 없다.
    val archiveYears by vm.archive.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val favoritesOnly by vm.showFavoritesOnly.collectAsState()
    val weather by vm.weather.collectAsState()
    val readIds by vm.readIds.collectAsState()
    val listState = rememberLazyListState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    // 지난 자료 보기 모드 · 펼친 '연도-월' 키 목록
    var showArchive by remember { mutableStateOf(false) }
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }

    // 전환 버튼이 목록 아래에 있으므로, 누르면 결과가 시작되는 위치로 올려준다.
    // 목록 앞에 놓인 고정 항목 수다 — 헤더·마스코트·검색·카테고리·출무점호·즐겨찾기칩 여섯.
    // 위에 item을 하나 더 끼우면 이 숫자도 같이 올려야 엉뚱한 데로 스크롤되지 않는다.
    LaunchedEffect(showArchive) {
        listState.animateScrollToItem(if (showArchive) 6 else 0)
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
                    weather = weather,
                    onShieldClick = { if (isAdmin) showLogoutDialog = true else onLoginClick() },
                    onSettingsClick = onSettingsClick
                )
            }
            // 기상특보는 헤더의 날씨 칩 위 작은 뱃지로 붙는다(전체폭 배너는 자리를 너무 먹었다)

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
                    onSelectAll = vm::selectAll,
                    onQuestions = onQuestionsClick
                )
            }

            // 출무점호 — 출근해서 제일 먼저 볼 것이므로 카테고리 바로 아래.
            item {
                // 고르는 순서가 중요하다.
                // 1) 오늘 자가 있으면 무조건 그것. 방금 올린 점호가 항목이 적다는 이유로 밀리면
                //    관리자 눈에는 "올렸는데 안 보인다"가 된다(실제로 그런 사고가 났다).
                // 2) 오늘 자가 없을 때만, 내용이 있는 최신 건을 고른다. 항목 하나 없는 빈 문서가
                //    맨 위에 있으면 홈이 텅 빈 카드로 보이기 때문이다.
                // 3) 그것도 없으면 그냥 최신.
                val todayKey = remember {
                    SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(java.util.Date())
                }
                val briefingToShow = briefings.firstOrNull { it.id == todayKey }
                    ?: briefings.firstOrNull {
                        it.items.isNotEmpty() || it.attachments.isNotEmpty() || it.links.isNotEmpty()
                    }
                    ?: briefings.firstOrNull()
                BriefingCard(
                    briefing = briefingToShow,
                    isAdmin = isAdmin,
                    onWrite = onBriefingWrite,
                    onList = onBriefingList
                )
            }

            // 규정에 물어보기 배너는 뺐다 — 운전규정 화면 안에 이미 같은 입구가 있어 중복이었다.
            // 질의응답도 배너 대신 위 카테고리 줄의 다섯 번째 타일로 옮겼다.

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
    weather: WeatherNow?,
    onShieldClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    // 폴드7 접은 화면(약 344dp)에서는 오른쪽 아이콘들이 폭을 먹어 제목에 남는 자리가 거의 없다.
    // 좁으면 글자·간격을 한 단계씩 줄인다. 캘린더 아이콘을 빼(배너 클릭으로 옮김) 42dp가 돌아온 만큼
    // 제목은 15sp까지 쥐어짜지 않고 16.5sp로 되돌렸다.
    val narrow = LocalConfiguration.current.screenWidthDp < 380
    val iconGap = if (narrow) 5.dp else 8.dp

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
                .size(if (narrow) 40.dp else 48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFE8F0FC))
                .border(2.dp, Color.White, RoundedCornerShape(15.dp))
        )
        Spacer(Modifier.width(if (narrow) 7.dp else 11.dp))
        Column(Modifier.weight(1f)) {
            // maxLines 없이 두면 폭이 모자랄 때 제목이 세 줄까지 늘어나 헤더가 화면을 먹는다
            Text(
                "신정승무사업소",
                fontSize = if (narrow) 16.5.sp else 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                    if (narrow) {
                        if (isAdmin) "관리자 모드" else "안전정보 공유중"
                    } else {
                        if (isAdmin) "관리자님 (관리자 모드)" else "실시간 안전정보 공유중"
                    },
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 날씨 칩 — 다음(daum) 날씨 위젯처럼 아이콘+기온만 작게.
        // 특보가 있으면 그 위에 작은 주황 뱃지가 한 줄 붙는다(맥박 애니메이션은 뺐다.
        // 칩이 작아지고 뱃지가 눈에 띄므로 움직임까지 얹을 값어치가 없다).
        var showWeatherDialog by remember { mutableStateOf(false) }
        val warning = weather?.warning
        val warnActive = warning != null
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { showWeatherDialog = true }
        ) {
            if (warning != null) {
                Text(
                    warning,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8A3D00),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        // 헤더 한 줄에 캘린더·설정·방패가 같이 있어 가로가 빠듯하다.
                        // 특보가 둘 이상이면 길어지므로 폭을 묶고 말줄임한다.
                        .widthIn(max = 64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFF1E6))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
                Spacer(Modifier.height(2.dp))
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (warnActive) Color(0xFFFF8A3D) else AppColors.Divider
                )
            ) {
                Text(
                    // 기온을 못 받았으면 이모지만
                    (weather?.emoji ?: "⛅") + (weather?.tempC?.let { " $it°" } ?: ""),
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.width(iconGap))

        if (showWeatherDialog) {
            AlertDialog(
                onDismissRequest = { showWeatherDialog = false },
                title = { Text(if (warnActive) "기상특보 발효 중" else "오늘 날씨") },
                text = {
                    Text(
                        buildString {
                            // 어느 지점 기준인지는 저장소가 정해 준다("현재 위치" / "신정동").
                            // 설정을 켰어도 위치를 못 얻으면 신정동으로 떨어지므로 여기서 단정하면 안 된다.
                            weather?.tempC?.let {
                                append(weather?.placeLabel ?: "신정동")
                                    .append(" 기준 현재 ").append(it).append("℃\n\n")
                            }
                            append(
                                if (warning != null)
                                    "서울 " + warning + " 발효 중입니다.\n\n폭염 및 이례상황 발생 시 관제보고 철저!"
                                else
                                    "현재 서울에 발효 중인 기상특보가 없습니다.\n(1시간 간격으로 갱신됩니다)"
                            )
                        }
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
        Spacer(Modifier.width(iconGap))

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

// ── 마스코트 배너: 컴팩트 와이드 (이미지 전체가 캘린더 앱 바로가기) ────
@Composable
private fun MascotBanner() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(112.dp)
                // clip 을 clickable 보다 먼저 걸어야 눌렀을 때 리플이 둥근 모서리를 넘지 않는다
                .clip(RoundedCornerShape(20.dp))
                .clickable { openCalendarApp(context) }
        ) {
            // 배너 이미지에 이미 "슬기로운 승무생활" 문구가 들어 있고, 오른쪽 아래는
            // "신정승무사업소" 로고 자리라 그림 위에 힌트를 얹을 빈 곳이 없다.
            // 그래서 안내는 그림 '밖'(아래)에 한 줄로 둔다 — 오버레이 금지 규칙도 지키고
            // 누를 수 있다는 것도 알린다. 헤더에서 캘린더 아이콘을 뺐으므로 이 안내가 유일한 단서다.
            Image(
                painter = painterResource(R.drawable.banner_main),
                contentDescription = "슬기로운 승무생활 - 누르면 신정승무캘린더 앱이 열립니다",
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.matchParentSize()
            )
        }
        Text(
            "위 배너를 누르면 신정승무캘린더가 열립니다 ›",
            fontSize = 10.5.sp,
            color = AppColors.TextHint,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 20.dp, top = 4.dp)
        )
    }
}

// ── 검색창 ──────────────────────────────────────────────────────
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    // 기본 높이(56dp)는 홈에서 자리를 너무 먹는다. 그런데 M3 OutlinedTextField 는 최소 높이 56dp와
    // 거기에 맞춘 고정 content padding 을 갖고 있어서, 높이만 46dp로 눌러 놓으면 글자·placeholder·
    // 아이콘이 세로로 눌리거나 잘린다("글자 크기가 안 맞는다"의 진짜 원인).
    // 그래서 기본값과 싸우는 대신 BasicTextField + 직접 만든 껍데기로 높이를 우리가 정한다.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (focused) AppColors.Primary else AppColors.Divider
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(46.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                cursorBrush = SolidColor(AppColors.Primary),
                interactionSource = interactionSource,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    // 비어 있을 때만 예시를 겹쳐 그린다. 무엇을 칠 수 있는지 감이 오도록
                    // 설명 대신 실제 검색어 예시 하나를 보여 준다.
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("예) 승강장안전문", fontSize = 14.sp, color = AppColors.TextSecondary)
                        }
                        inner()
                    }
                }
            )
        }
    }
}

// ── 카테고리 카드 ───────────────────────────────────────────────
private data class CategoryUi(val name: String, val short: String, val icon: ImageVector, val color: Color)

/**
 * 질의응답 타일의 이름표. 게시물 분류(Categories)가 아니라 화면 이동이므로
 * Categories 에 넣지 않는다 — 넣으면 필터 후보가 되어 빈 목록이 나온다.
 * Categories 의 어떤 값과도 겹치지 않아 selected 비교에서 자동으로 탈락한다.
 */
private const val QNA_TILE = "__qna__"

@Composable
private fun CategoryRow(
    selected: String?,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onQuestions: () -> Unit
) {
    val cats = listOf(
        CategoryUi(Categories.HUMAN_ERROR, "인적오류", Icons.Default.Warning, Color(0xFFF57C00)),
        CategoryUi(Categories.EDU_VIDEO, "교육영상", Icons.Default.PlayCircle, Color(0xFF1976D2)),
        CategoryUi(Categories.REGULATION, "운전규정", Icons.Default.MenuBook, Color(0xFF388E3C)),
        CategoryUi(Categories.NOTICE, "전달사항", Icons.Default.Campaign, Color(0xFFC79A00)),
        CategoryUi(QNA_TILE, "질의응답", Icons.Default.Forum, Color(0xFF7B1FA2))
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
                    .clickable { if (cat.name == QNA_TILE) onQuestions() else onToggle(cat.name) }
            ) {
                // 타일이 5개로 늘어 360dp 폭에서 한 칸이 50dp 아래로 떨어진다.
                // 아이콘 박스·글자·좌우 여백을 한 단계씩 줄여야 "질의응답"이 한 줄에 들어간다.
                Column(
                    Modifier.padding(vertical = 10.dp, horizontal = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(cat.color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        cat.short,
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
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
 * 신정승무캘린더 앱 열기. 설치돼 있지 않으면 플레이스토어(없으면 웹)로 안내한다.
 *
 * 원래 헤더의 달력 아이콘(CalendarButton)이 하던 일인데, 헤더 아이콘이 너무 많아져서
 * 아이콘을 없애고 마스코트 배너 전체를 누르는 것으로 옮겼다. 로직은 그대로다.
 */
private fun openCalendarApp(context: Context) {
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

/** 출무점호 항목 글자색. 원본 한글 문서가 빨간 글씨라 그 느낌을 그대로 살린다. */
private val BriefingRed = Color(0xFFD32F2F)
// 하위 항목은 한 톤 옅게 — 계층이 색으로도 구분돼야 훑기 좋다.
private val BriefingRedSub = Color(0xFFE05252)

/**
 * 출무점호 카드. 홈에서는 최신 한 건을, 작성 화면에서는 미리보기로 같은 모양을 쓴다.
 *
 * 오늘 것이 아니어도 최신 것을 보여주고 날짜 줄을 그대로 띄운다 — 언제 것인지
 * 모른 채 지적사항만 보는 게 더 위험하기 때문이다.
 *
 * @param onList null이면 미리보기라 '지난 점호 보기' 줄을 달지 않는다.
 * @param collapseLimit 이 수를 넘는 항목은 접는다. 미리보기는 전부 봐야 하므로 크게 넘긴다.
 */
@Composable
fun BriefingCard(
    briefing: Briefing?,
    isAdmin: Boolean,
    onWrite: () -> Unit,
    onList: (() -> Unit)? = null,
    // 계층이 생기면서 한 건의 줄 수가 늘었다. 5줄에서 자르면 개요만 보이고 원인이 잘린다.
    collapseLimit: Int = 8
) {
    // 아직 한 건도 없을 때: 관리자에게만 올려달라고 하고, 승무원에게는 그리지 않는다
    // (빈 카드가 자리만 먹는다).
    if (briefing == null) {
        if (!isAdmin) return
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppColors.Surface,
            // 아래 본 카드와 같은 옅은 초록 테두리로 맞춘다
            border = androidx.compose.foundation.BorderStroke(
                1.dp, AppColors.TagOpsFg.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable(onClick = onWrite)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "오늘 출무점호를 올려주세요",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "출무점호 올리기",
                    tint = AppColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    // 오늘 것인지 지난 것인지 제목·뱃지·하단 안내가 모두 같은 판정을 써야 해서 여기서 한 번만 구한다.
    // 문서 id가 yyyyMMdd라 오늘 날짜와 문자열 비교로 끝난다.
    val todayId = remember { SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(java.util.Date()) }
    val isToday = briefing.id == todayId
    val hidden = (briefing.items.size - collapseLimit).coerceAtLeast(0)
    val shown = if (expanded || hidden == 0) briefing.items else briefing.items.take(collapseLimit)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
        // 출무점호는 출근해서 제일 먼저 볼 것이라 다른 카드와 구별돼야 하지만,
        // 진하면 매일 보는 화면에서 피로해진다. 초록을 아주 옅게(25%)만 태운다.
        border = androidx.compose.foundation.BorderStroke(
            1.dp, AppColors.TagOpsFg.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 오늘 것인지 지난 것인지 제목에서 바로 알 수 있어야 한다.
                Text(
                    if (isToday) "오늘 출무점호" else "최근 출무점호",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.Primary
                )
                Spacer(Modifier.width(8.dp))
                // 제목만으로는 '최근'을 흘려보기 쉬워서, 지난 자료일 땐 날짜 앞에 눈에 띄는 표를 단다.
                if (!isToday) {
                    Text(
                        "지난 자료",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8A3D00),
                        modifier = Modifier
                            .background(Color(0xFFFFF1E6), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    briefing.dateText,
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isAdmin) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "출무점호 올리기",
                        tint = AppColors.Primary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onWrite)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            // 첨부·링크만 있고 지적사항이 없는 점호도 있다. 그 자리가 그냥 비면
            // 로딩이 덜 된 건지 원래 없는 건지 알 수가 없어 한 줄로 못 박아 준다.
            if (shown.isEmpty()) {
                Text(
                    "등록된 지적사항이 없습니다",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
            } else {
                shown.forEach { BriefingItemRow(it) }
            }

            if (hidden > 0) {
                Text(
                    if (expanded) "접기" else "+${hidden}개 더 보기",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(top = 4.dp, bottom = 2.dp)
                )
            }

            BriefingExtras(briefing)

            if (briefing.footer.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = AppColors.Divider)
                Spacer(Modifier.height(8.dp))
                Text(
                    briefing.footer,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary
                )
            }

            if (onList != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "지난 점호 보기 ›",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextSecondary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onList)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }

            // 오늘 자가 아직 없으면 관리자에게만 올릴 자리를 준다.
            // 승무원에게는 어차피 권한이 없어 눌러도 소용없는 버튼이라 감춘다.
            if (!isToday && isAdmin) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "오늘 출무점호 올리기",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TagOpsFg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.TagOpsBg)
                        .clickable(onClick = onWrite)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 점호 항목 한 줄. 홈 카드와 '지난 점호' 펼침이 같아야 해서 여기 한 곳에 둔다.
 * 최상위만 굵게 두는 편이 제목 ▸ 내용 구조가 눈에 바로 들어온다.
 */
@Composable
fun BriefingItemRow(item: BriefingItem) {
    val top = item.level == 0
    Row(Modifier.padding(start = (item.level * 12).dp, bottom = 3.dp)) {
        Text(
            if (top) "•" else "-",
            fontSize = if (top) 14.sp else 13.sp, lineHeight = 20.sp,
            color = if (top) BriefingRed else BriefingRedSub
        )
        Spacer(Modifier.width(6.dp))
        Text(
            item.text,
            fontSize = if (top) 14.sp else 13.sp, lineHeight = 20.sp,
            fontWeight = if (top) FontWeight.Bold else FontWeight.Normal,
            color = if (top) BriefingRed else BriefingRedSub
        )
    }
}

/**
 * 점호에 붙은 첨부·링크.
 * 상세 화면의 첨부 카드들은 그 파일 전용(private)이라 가져올 수 없다. 점호는 아침에
 * 훑고 지나가는 자리라 사진만 그대로 펼쳐 보이고 나머지는 눌러서 여는 한 줄로 둔다.
 */
@Composable
fun BriefingExtras(briefing: Briefing) {
    if (briefing.attachments.isEmpty() && briefing.links.isEmpty()) return

    Spacer(Modifier.height(8.dp))
    briefing.attachments.forEach { att ->
        if (att.isImage) {
            AsyncImage(
                model = att.url,
                contentDescription = att.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            BriefingOpenRow(
                icon = if (att.isVideo) Icons.Default.PlayCircle else Icons.Default.AttachFile,
                label = att.name,
                url = att.url
            )
        }
    }
    briefing.links.forEach { link ->
        BriefingOpenRow(
            icon = if (link.isYoutube) Icons.Default.PlayCircle else Icons.Default.Link,
            label = link.title.ifBlank { link.url },
            url = link.url
        )
    }
}

@Composable
private fun BriefingOpenRow(icon: ImageVector, label: String, url: String) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.5.sp,
            color = AppColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

