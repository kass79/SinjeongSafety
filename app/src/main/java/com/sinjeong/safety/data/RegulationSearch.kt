package com.sinjeong.safety.data

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * 규정 물어보기 — 기기 안에서만 도는 조문 검색 엔진.
 *
 * 인터넷·API를 전혀 쓰지 않는다(터널 대비). assets/regulations.json 626조문을
 * 그대로 훑어서 점수를 매긴다. 626개면 색인 없이도 한 번에 10~30ms라 충분히 빠르다.
 *
 * 화면 코드는 여기에 넣지 않는다. 2단계(AI 답변)에서 화면만 갈아끼우고
 * 이 파일은 그대로 재료로 쓰기 위해서다.
 */

/** 검색 결과 한 건 */
data class RegHit(
    val book: String,
    val article: RegArticle,
    val score: Double,
    val marks: List<String>   // 실제로 걸린 낱말 (형광펜 칠할 대상)
)

object RegulationSearch {

    /** 1등 점수가 이 값 미만이면 "딱 맞는 조문을 못 찾았어요"부터 띄운다.
     *  실측: 제대로 맞은 질문의 1등은 47~71점, 헛다리 짚은 질문의 1등은 22점. */
    const val WEAK_SCORE = 30.0

    // ── 규정집 가중치 : 현장에서 급할 때 필요한 건 예규와 운전취급규정 ──
    private val BOOK_WEIGHT = mapOf(
        "전동차승무원업무예규" to 1.15,
        "운전취급규정" to 1.10,
        "인사규정" to 1.00,
        "취업규칙" to 1.00
    )

    /** 배지에 쓸 짧은 이름 */
    fun shortBookName(name: String): String =
        if (name == "전동차승무원업무예규") "승무원업무예규" else name

    // ── 승무 도메인 동의어 사전 ─────────────────────────────────
    // 한 줄 = 한 그룹. 그룹 안의 말은 서로 바꿔 검색한다.
    // (현장에서 쓰는 말 ↔ 규정에 쓰인 말 을 잇는 게 목적)
    private val SYN: List<List<String>> = listOf(
        listOf("무전기", "무선", "무선전화기", "열차무선"),
        listOf("열차", "전동차", "차량"),
        listOf("기관사", "운전자", "승무원", "운전업무종사자"),
        listOf("출입문", "객실문", "도어", "승강문", "차문"),
        listOf("승강장안전문", "스크린도어", "안전문", "psd"),
        listOf("고장", "장애", "불량", "이상", "작동불능", "불능"),
        listOf("지연", "늦", "회복운전", "정시", "운전정리"),
        listOf("정차", "정지", "멈춤", "비상정차"),
        listOf("제동", "브레이크", "제동장치", "비상제동"),
        listOf("신호", "신호기", "현시", "차내신호"),
        listOf("사고", "이례상황", "재해", "인전사고"),
        listOf("비상", "응급", "긴급", "구원"),
        listOf("승객", "여객", "고객"),
        listOf("관제", "관제사", "관제실", "종합관제", "운전관제"),
        listOf("휴가", "연가", "연차", "휴일", "병가", "휴무"),
        listOf("근무", "복무", "근무시간", "출근", "퇴근", "당직"),
        listOf("징계", "처벌", "문책", "견책", "정직"),
        listOf("음주", "주취", "음주측정", "술"),
        listOf("속도", "제한속도", "운전속도", "서행"),
        listOf("교대", "인수인계", "승무교대"),
        listOf("점검", "확인", "검수", "출고점검"),
        listOf("방호", "열차방호", "정지수신호"),
        listOf("화재", "불", "연기"),
        listOf("전호", "부져", "기적", "경적")
    )

    /** 낱말 → 그룹 번호 */
    private val SYNMAP: Map<String, Int> = HashMap<String, Int>().apply {
        SYN.forEachIndexed { i, group -> group.forEach { w -> put(w, i) } }
    }

