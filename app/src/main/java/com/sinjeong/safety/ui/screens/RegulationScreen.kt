package com.sinjeong.safety.ui.screens

import android.content.Intent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.data.RegArticle
import com.sinjeong.safety.data.RegBook
import com.sinjeong.safety.data.RegulationRepository
import com.sinjeong.safety.data.TodayReg
import com.sinjeong.safety.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegulationScreen(onBack: () -> Unit, onAsk: () -> Unit = {}) {
    val context = LocalContext.current
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
        else -> "운전규정"
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
                    onAsk = onAsk,
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
    onAsk: () -> Unit,
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
            TodayCard(today, isWeekend, onTodayClick)
        }
        // 규정에 물어보기 (오늘의 규정 바로 아래 / 조문 검색창 바로 위)
        // 오른쪽 "AI로 정리"는 같은 화면으로 가는 두 번째 문 — AI 버튼이 그 안에 있다는 안내
        item {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AskEntryBanner(onClick = onAsk, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppColors.TagOpsBg,
                    modifier = Modifier.fillMaxHeight().clickable(onClick = onAsk)
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = AppColors.TagOpsFg,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("AI로 정리", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold,
                            color = AppColors.TagOpsFg)
                    }
                }
            }
        }
        // 검색
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                    .background(Color.White, RoundedCornerShape(15.dp))
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
                                shape = RoundedCornerShape(99.dp), color = Color.White,
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

@Composable
private fun Pill(text: String, color: Color) {
    Surface(color = Color.White, shape = RoundedCornerShape(99.dp),
        shadowElevation = 2.dp) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
private fun BookCard(book: RegBook, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = Color.White,
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
                    .background(Color.White, RoundedCornerShape(15.dp))
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
        shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp,
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
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
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
        shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp,
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
