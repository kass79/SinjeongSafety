package com.sinjeong.safety.ui.screens

import android.content.Intent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.ManualFormat
import com.sinjeong.safety.data.ManualRepository
import com.sinjeong.safety.data.ManualToday
import com.sinjeong.safety.data.RegArticle
import com.sinjeong.safety.data.RegBook
import com.sinjeong.safety.data.RegulationRepository
import com.sinjeong.safety.data.TodayManualCard
import com.sinjeong.safety.data.TodayReg
import com.sinjeong.safety.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegulationScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onAsk: (String) -> Unit = {},
    onOpenManual: () -> Unit = {},
    onOpenSection: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val manualReady by vm.manualReady.collectAsState()
    val empNo by vm.crewEmpNo.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val loggedIn = empNo != null || isAdmin
    // 오늘의 비상조치 — 매뉴얼+today.json 이 기기에 있고 로그인 상태일 때만 생긴다.
    // null 이면 예전과 똑같이 오늘의 규정 한 장만 보인다(페이저·점 없음).
    var todayManual by remember { mutableStateOf<TodayManualCard?>(null) }
    LaunchedEffect(manualReady, loggedIn) {
        todayManual = if (manualReady && loggedIn) {
            val doc = ManualRepository.load(context)
            val data = ManualRepository.loadToday(context)
            withContext(Dispatchers.Default) {
                ManualToday.cardFor(data, doc, Calendar.getInstance())
            }
        } else null
    }
    var books by remember { mutableStateOf<List<RegBook>>(emptyList()) }
    var today by remember { mutableStateOf<TodayReg?>(null) }
    var isWeekend by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(0) }

    // 내비게이션 상태: 0=목록, 1=조문목록, 2=조문상세
    var selectedBook by remember { mutableStateOf<RegBook?>(null) }
    var selectedArticle by remember { mutableStateOf<Pair<String, RegArticle>?>(null) }
    var selectedFun by remember { mutableStateOf<String?>(null) }
    // 조문 상세에서 형광펜으로 강조할 검색어
    var highlightTerm by remember { mutableStateOf("") }
    // 즐겨찾기 / 최근 본 조문
    var favArticles by remember { mutableStateOf<List<Pair<String, RegArticle>>>(emptyList()) }
    var recentArticles by remember { mutableStateOf<List<Pair<String, RegArticle>>>(emptyList()) }
    var isFav by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Pair<String, RegArticle>>>(emptyList()) }

    LaunchedEffect(Unit) {
        books = RegulationRepository.loadBooks(context)
        total = books.sumOf { it.articles.size }
        isWeekend = RegulationRepository.isWeekend()
        today = RegulationRepository.todayRegulation(context)
    }

    LaunchedEffect(query) {
        searchResults = if (query.isBlank()) emptyList()
        else RegulationRepository.search(context, query.trim())
    }

    // 조문을 열면 최근 목록에 기록하고, 목록으로 돌아오면 즐겨찾기·최근을 다시 읽는다
    LaunchedEffect(selectedArticle, books) {
        if (books.isEmpty()) return@LaunchedEffect
        val cur = selectedArticle
        if (cur != null) {
            RegulationRepository.addRecent(context, cur.first, cur.second.num)
            isFav = RegulationRepository.isFavorite(context, cur.first, cur.second.num)
        } else {
            favArticles = RegulationRepository.getFavoriteArticles(context)
            recentArticles = RegulationRepository.getRecentArticles(context)
        }
    }

    // 한 단계 뒤로: 조문상세 → 조문목록 → 규정집목록 → 화면 닫기
    // 화면 안 ← 버튼과 폰 시스템 뒤로가기가 똑같이 이 동작을 쓴다
    val stepBack: () -> Unit = {
        when {
            selectedArticle != null -> { selectedArticle = null; selectedFun = null; highlightTerm = "" }
            selectedBook != null -> selectedBook = null
            else -> onBack()
        }
    }
    // 첫 단계(규정집 목록)에서는 꺼 둔다.
    // 꺼야 시스템 기본 동작(NavHost가 화면 닫기 + predictive back 미리보기)이 그대로 산다.
    BackHandler(enabled = selectedArticle != null || selectedBook != null) { stepBack() }

    val title = when {
        selectedArticle != null -> "조문"
        selectedBook != null -> selectedBook!!.name
        else -> "운전규정/비상조치"
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = stepBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = AppColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                selectedArticle != null -> ArticleDetail(
                    regName = selectedArticle!!.first,
                    article = selectedArticle!!.second,
                    funText = selectedFun,
                    query = highlightTerm,
                    isFavorite = isFav,
                    onToggleFavorite = {
                        val cur = selectedArticle
                        if (cur != null) {
                            isFav = RegulationRepository.toggleFavorite(context, cur.first, cur.second.num)
                        }
                    }
                )
                selectedBook != null -> BookArticleList(
                    book = selectedBook!!,
                    onArticle = { a, term ->
                        selectedArticle = selectedBook!!.name to a
                        selectedFun = null
                        highlightTerm = term
                    }
                )
                else -> RegulationHome(
                    books = books, today = today, isWeekend = isWeekend, total = total,
                    todayManual = todayManual, onOpenSection = onOpenSection,
                    onAsk = onAsk,
                    manualReady = manualReady,
                    onOpenManual = onOpenManual,
                    query = query, onQuery = { query = it },
                    searchResults = searchResults,
                    favorites = favArticles,
                    recents = recentArticles,
                    onSavedArticle = { name, a ->
                        selectedArticle = name to a
                        selectedFun = null
                        highlightTerm = ""
                    },
                    onBook = { selectedBook = it },
                    onSearchArticle = { name, a ->
                        selectedArticle = name to a
                        selectedFun = null
                        highlightTerm = query
                    },
                    onTodayClick = { t ->
                        selectedArticle = t.reg to RegArticle(t.num, t.title, t.body)
                        selectedFun = t.fun_
                        highlightTerm = ""
                    }
                )
            }
        }
    }
}