    // ── 버리는 말 ───────────────────────────────────────────────
    // "휴가 규정"처럼 물을 때 '규정'은 조문 제목에도 널려 있어서
    // 그대로 두면 엉뚱한 조문(취업규칙 제2조 경과규정)이 1등이 된다.
    private val STOP = setOf(
        "규정", "규칙", "예규", "조문", "조항", "관련", "내용", "사항", "경우", "방법", "대해", "대한",
        "어떻게", "무엇", "뭐야", "뭔가", "언제", "어디", "누가", "알려줘", "알려", "있나요", "있나",
        "하나요", "되나요", "인가요", "해야", "때는", "이때", "그때", "뭘", "좀"
    )

    // ── 조사(은/는/이/가…) 떼기 : 긴 것부터 검사 ─────────────────
    private val JOSA = listOf(
        "으로써", "으로서", "에서는", "에게서", "이라는", "라는", "으로", "에서", "에게", "부터", "까지",
        "보다", "처럼", "한테", "이나", "에는", "은", "는", "이", "가", "을", "를", "에", "의", "로", "와",
        "과", "도", "만", "랑", "요"
    )

    /** 낱말 자체 + 조사를 뗀 형태. 떼고 나서 2글자 미만이면 안 뗀다. */
    private fun stems(token: String): List<String> {
        val out = ArrayList<String>(2)
        out.add(token)
        if (token.length >= 3) {
            for (j in JOSA) {
                if (token.length - j.length >= 2 && token.endsWith(j)) {
                    out.add(token.substring(0, token.length - j.length))
                    break
                }
            }
        }
        return out
    }

    /** 검색어 그룹 하나 (head = 내가 친 말, terms = head + 동의어) */
    private data class Group(val head: String, val terms: List<String>)

    private val SPLIT = Regex("[^0-9a-z가-힣]+")
    private val DIGITS = Regex("^[0-9]+$")

    /** 질문 → 검색어 그룹 목록 */
    private fun buildGroups(query: String): List<Group> {
        var raw = SPLIT.split(query.lowercase()).filter { t ->
            // 두 글자 이상만 쓰되, 사전에 등록된 한 글자 낱말(술·불…)은 살린다
            t.isNotEmpty() && (t.length >= 2 || DIGITS.matches(t) || SYNMAP.containsKey(t))
        }
        // 버리는 말 제거. 다 버려서 남는 게 없으면 원래대로 되돌린다.
        val kept = raw.filter { it !in STOP }
        val onlyStop = kept.isEmpty()
        if (!onlyStop) raw = kept

        val groups = ArrayList<Group>()
        val seen = HashSet<String>()
        for (token in raw) {
            for (s in stems(token)) {
                if (!seen.add(s)) continue
                if (!onlyStop && s in STOP) continue
                val gi = SYNMAP[s]
                // 원래 낱말이 맨 앞(가중치 1.0), 나머지 동의어는 0.7
                val terms = if (gi == null) listOf(s)
                else listOf(s) + SYN[gi].filter { it != s }
                groups.add(Group(s, terms))
            }
        }
        return groups
    }

    /** 조문 번호를 직접 부른 경우 ("38조", "제 38 조", "제38조의2") */
    private val ARTICLE_NO = Regex("제?\\s*([0-9]{1,3})\\s*조(?:\\s*의\\s*([0-9]+))?")

    private fun parseArticleNo(query: String): String? {
        val m = ARTICLE_NO.find(query) ?: return null
        val sub = m.groupValues[2]
        return "제" + m.groupValues[1] + "조" + (if (sub.isNotEmpty()) "의$sub" else "")
    }

    // ── 조문 뭉치 (소문자 본문을 미리 만들어 둔다) ──────────────
    private class Doc(
        val book: String,
        val article: RegArticle,
        val titleLower: String,
        val bodyLower: String
    )

    @Volatile private var corpusSource: List<RegBook>? = null
    private var docs: List<Doc> = emptyList()
    private val dfCache = HashMap<String, Int>()

