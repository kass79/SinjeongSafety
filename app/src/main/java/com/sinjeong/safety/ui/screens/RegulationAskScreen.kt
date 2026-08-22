package com.sinjeong.safety.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.R
import com.sinjeong.safety.data.RegBook
import com.sinjeong.safety.data.RegHit
import com.sinjeong.safety.data.RegulationRepository
import com.sinjeong.safety.data.RegulationSearch
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 규정 물어보기 — 챗봇형 검색 화면.
 * 인터넷 없이 기기 안에서만 계산한다(터널 대비).
 */

/** 대화 한 줄 */
private sealed class ChatItem {
    data class Me(val text: String) : ChatItem()
    data class Intro(val total: Int) : ChatItem()
    data class Empty(val query: String) : ChatItem()
    data class Result(
        val query: String,
        val totalHits: Int,
        val top: List<RegHit>,
        val weak: Boolean,
        val ms: Double,
        val corpus: Int
    ) : ChatItem()
}

/** 자주 묻는 질문 (시안과 동일) */
private val FAQ = listOf("무선 고장", "지연 시 조치", "휴가 규정", "출입문 안 열림")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegulationAskScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var books by remember { mutableStateOf<List<RegBook>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.clearAiAnswer()   // 지난번 답이 새 대화에 묻어 오지 않게
        books = RegulationRepository.loadBooks(context)
        total = books.sumOf { it.articles.size }
        items = listOf(ChatItem.Intro(total))
    }

    // 새 말풍선이 생기면 맨 아래로 내려준다
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    fun ask(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty() || books.isEmpty()) return
        input = ""
        keyboard?.hide()
        vm.clearAiAnswer()   // 검색어가 바뀌면 앞 질문의 AI 답은 더 이상 근거가 없다

        val t0 = System.nanoTime()
        val hits = RegulationSearch.search(books, q)
        val ms = (System.nanoTime() - t0) / 1_000_000.0

        val answer = if (hits.isEmpty()) ChatItem.Empty(q)
        else ChatItem.Result(
            query = q,
            totalHits = hits.size,
            // 화면에는 5개만 보여 주고, AI에는 8개까지 넘긴다 (근거가 많을수록 답이 좋다)
            top = hits.take(8),
            weak = hits[0].score < RegulationSearch.WEAK_SCORE,
            ms = ms,
            corpus = total
        )
        items = items + ChatItem.Me(q) + answer
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { AskTopBar(total = total, onBack = onBack) },
        bottomBar = {
            AskInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = { ask(input) },
                enabled = books.isNotEmpty()
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is ChatItem.Me -> MeBubble(item.text)
                    is ChatItem.Intro -> {
                        IntroBubble(item.total)
                        FaqChips(onPick = { ask(it) })
                    }
                    is ChatItem.Empty -> EmptyBubble()
                    is ChatItem.Result -> ResultBubble(item)
                }
            }
            // AI 정리 — 마지막 답이 '결과 있음'일 때만. 대화 화면이라 결과 아래(=최신 자리)에 붙인다.
            item {
                val last = items.lastOrNull()
                if (last is ChatItem.Result && last.top.isNotEmpty()) AiSection(vm, last)
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
    }
}

// ── AI 정리 (서버 Cloud Functions) ──────────────────────────────
/**
 * 답이 오면 버튼 자리에 카드가 대신 선다(✕ 로 닫으면 다시 버튼).
 * 같은 자리에 하나만 두어야 화면이 길어지지 않는다.
 */
