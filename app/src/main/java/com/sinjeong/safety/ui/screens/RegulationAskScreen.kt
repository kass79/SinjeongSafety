package com.sinjeong.safety.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.GuideAnswer
import com.sinjeong.safety.data.GuideSource
import com.sinjeong.safety.data.ManualDoc
import com.sinjeong.safety.data.ManualFormat
import com.sinjeong.safety.data.ManualRepository
import com.sinjeong.safety.data.RegBook
import com.sinjeong.safety.data.RegHit
import com.sinjeong.safety.data.RegulationRepository
import com.sinjeong.safety.data.RegulationSearch
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 물어보기 — 규정 / 비상조치 / 전체 세 범위.
 *
 * 검색은 예전 그대로 기기 안에서만 돈다(터널 대비). 그 위에 서버 AI(askGuide)가
 * "결론 + 원문 인용 단계"를 얹는다. **통신이 끊겨도 검색 결과 카드와 상황 바로가기는
 * 그대로 살아 있어야 한다** — AI 는 덤이지 본체가 아니다.
 */

/** 물어볼 범위. 서버 askGuide 의 mode 값과 같은 글자를 쓴다. */
object AskMode {
    const val REG = "reg"
    const val MANUAL = "manual"
    const val ALL = "all"
    val ALL_MODES = listOf(REG, MANUAL, ALL)

    fun label(mode: String): String = when (mode) {
        MANUAL -> "비상조치"
        ALL -> "전체"
        else -> "규정"
    }

    fun title(mode: String): String = when (mode) {
        MANUAL -> "비상조치 물어보기"
        ALL -> "전체 물어보기"
        else -> "규정에 물어보기"
    }
}

// AI에 넘기는 근거 길이 — 서버와 같은 기준이라야 인용문 대조가 맞는다
private const val CUT_REG = 2000
private const val CUT_MANUAL = 6000

/** 대화 한 줄 */
private sealed class ChatItem {
    data class Me(val text: String) : ChatItem()
    data class Intro(val mode: String, val total: Int) : ChatItem()
    data class Empty(val query: String) : ChatItem()
    data class Result(
        val query: String,
        val mode: String,
        val totalHits: Int,
        val top: List<RegHit>,
        val weak: Boolean,
        val ms: Double,
        val corpus: Int
    ) : ChatItem()
}

/** 자주 묻는 질문 (범위마다 다르게) */
private fun faqFor(mode: String): List<String> = when (mode) {
    AskMode.MANUAL -> listOf("터널 화재", "탈선 초동조치", "전차선 단전", "승객 임의하차")
    AskMode.ALL -> listOf("출입문 안 열림", "터널 화재", "지연 시 조치", "비상제동")
    else -> listOf("무선 고장", "지연 시 조치", "휴가 규정", "출입문 안 열림")
}