@Composable
private fun RegulationHome(
    books: List<RegBook>, today: TodayReg?, isWeekend: Boolean, total: Int,
    todayManual: TodayManualCard?, onOpenSection: (String) -> Unit,
    onAsk: (String) -> Unit,
    manualReady: Boolean,
    onOpenManual: () -> Unit,
    query: String, onQuery: (String) -> Unit,
    searchResults: List<Pair<String, RegArticle>>,
    favorites: List<Pair<String, RegArticle>>,
    recents: List<Pair<String, RegArticle>>,
    onSavedArticle: (String, RegArticle) -> Unit,
    onBook: (RegBook) -> Unit,
    onSearchArticle: (String, RegArticle) -> Unit,
    onTodayClick: (TodayReg) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        // 오늘의 규정
        item {
            Spacer(Modifier.height(14.dp))
            // 주말·자료 없음·판 불일치면 todayManual 이 null → 예전 그대로 한 장
            if (todayManual != null && today != null && !isWeekend) {
                TodayPager(today, todayManual, onTodayClick, onOpenSection)
            } else {
                TodayCard(today, isWeekend, onTodayClick)
            }
        }
        // 물어보기 3범위 (오늘의 규정 바로 아래 / 조문 검색창 바로 위)
        item { AskEntryRow(onAsk) }
        // 검색
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                    .background(AppColors.Surface, RoundedCornerShape(15.dp))
                    .padding(horizontal = 15.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                TextField(
                    value = query, onValueChange = onQuery,
                    placeholder = { Text("조문 검색 (예: 신호, 휴가, 근무)", color = AppColors.TextHint, fontSize = 13.5.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (query.isNotBlank()) {
            // 검색 결과
            item {
                Text(
                    "\"$query\" 검색결과 ${searchResults.size}건",
                    fontSize = 11.5.sp, color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }
            items(searchResults.take(50)) { (name, a) ->
                ArticleRow(prefix = "$name ${a.num}", title = a.title, body = a.body,
                    query = query, onClick = { onSearchArticle(name, a) })
            }
            if (searchResults.isEmpty()) {
                item { EmptyText("검색 결과가 없어요") }
            }
        } else {
            // 규정 헤더 + 규정집 목록
            item {
                Row(
                    Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("규정", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("총 ${total}개 조문", fontSize = 12.sp, color = AppColors.TextHint,
                        modifier = Modifier.padding(bottom = 1.dp))
                }
            }
            // 10번째 문서 — 규정집 9권과 같은 줄에 서지만 색으로 구분한다.
            // 사고 났을 때 제일 먼저 여는 자료라 목록 맨 위다.
            item { ManualCard(ready = manualReady, onClick = onOpenManual) }
            items(books) { book -> BookCard(book, onClick = { onBook(book) }) }

            // ⭐ 즐겨찾기
            if (favorites.isNotEmpty()) {
                item { SectionHeader("⭐ 즐겨찾기", "${favorites.size}개") }
                items(favorites) { (name, a) ->
                    SavedRow(book = name, article = a, onClick = { onSavedArticle(name, a) })
                }
            }

            // 🕘 최근 본 조문
            if (recents.isNotEmpty()) {
                item { SectionHeader("🕘 최근 본 조문", null) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recents) { (name, a) ->
                            Surface(
                                shape = RoundedCornerShape(99.dp), color = AppColors.Surface,
                                shadowElevation = 1.dp,
                                modifier = Modifier.clickable { onSavedArticle(name, a) }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(a.num, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold,
                                        color = AppColors.Primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text(a.title, fontSize = 12.sp, color = AppColors.TextSecondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(today: TodayReg?, isWeekend: Boolean, onClick: (TodayReg) -> Unit) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp)
        .clip(RoundedCornerShape(22.dp))

    if (isWeekend || today == null) {
        Box(
            cardModifier.background(
                Brush.linearGradient(listOf(Color(0xFFFFF4E6), Color(0xFFFFEEF0)))
            ).padding(20.dp)
        ) {
            Column {
                Pill("🌙 주말", Color(0xFFD68910))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("😴", fontSize = 22.sp)
                    Spacer(Modifier.width(9.dp))
                    Text("오늘은 쉬는 날!", fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7A5A2E))
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    "오늘의 규정은 평일에만 찾아와요. 푹 쉬고 월요일에 만나요! 재충전하는 하루 되세요 🍀",
                    fontSize = 13.5.sp, color = Color(0xFF8A6D4A), lineHeight = 22.sp
                )
            }
        }
    } else {
        Box(
            cardModifier
                .background(Brush.linearGradient(listOf(Color(0xFFEEF3FF), Color(0xFFF3EEFF))))
                .clickable { onClick(today) }
                .padding(20.dp)
        ) {
            Column {
                Pill("✨ 오늘의 규정", Color(0xFF5B4BC4))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(today.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(9.dp))
                    Text("${today.num} ${today.title}", fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color(0xFF2A2456), lineHeight = 21.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(today.reg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8579C7), modifier = Modifier.padding(start = 31.dp, bottom = 11.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(today.fun_, fontSize = 13.5.sp, color = Color(0xFF4A4470), lineHeight = 23.sp)
                }
            }
        }
    }
}

/**
 * 오늘의 규정 ↔ 오늘의 비상조치 — 같은 자리에서 옆으로 넘긴다.
 *
 * 두 장의 높이가 다르다(② 가 길다). 페이저를 그냥 두면 넘길 때마다 아래 내용이 덜컥 뛴다.
 * 그래서 두 장을 **둘 다 그려 두고**(beyondViewportPageCount = 1) 제 높이를 잰 다음,
 * 넘기는 위치에 따라 두 높이 사이를 이어 준다 — 손가락을 따라 아래가 부드럽게 움직인다.
 * 위치는 layout 단계에서만 읽으므로 넘기는 동안 다시 그리기(recomposition)는 일어나지 않는다.
 */
@Composable
private fun TodayPager(
    today: TodayReg,
    card: TodayManualCard,
    onTodayClick: (TodayReg) -> Unit,
    onOpenSection: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val heights = remember { mutableStateListOf(0, 0) }   // 각 장의 제 높이(px)

    Column {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().layout { measurable, constraints ->
                val a = heights[0]
                val b = heights[1]
                val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, 1f)
                val fixed = if (a > 0 && b > 0) (a + (b - a) * pos).toInt() else -1
                val placeable = measurable.measure(
                    if (fixed > 0) constraints.copy(minHeight = fixed, maxHeight = fixed)
                    else constraints
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        ) { page ->
            // unbounded: 페이저 높이에 눌리지 않은 '제 높이'를 재야 한다
            Box(
                Modifier.fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { heights[page] = it.height }
            ) {
                if (page == 0) TodayCard(today, false, onTodayClick)
                else TodayManualCardView(card, onOpenSection)
            }
        }
        // ● ○
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(2) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == i) AppColors.TextSecondary
                            else AppColors.Divider
                        )
                )
            }
        }
    }
}

/**
 * ② 오늘의 비상조치. 해설(hook·easy)은 사람이 쓴 글이고, "원문 핵심 절차"는 매뉴얼을
 * 그대로 옮긴 인용이다 — 그래서 절차마다 기기의 원문과 대조한 결과를 배지로 단다.
 * today.json 과 manual.json 이 어긋나면 여기서 티가 난다.
 */
@Composable
private fun TodayManualCardView(card: TodayManualCard, onOpenSection: (String) -> Unit) {
    val item = card.item
    var expanded by remember(item.sectionId) { mutableStateOf(false) }
    var easyOverflow by remember(item.sectionId) { mutableStateOf(false) }
    val foldable = item.keySteps.size > 3 || easyOverflow || expanded

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(AppColors.TodayManualBgA, AppColors.TodayManualBgB)))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("🚨 오늘의 비상조치", AppColors.TodayManualPillFg, AppColors.TodayManualPillBg)
                if (item.role.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(ManualFormat.clean(item.role), fontSize = 10.5.sp,
                        color = AppColors.TextHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(ManualFormat.clean(item.title), fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary, lineHeight = 21.sp)
            if (item.hook.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(ManualFormat.clean(item.hook), fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary, lineHeight = 21.sp)
            }
            if (item.easy.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .background(AppColors.TodayManualInner, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        ManualFormat.clean(item.easy), fontSize = 13.sp,
                        color = AppColors.TextPrimary, lineHeight = 21.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) easyOverflow = it.hasVisualOverflow }
                    )
                }
            }

            if (item.keySteps.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("원문 핵심 절차", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.TodayManualPillFg)
                Spacer(Modifier.height(9.dp))
                val shown = if (expanded) item.keySteps else item.keySteps.take(3)
                shown.forEachIndexed { i, step ->
                    QuoteStep(
                        index = i + 1,
                        text = AnnotatedString(ManualFormat.clean(step)),
                        verified = card.verified.getOrElse(i) { false },
                        unverifiedLabel = "원문 대조 안 됨",
                        bottomGap = 10.dp
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (foldable) {
                    // 폴드 접힌 폭(약 280dp)에서는 이 줄에 204dp 밖에 없다 — 문구를 짧게 둔다
                    Text(
                        if (expanded) "접기 ▴" else "더 보기 ▾",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("원문 전체 보기 ›", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.TodayManualPillFg, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenSection(item.sectionId) }
                        .padding(horizontal = 4.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, bg: Color = Color.White) {
    Surface(color = bg, shape = RoundedCornerShape(99.dp),
        shadowElevation = 2.dp) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
private fun BookCard(book: RegBook, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(book.bgColor)),
                contentAlignment = Alignment.Center
            ) { Text(book.icon, fontSize = 17.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(book.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary,
                    lineHeight = 18.sp)
                Text("${book.articles.size}개 조문 · ${book.subtitle}", fontSize = 10.5.sp,
                    color = AppColors.TextSecondary, lineHeight = 14.sp)
            }
            Text("›", fontSize = 16.sp, color = AppColors.TextHint)
        }
    }
}

/** 비상대응 현장조치 매뉴얼 (규정집 9권과 나란히 서는 10번째 문서) */
@Composable
private fun ManualCard(ready: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SituationBg),
                contentAlignment = Alignment.Center
            ) { Text("🚨", fontSize = 17.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("비상대응 현장조치 매뉴얼", fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary, lineHeight = 18.sp)
                Text(
                    if (ready) "상황별 조치 · 기기에 저장됨"
                    else "직원 전용 · 눌러서 받기",
                    fontSize = 10.5.sp, color = AppColors.TextSecondary, lineHeight = 14.sp
                )
            }
            if (!ready) {
                Surface(color = AppColors.SituationBg, shape = RoundedCornerShape(6.dp)) {
                    Text("받기", color = AppColors.SituationNum, fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            Text("›", fontSize = 16.sp, color = AppColors.TextHint)
        }
    }
}

/**
 * 물어보기 3범위 진입. 갤럭시 폴드 접힌 폭(약 280dp)에서도 세 칸이 나란히 서야 해서
 * 한 칸에 글자를 두 줄로 끊어 넣는다(가로 여백 6dp, 글자 11.5sp → 한 칸 약 72dp).
 */
@Composable
private fun AskEntryRow(onAsk: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        AskEntryTile("🙋", "규정에", "물어보기", AppColors.AnsConclusionBg,
            AppColors.AnsConclusionLabel, Modifier.weight(1f)) { onAsk(AskMode.REG) }
        AskEntryTile("🚨", "비상조치", "물어보기", AppColors.SituationBg,
            AppColors.SituationNum, Modifier.weight(1f)) { onAsk(AskMode.MANUAL) }
        AskEntryTile("🔎", "전체", "물어보기", AppColors.VerifiedBg,
            AppColors.VerifiedFg, Modifier.weight(1f)) { onAsk(AskMode.ALL) }
    }
}

@Composable
private fun AskEntryTile(
    emoji: String, line1: String, line2: String,
    bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = bg,
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.height(5.dp))
            Text(line1, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold,
                color = AppColors.TextPrimary, maxLines = 1)
            Text(line2, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun BookArticleList(book: RegBook, onArticle: (RegArticle, String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(q) {
        if (q.isBlank()) book.articles
        else book.articles.filter { it.title.contains(q, true) || it.body.contains(q, true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)
                    .background(AppColors.Surface, RoundedCornerShape(15.dp))
                    .padding(horizontal = 15.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                TextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("이 규정집에서 검색", color = AppColors.TextHint, fontSize = 13.5.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (book.hasOriginalFile) {
            item {
                Surface(
                    color = Color(0xFFEFF3FB), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 8.dp)
                ) {
                    Text(
                        "📎 이 규정은 신호기·배선 그림이 포함되어 있습니다. 그림이 필요하면 원본 파일을 참고하세요.",
                        fontSize = 11.5.sp, color = AppColors.Primary, lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        items(filtered) { a ->
            ArticleRow(prefix = a.num, title = a.title, body = a.body, query = q, onClick = { onArticle(a, q) })
        }
        if (filtered.isEmpty()) {
            item { EmptyText("이 규정집에 해당 내용이 없어요") }
        }
    }
}

@Composable
private fun ArticleRow(prefix: String, title: String, body: String, query: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.5.dp).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Surface(color = Color(0xFFEEF2FB), shape = RoundedCornerShape(6.dp)) {
                Text(prefix, color = AppColors.Primary, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(highlight(title, query), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary, lineHeight = 19.sp)
            Spacer(Modifier.height(3.dp))
            Text(highlight(snippet(body, query), query), fontSize = 11.5.sp, color = AppColors.TextSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        }
    }
}

/** 검색어와 일치하는 부분을 노란 형광펜으로 강조 (대소문자 무시) */
private fun highlight(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        while (true) {
            val idx = text.indexOf(q, start, ignoreCase = true)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(
                SpanStyle(background = Color(0xFFFFE94D), color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold)
            ) {
                append(text.substring(idx, idx + q.length))
            }
            start = idx + q.length
        }
    }
}

/**
 * 검색어가 본문 뒤쪽에 있어도 미리보기에 보이도록,
 * 일치 지점 주변을 잘라낸다. 검색어가 없으면 앞부분을 그대로 사용.
 */
private fun snippet(body: String, query: String, len: Int = 90): String {
    val q = query.trim()
    if (q.isEmpty()) return body.take(len)
    val idx = body.indexOf(q, ignoreCase = true)
    if (idx < 0) return body.take(len)
    val start = (idx - 24).coerceAtLeast(0)
    val end = (start + len).coerceAtMost(body.length)
    return (if (start > 0) "…" else "") + body.substring(start, end)
}

@Composable
private fun ArticleDetail(
    regName: String,
    article: RegArticle,
    funText: String?,
    query: String = "",
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
            Column(Modifier.padding(16.dp)) {
                if (funText != null) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFFEEF3FF), Color(0xFFF3EEFF))),
                                RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Pill("✨ 쉽게 풀면", Color(0xFF5B4BC4))
                            Spacer(Modifier.height(8.dp))
                            Text(funText, fontSize = 13.sp, color = Color(0xFF4A4470), lineHeight = 20.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Surface(color = Color(0xFFEEF2FB), shape = RoundedCornerShape(7.dp)) {
                    Text(article.num, color = AppColors.Primary, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(9.dp))
                Text(highlight(article.title, query), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.TextPrimary, lineHeight = 25.sp)
                Spacer(Modifier.height(10.dp))
                SelectionContainer {
                    // 터널에서 급히 읽는 화면이라 본문은 14sp 밑으로 내리지 않는다.
                    // 줄간은 1.5배(21sp)로 유지해 밀도를 높이면서 가독성은 지킨다.
                    Text(highlight(article.body, query), fontSize = 14.sp,
                        color = AppColors.TextPrimary, lineHeight = 21.sp)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = AppColors.Divider)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(regName, fontSize = 12.sp, color = AppColors.TextHint,
                        modifier = Modifier.weight(1f))

                    // ⭐ 즐겨찾기
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
                            tint = if (isFavorite) Color(0xFFF5A623) else AppColors.TextHint
                        )
                    }

                    // 🔗 공유
                    IconButton(onClick = {
                        val text = "[$regName ${article.num}] ${article.title}\n\n${article.body}"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, "${article.num} ${article.title}")
                        }
                        context.startActivity(Intent.createChooser(send, "조문 공유"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "조문 공유",
                            tint = AppColors.TextSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary)
        if (trailing != null) {
            Spacer(Modifier.width(7.dp))
            Text(trailing, fontSize = 11.5.sp, color = AppColors.TextHint,
                modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}

/** 즐겨찾기 목록에 쓰이는 한 줄 (규정집 이름 + 조문) */
@Composable
private fun SavedRow(book: String, article: RegArticle, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$book ${article.num}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = AppColors.Primary)
                Spacer(Modifier.height(3.dp))
                Text(article.title, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", fontSize = 16.sp, color = AppColors.TextHint)
        }
    }
}

@Composable
private fun EmptyText(msg: String) {
    Box(Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = AppColors.TextHint, fontSize = 13.sp)
    }
}