@Composable
private fun AiSection(vm: MainViewModel, result: ChatItem.Result) {
    val answer by vm.aiAnswer.collectAsState()
    val loading by vm.aiLoading.collectAsState()
    val text = answer

    if (text != null) {
        Surface(
            color = AppColors.TagOpsBg,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.TagOpsFg.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 AI 답변", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                        color = AppColors.TagOpsFg, modifier = Modifier.weight(1f))
                    Text("✕", fontSize = 14.sp, color = AppColors.TagOpsFg,
                        modifier = Modifier
                            .clickable { vm.clearAiAnswer() }
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.height(7.dp))
                // 마크다운은 그리지 않는다 — 굵게용 ** 만 걷어내고 그대로 읽힌다
                Text(text.replace("**", ""), fontSize = 13.5.sp,
                    color = AppColors.TextPrimary, lineHeight = 22.sp)
                Spacer(Modifier.height(9.dp))
                Text("AI가 만든 초안입니다. 반드시 원문 조문으로 확인하세요.",
                    fontSize = 10.5.sp, color = AppColors.TextHint, lineHeight = 16.sp)
            }
        }
    } else {
        Button(
            onClick = {
                vm.askRegulationAi(
                    result.query,
                    result.top.map {
                        mapOf("n" to it.article.num, "t" to it.article.title, "b" to it.article.body)
                    }
                )
            },
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.TagOpsBg, contentColor = AppColors.TagOpsFg,
                // 로딩 중에도 초록을 유지 (기본 회색이면 스피너가 묻힌다)
                disabledContainerColor = AppColors.TagOpsBg.copy(alpha = 0.6f),
                disabledContentColor = AppColors.TagOpsFg
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(color = AppColors.TagOpsFg, strokeWidth = 2.dp,
                    modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI가 조문을 읽는 중...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("AI로 정리해서 보기", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── 상단바 ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskTopBar(total: Int, onBack: () -> Unit) {
    Surface(color = AppColors.Primary) {
        Row(
            // 상태표시줄 여백은 MainActivity의 Scaffold가 이미 넣어 준다 (여기서 또 넣으면 두 배)
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Image(
                painter = painterResource(R.drawable.mascot_hello),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(Color(0xFFE8F0FC))
                    .border(2.dp, Color.White, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("규정에 물어보기", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(
                    if (total > 0) "규정 4권 · 조문 ${total}개에서 찾아드려요" else "규정을 불러오는 중…",
                    fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

// ── 하단 입력창 ─────────────────────────────────────────────────
@Composable
private fun AskInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                placeholder = {
                    Text("궁금한 걸 물어보세요 (예: 무전기 고장)",
                        color = AppColors.TextHint, fontSize = 13.5.sp)
                },
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFF7F9FD),
                    focusedBorderColor = AppColors.PrimaryLight,
                    unfocusedBorderColor = AppColors.Divider
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = if (value.isBlank() || !enabled) Color(0xFFC3CBDF) else AppColors.Primary,
                modifier = Modifier.size(46.dp).clickable(enabled = enabled && value.isNotBlank()) { onSend() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "검색", tint = Color.White,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── 말풍선 ──────────────────────────────────────────────────────
@Composable
private fun MeBubble(text: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            color = AppColors.Primary,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            modifier = Modifier.fillMaxWidth(0.86f).wrapContentWidth(Alignment.End)
        ) {
            Text(text, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun BotBubble(content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), content = content)
        }
    }
}

@Composable
private fun IntroBubble(total: Int) {
    BotBubble {
        Text("안녕하세요, 또타예요! 🚇", fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary, lineHeight = 21.sp)
        Spacer(Modifier.height(4.dp))
        Text("궁금한 걸 낱말이나 짧은 문장으로 물어보세요.\n규정집 4권" +
            (if (total > 0) " · 조문 ${total}개" else "") + "에서 관련 조문을 찾아 드릴게요.",
            fontSize = 13.5.sp, color = AppColors.TextPrimary, lineHeight = 21.sp)
        Spacer(Modifier.height(6.dp))
        Text("'무전기'라고 쳐도 규정에 적힌 '무선전화기' 조문을 찾아냅니다.",
            fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 18.sp)
    }
}

@Composable
private fun EmptyBubble() {
    BotBubble {
        Text("관련 조문을 못 찾았어요. 😥", fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary)
        Spacer(Modifier.height(5.dp))
        Text("낱말로 다시 물어보시면 잘 찾습니다.\n예) \"출입문 안 닫힘\", \"비상제동\", \"연차휴가\", \"제38조\"",
            fontSize = 12.5.sp, color = AppColors.TextSecondary, lineHeight = 20.sp)
    }
}

@Composable
private fun ResultBubble(item: ChatItem.Result) {
    BotBubble {
        if (item.weak) {
            // 안전앱이라 자신 없을 땐 자신 없다고 먼저 말한다
            Text("딱 맞는 조문을 못 찾았어요. 😐 비슷해 보이는 것만 보여드릴게요.",
                fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary, lineHeight = 21.sp)
            Spacer(Modifier.height(5.dp))
            Text("규정에 쓰인 말로 바꿔 물어보면 잘 찾습니다.\n예) '뒤에서 밀어도 되나' → '구원', '스크린도어' → '승강장안전문'",
                fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 18.sp)
        } else {
            Text(
                buildAnnotatedString {
                    append("관련 조문 ")
                    withStyle(SpanStyle(color = AppColors.Primary, fontWeight = FontWeight.Bold)) {
                        append("${item.totalHits}건")
                    }
                    append(" 중 가까운 ")
                    withStyle(SpanStyle(color = AppColors.Primary, fontWeight = FontWeight.Bold)) {
                        append("${minOf(item.top.size, 5)}건")
                    }
                    append("이에요.")
                },
                fontSize = 13.5.sp, color = AppColors.TextPrimary, lineHeight = 21.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("규정 ${item.corpus}개 조문 훑는 데 ${"%.1f".format(item.ms)}ms · 인터넷 없이 기기 안에서 계산",
            fontSize = 10.5.sp, color = AppColors.TextHint)

        Spacer(Modifier.height(8.dp))
        item.top.take(5).forEach { hit -> HitCard(hit) }

        Surface(
            color = Color(0xFFFFFBE8),
            shape = RoundedCornerShape(9.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2E5AE)),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text("⚠️ 답이 아니라 규정 원문입니다. 실제 조치는 조문 전문과 관제 지시를 따르세요.",
                fontSize = 10.5.sp, color = Color(0xFF8A6D00), lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
        }
    }
}

// ── 결과 카드 (탭하면 조문 전문) ────────────────────────────────
@Composable
private fun HitCard(hit: RegHit) {
    var open by remember(hit.article.num, hit.book) { mutableStateOf(false) }
    val (bg, fg) = bookBadgeColors(hit.book)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (open) AppColors.PrimaryLight else AppColors.Divider
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
            .clickable { open = !open }
            .animateContentSize()
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = bg, shape = RoundedCornerShape(99.dp)) {
                    Text(RegulationSearch.shortBookName(hit.book), color = fg,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(hit.article.num, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.Primary)
                Spacer(Modifier.weight(1f))
                Text("관련도 ${Math.round(hit.score)}", fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, color = AppColors.TextHint)
            }
            Spacer(Modifier.height(6.dp))
            Text(markUp(hit.article.title, hit.marks), fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, lineHeight = 19.sp)
            Spacer(Modifier.height(5.dp))
            if (open) {
                HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 4.dp))
                Text(markUp(hit.article.body, hit.marks), fontSize = 12.5.sp,
                    color = Color(0xFF333A4C), lineHeight = 22.sp)
            } else {
                Text(markUp(RegulationSearch.excerpt(hit.article.body, hit.marks), hit.marks),
                    fontSize = 12.5.sp, color = Color(0xFF4A5163), lineHeight = 20.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (open) "접기 ▴" else "탭하면 조문 전문이 펼쳐집니다 ▾",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.PrimaryLight)
        }
    }
}

/** 규정집 배지 색 (RegulationRepository.bookMeta 와 같은 계열) */
private fun bookBadgeColors(book: String): Pair<Color, Color> = when (book) {
    "전동차승무원업무예규" -> Color(0xFFE9F1FD) to Color(0xFF1E3A8A)
    "운전취급규정" -> Color(0xFFE9F6ED) to Color(0xFF2E7D32)
    "인사규정" -> Color(0xFFFDF0E3) to Color(0xFFC2660A)
    "취업규칙" -> Color(0xFFFBF4DC) to Color(0xFF8A6D00)
    else -> Color(0xFFEEEEEE) to Color(0xFF555555)
}

/** 걸린 낱말을 노란 형광펜으로. 긴 낱말부터 칠해야 서로 겹치지 않는다. */
private fun markUp(text: String, marks: List<String>): AnnotatedString {
    val terms = marks.filter { it.isNotBlank() }.sortedByDescending { it.length }
    if (terms.isEmpty()) return AnnotatedString(text)
    val low = text.lowercase()
    val painted = BooleanArray(text.length)

    // 칠할 구간부터 모아 두고 한 번에 그린다 (겹치면 먼저 칠한 쪽이 이긴다)
    for (t in terms) {
        var i = low.indexOf(t)
        while (i >= 0) {
            var free = true
            for (k in i until i + t.length) if (painted[k]) { free = false; break }
            if (free) for (k in i until i + t.length) painted[k] = true
            i = low.indexOf(t, i + t.length)
        }
    }

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val on = painted[i]
            var j = i
            while (j < text.length && painted[j] == on) j++
            if (on) {
                withStyle(SpanStyle(background = Color(0xFFFFF0A8), fontWeight = FontWeight.Bold)) {
                    append(text.substring(i, j))
                }
            } else {
                append(text.substring(i, j))
            }
            i = j
        }
    }
}

/** 자주 묻는 질문 칩 — FlowRow(실험 API) 대신 Row 두 줄로 (빌드 안정) */
@Composable
private fun FaqChips(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 14.dp)) {
        FAQ.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pair.forEach { q ->
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DCEC)),
                        modifier = Modifier.clickable { onPick(q) }
                    ) {
                        Text(q, color = AppColors.Primary, fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}

/** 규정 뷰어·홈에서 쓰는 진입 배너 */
@Composable
fun AskEntryBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFEEF3FF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DEF6)),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Text("🙋", fontSize = 19.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("규정에 물어보기", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.Primary)
                Spacer(Modifier.height(2.dp))
                Text("\"출입문 안 열림\"처럼 물어보면 관련 조문을 찾아드려요",
                    fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 16.sp)
            }
            Text("›", fontSize = 17.sp, color = AppColors.Primary)
        }
    }
}