@Composable
fun RegulationAskScreen(
    vm: MainViewModel,
    initialMode: String,
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val manualReady by vm.manualReady.collectAsState()

    var mode by remember { mutableStateOf(initialMode) }
    var books by remember { mutableStateOf<List<RegBook>>(emptyList()) }
    var manualDoc by remember { mutableStateOf<ManualDoc?>(null) }
    var input by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.clearAiAnswer()   // 지난번 답이 새 대화에 묻어 오지 않게
        books = RegulationRepository.loadBooks(context)
    }
    LaunchedEffect(manualReady) {
        manualDoc = if (manualReady) ManualRepository.load(context) else null
    }

    // 매뉴얼을 아직 못 받았으면 매뉴얼·전체를 골라도 규정 범위로만 돈다
    val manualBook = remember(manualDoc) { manualDoc?.let { ManualRepository.asBook(it) } }
    val manualUsable = manualBook != null
    val effectiveMode = if (manualUsable) mode else AskMode.REG

    // 범위별 책 목록은 인스턴스를 고정해 둔다 — RegulationSearch 가 목록 동일성으로
    // 색인 재사용 여부를 판단하므로, 매번 새 리스트를 만들면 헛일을 한다.
    val allBooks = remember(books, manualBook) { books + listOfNotNull(manualBook) }
    val manualOnly = remember(manualBook) { listOfNotNull(manualBook) }
    val scopedBooks = when (effectiveMode) {
        AskMode.MANUAL -> manualOnly
        AskMode.ALL -> allBooks
        else -> books
    }
    val corpus = scopedBooks.sumOf { it.articles.size }

    // AI 에 넘기면 안 되는 섹션(실명·연락처). 뷰어와 검색에는 그대로 나온다.
    val blockedSids = remember(manualDoc) {
        // 섹션 하나씩의 판정(isSensitive)이 아니라 문서 단위 판정을 쓴다 —
        // 연락망 장에서 갈라져 나온 소절은 제 내용에 이름·번호가 없어도 부모와 같은 취급이다.
        manualDoc?.let { ManualFormat.sensitiveIds(it) } ?: emptySet()
    }

    LaunchedEffect(effectiveMode, corpus) {
        if (corpus > 0) {
            items = listOf(ChatItem.Intro(effectiveMode, corpus))
            vm.clearAiAnswer()
        }
    }

    // 새 말풍선이 생기면 맨 아래로 내려준다
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    fun ask(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty() || scopedBooks.isEmpty()) return
        input = ""
        keyboard?.hide()
        vm.clearAiAnswer()   // 검색어가 바뀌면 앞 질문의 AI 답은 더 이상 근거가 없다

        val t0 = System.nanoTime()
        val hits = RegulationSearch.search(scopedBooks, q)
        val ms = (System.nanoTime() - t0) / 1_000_000.0

        val answer = if (hits.isEmpty()) ChatItem.Empty(q)
        else ChatItem.Result(
            query = q, mode = effectiveMode, totalHits = hits.size,
            top = hits.take(8),
            weak = hits[0].score < RegulationSearch.WEAK_SCORE,
            ms = ms, corpus = corpus
        )
        items = items + ChatItem.Me(q) + answer
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            Column {
                AskTopBar(mode = effectiveMode, corpus = corpus, onBack = onBack)
                ModeChips(selected = mode, onSelect = { mode = it })
            }
        },
        bottomBar = {
            AskInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = { ask(input) },
                enabled = scopedBooks.isNotEmpty(),
                mode = effectiveMode
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
        ) {
            // 범위를 골랐는데 매뉴얼이 없으면 왜 규정만 도는지 먼저 알려 준다
            if (mode != AskMode.REG && !manualUsable) {
                item { ManualMissingBubble() }
            }
            items(items) { item ->
                when (item) {
                    is ChatItem.Me -> MeBubble(item.text)
                    is ChatItem.Intro -> {
                        IntroBubble(item.mode, item.total)
                        FaqChips(faqFor(item.mode), onPick = { ask(it) })
                    }
                    is ChatItem.Empty -> EmptyBubble()
                    is ChatItem.Result -> {
                        // AI 정리는 가장 최근 결과 '위'에 — 버튼을 먼저 보고 그 아래로 원문을 읽는다.
                        // 같은 LazyColumn item 안에 두어야 아래 자동 스크롤(마지막 item)이 버튼 위를 잡는다.
                        if (item === items.lastOrNull() && item.top.isNotEmpty()) {
                            AiSection(
                                vm = vm, result = item, manualDoc = manualDoc,
                                blockedSids = blockedSids, onOpenSection = onOpenSection
                            )
                        }
                        ResultBubble(item)
                    }
                }
            }
            // 질문 전 비상조치 화면: 상황 바로가기 (AI 없이 원문으로 바로 간다)
            if (effectiveMode == AskMode.MANUAL && items.size <= 1 && manualDoc != null) {
                item { SituationGrid(manualDoc!!, onOpenSection) }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
    }
}

// ── 범위 칩 ─────────────────────────────────────────────────────
@Composable
private fun ModeChips(selected: String, onSelect: (String) -> Unit) {
    Surface(color = AppColors.Surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AskMode.ALL_MODES.forEach { m ->
                val on = m == selected
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (on) AppColors.Primary else AppColors.Background,
                    modifier = Modifier.weight(1f).clickable { onSelect(m) }
                ) {
                    Text(
                        AskMode.label(m),
                        color = if (on) Color.White else AppColors.TextSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                    )
                }
            }
        }
    }
}