    /** 규정집이 바뀌었을 때만 다시 만든다 (앱 실행 중엔 한 번뿐) */
    @Synchronized
    private fun corpus(books: List<RegBook>): List<Doc> {
        if (corpusSource === books && docs.isNotEmpty()) return docs
        docs = books.flatMap { book ->
            book.articles.map { a ->
                Doc(book.name, a, a.title.lowercase(), a.body.lowercase())
            }
        }
        dfCache.clear()
        corpusSource = books
        return docs
    }

    /** 그 말이 나오는 조문 수 (한 번 센 건 기억해 둔다) */
    @Synchronized
    private fun df(term: String, list: List<Doc>): Int =
        dfCache.getOrPut(term) {
            var c = 0
            for (d in list) if (d.titleLower.contains(term) || d.bodyLower.contains(term)) c++
            c
        }

    /** 흔한 말은 점수를 깎는 장치 */
    private fun idf(term: String, list: List<Doc>): Double =
        1.0 + log10(list.size.toDouble() / (1 + df(term, list)))

    private fun countIn(hay: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var c = 0
        var i = hay.indexOf(needle)
        while (i >= 0) {
            c++
            i = hay.indexOf(needle, i + needle.length)
        }
        return c
    }

    /**
     * 점수식 — 조문 하나당
     *   검색어 그룹마다 (그룹 안에서 제일 높은 점수 하나만 채택)
     *      제목에 있으면   idf × 12 × 감쇠
     *      본문에 있으면   idf × (4 + 나온 횟수, 최대 4) × 감쇠
     *      감쇠 = 내가 친 말이면 1.0 / 사전이 넓혀준 동의어면 0.7
     *   + 커버리지 보너스  맞춘 그룹 ÷ 전체 그룹 × 18
     *   + 제목에 질문 문구가 통째로 들어있으면 +25
     *   + 조문 번호를 직접 불렀으면 +120
     *   × 규정집 가중치
     */
    fun search(books: List<RegBook>, query: String): List<RegHit> {
        val list = corpus(books)
        if (list.isEmpty()) return emptyList()

        val groups = buildGroups(query)
        val wantNo = parseArticleNo(query)
        val qlow = query.lowercase().trim()
        if (groups.isEmpty() && wantNo == null) return emptyList()

        val res = ArrayList<RegHit>()
        for (d in list) {
            var s = 0.0
            var hit = 0
            val marks = ArrayList<String>()

            for (g in groups) {
                var best = 0.0
                for (term in g.terms) {
                    val decay = if (term == g.head) 1.0 else 0.7
                    val w = idf(term, list) * decay
                    var sc = 0.0
                    if (d.titleLower.contains(term)) sc = w * 12
                    val cnt = countIn(d.bodyLower, term)
                    if (cnt > 0) sc = max(sc, w * (4 + min(cnt, 4)))
                    if (sc > best) best = sc
                    if (sc > 0 && term !in marks) marks.add(term)
                }
                if (best > 0) hit++
                s += best
            }

            if (hit > 0 && groups.isNotEmpty()) s += 18.0 * hit / groups.size
            if (qlow.length >= 3 && d.titleLower.contains(qlow)) s += 25.0
            if (wantNo != null && d.article.num == wantNo) s += 120.0

            if (s <= 0.0) continue
            s *= (BOOK_WEIGHT[d.book] ?: 1.0)
            res.add(RegHit(d.book, d.article, s, marks))
        }
        res.sortByDescending { it.score }
        return res
    }

    /** 본문 발췌 : 맞은 낱말 주변만 잘라 보여준다 */
    fun excerpt(body: String, marks: List<String>): String {
        val low = body.lowercase()
        var pos = -1
        var len = 0
        for (m in marks) {
            val p = low.indexOf(m)
            if (p >= 0 && (pos < 0 || p < pos)) {
                pos = p
                len = m.length
            }
        }
        if (pos < 0) return body.take(110) + (if (body.length > 110) "…" else "")
        val st = max(0, pos - 45)
        val en = min(body.length, pos + len + 95)
        return (if (st > 0) "…" else "") + body.substring(st, en) + (if (en < body.length) "…" else "")
    }
}