// ── AI 정리 ─────────────────────────────────────────────────────
@Composable
private fun AiSection(
    vm: MainViewModel,
    result: ChatItem.Result,
    manualDoc: ManualDoc?,
    blockedSids: Set<String>,
    onOpenSection: (String) -> Unit
) {
    val answer by vm.guide.collectAsState()
    val sent by vm.guideSources.collectAsState()
    val loading by vm.aiLoading.collectAsState()

    if (answer != null) {
        GuideAnswerCard(
            answer = answer!!, sources = sent, mode = result.mode,
            manualDoc = manualDoc, onOpenSection = onOpenSection,
            onClose = { vm.clearAiAnswer() }
        )
        return
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Button(
            onClick = {
                val sources = buildSources(result.mode, result.top, blockedSids, manualDoc)
                if (sources.isEmpty()) return@Button
                vm.askGuide(result.query, result.mode, sources)
            },
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.AnsConclusionBg,
                contentColor = AppColors.AnsConclusionLabel,
                // 로딩 중에도 같은 색을 유지 (기본 회색이면 스피너가 묻힌다)
                disabledContainerColor = AppColors.AnsConclusionBg,
                disabledContentColor = AppColors.AnsConclusionLabel
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(color = AppColors.AnsConclusionLabel, strokeWidth = 2.dp,
                    modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI가 원문을 읽는 중...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("AI로 정리해서 보기", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 한 번 보냈는데 답이 없다 = 실패. AI 영역만 알리고 아래 원문 카드는 그대로 둔다
        if (!loading && sent.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("AI 답변을 받지 못했습니다. 아래 원문은 그대로 보실 수 있습니다.",
                fontSize = 11.sp, color = AppColors.TextHint, lineHeight = 16.sp)
        }
    }
}

/**
 * AI 에게 넘길 근거 고르기.
 *  reg  → 규정 8건 / manual → 매뉴얼 4건 / all → 매뉴얼 3건 + 규정 5건
 * 본문은 서버와 **같은 기준**으로 자른다(reg 2000 / manual 6000자).
 * 자르는 길이가 다르면 서버가 검증한 인용문을 앱이 "원문 아님"으로 표시하게 된다.
 */
private fun buildSources(
    mode: String,
    hits: List<RegHit>,
    blockedSids: Set<String>,
    manualDoc: ManualDoc?
): List<GuideSource> {
    val isManual = { h: RegHit -> h.book == ManualRepository.BOOK_NAME }
    // 매뉴얼은 검색용 평문(h.article.body)이 아니라 **AI 용 본문**을 보낸다 — 흐름도 글을 표식으로
    // 감싼 것만 다르다(ManualFormat.aiBody). 답을 받은 뒤의 인용문 대조도 "보낸 본문" 기준이라
    // 여기서 만든 글을 그대로 들고 있으면 서버와 앱이 저절로 같은 글을 본다.
    val manualBody = { h: RegHit ->
        manualDoc?.sections?.firstOrNull { it.id == h.article.sid }?.let { ManualFormat.aiBody(it) }
            ?: h.article.body
    }
    // 실명·연락처가 든 섹션은 AI 에 보내지 않는다(개인정보처리방침 약속).
    // 보낼 글이 없는 섹션(그림뿐인 섹션)도 뺀다 — 빈 근거는 자리만 차지한다.
    val manualHits = hits.filter {
        isManual(it) && it.article.sid !in blockedSids && manualBody(it).isNotBlank()
    }
    val regHits = hits.filterNot(isManual)

    val picked = when (mode) {
        AskMode.MANUAL -> manualHits.take(4)
        AskMode.ALL -> manualHits.take(3) + regHits.take(5)
        else -> regHits.take(8)
    }
    val seen = HashSet<String>()
    return picked.mapNotNull { h ->
        val manual = isManual(h)
        val id = if (manual) h.article.sid else "${h.book}#${h.article.num}"
        if (id.isBlank() || !seen.add(id)) return@mapNotNull null   // id 는 요청 안에서 유일해야 한다
        GuideSource(
            id = id,
            kind = if (manual) "manual" else "reg",
            label = if (manual) ManualFormat.clean(h.article.title)
            else "${RegulationSearch.shortBookName(h.book)} ${h.article.num}",
            title = ManualFormat.clean(h.article.title),
            body = ManualFormat.cutBody(
                if (manual) manualBody(h) else h.article.body,
                if (manual) CUT_MANUAL else CUT_REG
            )
        )
    }
}

// ── 답변 카드 ───────────────────────────────────────────────────
@Composable
private fun GuideAnswerCard(
    answer: GuideAnswer,
    sources: List<GuideSource>,
    mode: String,
    manualDoc: ManualDoc?,
    onOpenSection: (String) -> Unit,
    onClose: () -> Unit
) {
    val byId = remember(sources) { sources.associateBy { it.id } }
    val hasHighlight = answer.steps.any { it.highlights.isNotEmpty() }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 2.dp)) {
            Text("🤖 AI 답변", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("✕", fontSize = 14.sp, color = AppColors.TextHint,
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 6.dp, vertical = 2.dp))
        }

        if (!answer.found) {
            PastelCard(AppColors.DraftBg) {
                Text("제공된 자료에서 찾지 못했습니다", fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text("아래 원문 카드를 직접 확인해 주세요.", fontSize = 12.sp,
                    color = AppColors.TextSecondary, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(10.dp))
            AiFootnote(mode)
            return@Column
        }

        // ① 결론
        if (answer.conclusion.isNotBlank()) {
            PastelCard(AppColors.AnsConclusionBg) {
                Text("결론", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.AnsConclusionLabel)
                Spacer(Modifier.height(7.dp))
                Text(answer.conclusion, fontSize = 14.sp, color = AppColors.TextPrimary,
                    lineHeight = 23.sp)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ② 근거 · 원문
        if (answer.steps.isNotEmpty()) {
            Text("근거 · 원문", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = AppColors.TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            answer.steps.forEachIndexed { i, step ->
                val src = byId[step.sourceId]
                StepRow(index = i + 1, step = step, source = src)
            }

            // ③ 그림이 있는 매뉴얼 섹션이면 썸네일 줄
            val manualIds = answer.steps.mapNotNull { s ->
                byId[s.sourceId]?.takeIf { it.kind == "manual" }?.id
            }.distinct()
            manualIds.forEach { sid ->
                val section = manualDoc?.sections?.firstOrNull { it.id == sid }
                if (section != null && section.imageFiles.isNotEmpty()) {
                    SectionThumbnails(section.id, section.imageFiles, onOpenSection)
                }
            }

            // ④ 색 범례
            if (hasHighlight) {
                Spacer(Modifier.height(2.dp))
                ColorLegend()
            }
            Spacer(Modifier.height(10.dp))
        }

        // ⑤ 실무 유의사항
        if (answer.cautions.isNotEmpty()) {
            PastelCard(AppColors.AnsCautionBg) {
                Text("실무 유의사항", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.AnsCautionLabel)
                Spacer(Modifier.height(7.dp))
                answer.cautions.forEach { c ->
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text("· ", fontSize = 13.sp, color = AppColors.TextPrimary)
                        Text(c, fontSize = 13.sp, color = AppColors.TextPrimary, lineHeight = 21.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        AiFootnote(mode)
    }
}

/** 파스텔 카드 한 장 — 테두리 없이 배경만, 안쪽 여백은 넉넉히 */
@Composable
private fun PastelCard(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), content = content)
    }
}

@Composable
private fun StepRow(index: Int, step: com.sinjeong.safety.data.GuideStep, source: GuideSource?) {
    // 서버가 "원문 그대로"라고 해도 앱이 한 번 더 대조한다. 둘 다 맞을 때만 배지를 단다.
    // (6000자 본문을 정규화하는 일이라 다시 그릴 때마다 하지 않게 기억해 둔다)
    val verified = remember(step, source) {
        step.verified && source != null && ManualFormat.quoteMatches(step.quote, source.body)
    }

    QuoteStep(
        index = index,
        text = highlightQuote(step.quote, step.highlights),
        verified = verified,
        unverifiedLabel = "AI 정리",
        sourceLabel = source?.label
    )
}

/**
 * 번호 원 + 인용문 + "✓ 원문 일치" 배지 한 줄.
 * AI 답변의 단계와 "오늘의 비상조치"의 원문 핵심 절차가 같이 쓴다 — 둘 다
 * "이 문장이 원문 그대로인가"를 같은 모양으로 보여 줘야 한다.
 * 일치하지 않으면 [unverifiedLabel] 을 회색으로 단다(숨기면 어긋난 게 티가 안 난다).
 */
@Composable
internal fun QuoteStep(
    index: Int,
    text: AnnotatedString,
    verified: Boolean,
    unverifiedLabel: String,
    sourceLabel: String? = null,
    textColor: Color = AppColors.TextPrimary,
    bottomGap: androidx.compose.ui.unit.Dp = 14.dp
) {
    Row(Modifier.fillMaxWidth().padding(bottom = bottomGap)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(AppColors.AnsStepNumBg),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold,
                color = AppColors.AnsStepNumFg)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 13.5.sp, color = textColor, lineHeight = 23.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (verified) AppColors.VerifiedBg else AppColors.DraftBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (verified) "✓ 원문 일치" else unverifiedLabel,
                        color = if (verified) AppColors.VerifiedFg else AppColors.DraftFg,
                        fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (sourceLabel != null) {
                    Spacer(Modifier.width(7.dp))
                    Text(sourceLabel, fontSize = 10.5.sp, color = AppColors.TextHint,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 인용문 안 형광. 못 찾은 형광은 조용히 버린다(엉뚱한 자리를 칠하지 않는다). */
@Composable
private fun highlightQuote(
    quote: String,
    highlights: List<com.sinjeong.safety.data.GuideHighlight>
): AnnotatedString {
    val spans = ManualFormat.highlightSpans(quote, highlights.map { it.text to it.kind })
    // 형광 자리는 **원문 글자 기준**으로 잡고, 보여 줄 때만 조각마다 전용문자(PUA)를 바꾼다.
    // 통째로 먼저 바꾸면 글자 수가 달라져(U+F03DA 는 두 칸) 형광 자리가 밀린다.
    if (spans.isEmpty()) return AnnotatedString(ManualFormat.clean(quote))
    return buildAnnotatedString {
        var at = 0
        for (s in spans) {
            if (s.start > at) append(ManualFormat.clean(quote.substring(at, s.start)))
            withStyle(SpanStyle(background = highlightColor(s.kind))) {
                append(ManualFormat.clean(quote.substring(s.start, s.end)))
            }
            at = s.end
        }
        if (at < quote.length) append(ManualFormat.clean(quote.substring(at)))
    }
}

private fun highlightColor(kind: String): Color = when (kind) {
    "contact" -> AppColors.HlContactBg
    "caution" -> AppColors.HlCautionBg
    else -> AppColors.HlActionBg
}

@Composable
private fun ColorLegend() {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp, start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LegendDot(AppColors.HlActionBg, "핵심 행동")
        LegendDot(AppColors.HlContactBg, "보고·연락")
        LegendDot(AppColors.HlCautionBg, "금지·주의")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = AppColors.TextHint)
    }
}

/** 매뉴얼 섹션 그림 — 탭하면 그 섹션 원문(WebView)으로 간다 */
@Composable
private fun SectionThumbnails(sectionId: String, files: List<String>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        files.take(4).forEach { f ->
            AsyncImage(
                model = ManualRepository.imageFile(context, f),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Divider)
                    .clickable { onOpen(sectionId) }
            )
        }
        if (files.size > 4) {
            Text("그림 ${files.size - 4}개 더", fontSize = 10.sp, color = AppColors.TextHint,
                modifier = Modifier.clickable { onOpen(sectionId) })
        }
    }
}

/** 범위별 고정 안내문 (AI 영역 맨 아래) */
@Composable
private fun AiFootnote(mode: String) {
    Text(
        if (mode == AskMode.REG)
            "AI 답변은 참고용입니다. 정확한 내용은 조문 원문을 확인하세요."
        else
            "AI 답변은 참고용입니다. 실제 조치는 매뉴얼 원문과 관제 지시가 우선입니다.",
        fontSize = 10.5.sp, color = AppColors.TextHint, lineHeight = 16.sp,
        modifier = Modifier.padding(start = 2.dp)
    )
}

// ── 상황 바로가기 (질문 전, 오프라인에서도 동작) ────────────────
@Composable
private fun SituationGrid(doc: ManualDoc, onOpen: (String) -> Unit) {
    // 배열 순서 그대로(= 문서 순서). id 로 정렬하지 않는다 — v2 에서 id 순서는 문서 순서가 아니다.
    val list = remember(doc) { doc.visibleSections.filter { ManualFormat.isSituation(it) } }
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("상황 바로가기", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = AppColors.TextPrimary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        // 폴드 접힘 폭에서도 두 칸이 서게 Row 두 칸씩 (FlowRow 는 실험 API라 쓰지 않는다)
        list.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                pair.forEach { s ->
                    SituationCard(
                        tag = ManualFormat.situationTag(s),
                        title = ManualFormat.clean(s.title),
                        modifier = Modifier.weight(1f)
                    ) { onOpen(s.id) }
                }
                // 홀수면 마지막 줄 오른쪽을 비워 둔다 (카드가 폭 전체로 늘어나지 않게)
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text("통신이 안 되는 곳에서도 상황 바로가기와 원문·그림은 열립니다.",
            fontSize = 10.5.sp, color = AppColors.TextHint, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp, start = 2.dp))
    }
}

@Composable
private fun SituationCard(tag: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = AppColors.SituationBg,
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            if (tag.isNotEmpty()) {
                Text(tag, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.SituationNum)
                Spacer(Modifier.height(3.dp))
            }
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary, lineHeight = 16.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── 상단바 ──────────────────────────────────────────────────────
@Composable
private fun AskTopBar(mode: String, corpus: Int, onBack: () -> Unit) {
    Surface(color = AppColors.Primary) {
        Row(
            // 상태표시줄 여백은 MainActivity의 Scaffold가 이미 넣어 준다 (여기서 또 넣으면 두 배)
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(AskMode.title(mode), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White)
                Text(
                    if (corpus > 0) "${corpus}개 항목에서 찾아드려요" else "자료를 불러오는 중…",
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
    enabled: Boolean,
    mode: String
) {
    Surface(color = AppColors.Surface, shadowElevation = 8.dp) {
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
                    Text(
                        if (mode == AskMode.MANUAL) "상황을 물어보세요 (예: 터널 화재)"
                        else "궁금한 걸 물어보세요 (예: 무전기 고장)",
                        color = AppColors.TextHint, fontSize = 13.5.sp
                    )
                },
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.Surface,
                    unfocusedContainerColor = AppColors.Background,
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
            color = AppColors.Surface,
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
private fun IntroBubble(mode: String, total: Int) {
    BotBubble {
        Text("안녕하세요, 또타예요! 🚇", fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary, lineHeight = 21.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            when (mode) {
                AskMode.MANUAL -> "비상대응 현장조치 매뉴얼에서 찾아 드릴게요.\n아래 상황 바로가기를 눌러도 됩니다."
                AskMode.ALL -> "규정과 비상대응 매뉴얼을 함께 찾아 드릴게요."
                else -> "궁금한 걸 낱말이나 짧은 문장으로 물어보세요.\n규정집 9권에서 관련 조문을 찾아 드릴게요."
            } + (if (total > 0) "\n($total 개 항목)" else ""),
            fontSize = 13.5.sp, color = AppColors.TextPrimary, lineHeight = 21.sp
        )
        Spacer(Modifier.height(6.dp))
        Text("'무전기'라고 쳐도 규정에 적힌 '무선전화기' 항목을 찾아냅니다.",
            fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 18.sp)
    }
}

@Composable
private fun ManualMissingBubble() {
    BotBubble {
        Text("매뉴얼이 아직 기기에 없어요", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary)
        Spacer(Modifier.height(5.dp))
        Text("지금은 규정 범위로만 찾아 드립니다. 규정 화면의 '비상대응 현장조치 매뉴얼'에서 먼저 받아 주세요.",
            fontSize = 12.sp, color = AppColors.TextSecondary, lineHeight = 19.sp)
    }
}

@Composable
private fun EmptyBubble() {
    BotBubble {
        Text("관련 내용을 못 찾았어요. 😥", fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary)
        Spacer(Modifier.height(5.dp))
        Text("낱말로 다시 물어보시면 잘 찾습니다.\n예) \"출입문 안 닫힘\", \"비상제동\", \"터널 화재\", \"제38조\"",
            fontSize = 12.5.sp, color = AppColors.TextSecondary, lineHeight = 20.sp)
    }
}

@Composable
private fun ResultBubble(item: ChatItem.Result) {
    BotBubble {
        if (item.weak) {
            // 안전앱이라 자신 없을 땐 자신 없다고 먼저 말한다
            Text("딱 맞는 내용을 못 찾았어요. 😐 비슷해 보이는 것만 보여드릴게요.",
                fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary, lineHeight = 21.sp)
            Spacer(Modifier.height(5.dp))
            Text("규정에 쓰인 말로 바꿔 물어보면 잘 찾습니다.\n예) '뒤에서 밀어도 되나' → '구원', '스크린도어' → '승강장안전문'",
                fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 18.sp)
        } else {
            Text(
                buildAnnotatedString {
                    append("관련 내용 ")
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
        Text("${item.corpus}개 항목 훑는 데 ${"%.1f".format(item.ms)}ms · 인터넷 없이 기기 안에서 계산",
            fontSize = 10.5.sp, color = AppColors.TextHint)

        Spacer(Modifier.height(8.dp))
        // RegulationSearch 가 점수 내림차순으로 정렬해 주므로 첫 번째가 1위다.
        // 결과가 1건뿐이면 비교 대상이 없어 강조가 의미 없다.
        item.top.take(5).forEachIndexed { index, hit ->
            HitCard(hit, best = index == 0 && item.top.size > 1)
        }

        Surface(
            color = AppColors.AnsCautionBg,
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text("⚠️ 답이 아니라 원문입니다. 실제 조치는 원문 전문과 관제 지시를 따르세요.",
                fontSize = 10.5.sp, color = AppColors.TextPrimary, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
        }
    }
}

// ── 결과 카드 (탭하면 원문) ─────────────────────────────────────
@Composable
private fun HitCard(hit: RegHit, best: Boolean = false) {
    var open by remember(hit.article.num, hit.article.title, hit.book) { mutableStateOf(false) }
    val (bg, fg) = bookBadgeColors(hit.book)

    Surface(
        shape = RoundedCornerShape(14.dp),
        // 1위만 아주 연하게 띄운다
        color = if (best) AppColors.AnsConclusionBg else AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (best) Color.Transparent else AppColors.Divider
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
                if (hit.article.num.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(hit.article.num, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                        color = AppColors.Primary)
                }
                Spacer(Modifier.weight(1f))
                Text("관련도 ${Math.round(hit.score)}", fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, color = AppColors.TextHint)
            }
            Spacer(Modifier.height(6.dp))
            Text(markUp(ManualFormat.clean(hit.article.title), hit.marks), fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, lineHeight = 19.sp)
            Spacer(Modifier.height(5.dp))
            if (open) {
                HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 4.dp))
                Text(markUp(ManualFormat.clean(hit.article.body), hit.marks), fontSize = 12.5.sp,
                    color = AppColors.TextPrimary, lineHeight = 22.sp)
            } else {
                Text(markUp(ManualFormat.clean(RegulationSearch.excerpt(hit.article.body, hit.marks)), hit.marks),
                    fontSize = 12.5.sp, color = AppColors.TextSecondary, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (open) "접기 ▴" else "탭하면 원문이 펼쳐집니다 ▾",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.PrimaryLight)
        }
    }
}

/** 규정집 배지 색 (RegulationRepository.bookMeta 와 같은 계열) */
@Composable
private fun bookBadgeColors(book: String): Pair<Color, Color> = when (book) {
    ManualRepository.BOOK_NAME -> AppColors.ManualBadgeBg to AppColors.ManualBadgeFg
    "전동차승무원업무예규" -> Color(0xFFE9F1FD) to Color(0xFF1E3A8A)
    "운전취급규정" -> Color(0xFFE9F6ED) to Color(0xFF2E7D32)
    "운전관계직원업무내규" -> Color(0xFFEDE9FB) to Color(0xFF5E35B1)
    "운전취급세부요령" -> Color(0xFFE0F2F1) to Color(0xFF00695C)
    "차량기지운전취급내규" -> Color(0xFFFFF3E0) to Color(0xFFB26A00)
    "승무원지도운용내규" -> Color(0xFFFCE4EC) to Color(0xFFAD1457)
    "운전무사고성적심사규정" -> Color(0xFFF1F8E9) to Color(0xFF558B2F)
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
private fun FaqChips(faq: List<String>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 14.dp)) {
        faq.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pair.forEach { q ->
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = AppColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
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
