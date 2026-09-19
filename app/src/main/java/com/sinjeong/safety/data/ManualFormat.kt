package com.sinjeong.safety.data

/**
 * 비상대응 현장조치 매뉴얼 — 자료 모양과 "보여 주기 위한 변환"만 담는 곳.
 *
 * 여기에는 안드로이드 API 도, 파일·네트워크도 들어오지 않는다. 순수 함수뿐이라
 * JVM 단위 테스트(app/src/test)가 그대로 돌릴 수 있다 — 표 290개·그림 64개를 다루는
 * 변환이라 눈으로만 확인하면 반드시 빠뜨린다.
 *
 * 내려받기·저장은 [ManualRepository], 파싱은 [ManualJson] 이 맡는다.
 */

/**
 * 표 셀 하나. 병합(colspan/rowspan)이 많아 원본 값을 그대로 들고 다닌다.
 *
 * 스키마 v2: [blocks] 가 있으면 그 셀의 내용을 **순서대로** 담은 블록 목록이다(p/table/image/h —
 * 표 안의 표, 셀 안 그림). 화면은 [blocks] 를 그리고, [text] 는 그 셀의 **전체 평문**(중첩 표 글 포함)이라
 * 검색·원문 대조는 [text] 만 본다. [blocks] 가 없으면(v1 자료·단순 셀) 화면도 [text] 를 그린다.
 */
data class ManualCell(
    val text: String,
    val colspan: Int = 1,
    val rowspan: Int = 1,
    val blocks: List<ManualBlock> = emptyList()
)

/**
 * 섹션 안의 덩어리 하나.
 * [type] 은 "p"(문단) / "h"(소제목) / "table" / "image" 이며,
 * **모르는 값이 와도 문단처럼 그린다** — 서버 스키마가 늘어도 앱이 죽지 않아야 한다.
 */
data class ManualBlock(
    val type: String,
    val text: String = "",
    val level: Int = 1,                             // h 전용 (1~3)
    val rows: List<List<ManualCell>> = emptyList(), // table 전용
    val file: String = "",                          // image 전용
    val caption: String = "",                       // image 전용
    /**
     * image 전용(v2). 도형으로 그린 흐름도를 그림으로 떠 온 것이면 [flow] = true 이고,
     * [alt] 는 도형 안 문구를 위→아래·왼→오로 이은 글이다. **화면에는 그림만** 낸다 —
     * [alt] 는 검색·원문 대조·AI 본문의 재료로만 쓴다(AI 본문에서는 표식으로 감싼다: [ManualFormat.aiBody]).
     * 앱은 [flow] 로 화면을 가르지 않는다(자료의 뜻을 남겨 두는 칸이다).
     */
    val alt: String = "",
    val flow: Boolean = false
)

/** 섹션 하나. [path] 는 상위 제목 계층("부 록" > "5. …"). */
data class ManualSection(
    val id: String,
    val path: List<String>,
    val title: String,
    val blocks: List<ManualBlock>
) {
    /**
     * 이 섹션이 쓰는 그림 파일 (중복 제거, 나온 순서). **표 셀의 `blocks` 안 그림까지** 모은다 —
     * 여기서 빠진 그림은 내려받지도 않으므로 화면에 깨진 그림으로 나온다(흐름도 `flow_*.png` 포함).
     */
    val imageFiles: List<String>
        get() = ManualFormat.imagesIn(blocks).map { it.file }
            .filter { ManualFormat.isSafeImageName(it) }.distinct()
}

data class ManualDoc(
    val title: String,
    val edition: String,
    val sections: List<ManualSection>
) {
    /**
     * 목록·검색·상황 바로가기에 올릴 섹션 — 화면에 그릴 것이 하나라도 있는 것만([ManualFormat.hasContent]).
     * **순서는 [sections] 배열 순서 그대로다.** id 는 열쇠일 뿐이고 문서 순서가 아니다(스키마 v2 에서
     * 새로 갈라져 나온 소절 s072~s091 은 문서 중간중간에 놓인다) — id 로 정렬하거나 번호 크기로 순서를 가정하지 말 것.
     */
    val visibleSections: List<ManualSection> by lazy { sections.filter { ManualFormat.hasContent(it) } }
}

/** 원문 대조용으로 찾아낸 형광 구간 */
data class ManualSpan(val start: Int, val end: Int, val kind: String)

object ManualFormat {

    /** path[0] 세 그룹. 상황 바로가기는 이 그룹에서만 뽑는다. */
    const val GROUP_COVER = "표지 및 변경연혁"
    const val GROUP_SOP = "비상대응 표준운영절차"
    const val GROUP_APPENDIX = "부 록"

    // ── 한컴 전용문자(PUA) ──────────────────────────────────────
    // 한글이 Wingdings 글꼴로 그리던 글자라 안드로이드에서는 □ 로 나온다.
    // **보여 줄 때만** 바꾼다 — 저장한 자료와 검색 색인은 원문 그대로 둔다.
    //
    // ★ 짐작으로 바꾸지 말 것. 1차 구현은 "상자 사이를 잇는 화살표"라는 쓰임만 보고
    //   U+F0EF→↗, U+F0F0→↘, U+F0F3→→ 로 넣었는데 **원본은 ⇦ ⇨ ⇔ 였다.**
    //   136~138쪽 상황보고체계도에서 왼쪽 보고(⇦)가 ↗ 로, 양방향(⇔)이 → 로 보여
    //   **보고 방향이 달라 보였다.** 아래 값은 독립 감사가 PDF 의 글꼴 코드(Wingdings 0xEF/0xF0/0xF3/0x9E)와
    //   확대 렌더링으로 하나씩 확인한 글리프다(manual_build\audit\fidelity_audit.md 결함 D).
    //   새 PUA 가 나오면 표에 추가하기 전에 **원본 인쇄본에서 글리프를 확인**할 것.
    //
    // 변환기도 같은 치환을 해서 스키마 v2 자료에는 PUA 가 남지 않는다. 이 표는 안전망이다.
    // 키를 코드포인트 숫자로 적는 이유: PUA 글자를 소스에 그대로 두면 눈에 안 보여 검수할 수 없다.
    private val PUA: Map<Int, String> = mapOf(
        0xF09E to "·",    // "구조·구급" 사이 가운뎃점
        0xF0EF to "⇦",    // U+21E6 왼쪽
        0xF0F0 to "⇨",    // U+21E8 오른쪽
        0xF0F3 to "⇔",    // U+21D4 양방향
        0xF03DA to "▢"    // U+25A2 둥근 네모 글머리 (보조 평면 — UTF-16 두 칸)
    )

    // 옛(v1) 변환기가 표 셀에 끼워 넣던 자리 표시. v2 자료에는 없지만, 이미 받아 둔 옛 자료가
    // 기기에 남아 있을 수 있어 **화면에서만** 걸러 낸다(자료·검색·인용 대조는 건드리지 않는다).
    private val LEGACY_MARKER = Regex("\\[표\\]|\\[그림:[^\\]]*\\]")

    private fun isPua(cp: Int): Boolean = cp in 0xE000..0xF8FF || cp >= 0xF0000

    /** 표시 직전 단계에서만 부르는 치환 */
    fun clean(text: String): String {
        if (text.isEmpty()) return text
        val s = if (text.indexOf('[') >= 0) LEGACY_MARKER.replace(text, "") else text
        // 대부분의 글에는 전용문자가 없다 — 있을 때만 새로 만든다
        var i = 0
        var found = false
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (isPua(cp)) { found = true; break }
            i += Character.charCount(cp)
        }
        if (!found) return s
        val sb = StringBuilder(s.length)
        i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val to = PUA[cp]
            if (to != null) sb.append(to) else sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    fun escapeHtml(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) when (ch) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&#39;")
            else -> sb.append(ch)
        }
        return sb.toString()
    }

    // ── 평문 두 가지 ────────────────────────────────────────────
    // (가) [plainText]  검색 색인 · 원문 대조(오늘의 비상조치 keyStep) · 실명/오기 판정
    // (나) [aiBody]     AI(askGuide)에 보내는 본문 — (가)와 같되 흐름도 글을 표식으로 감싼다
    //
    // 재료는 둘 다 같다: p/h 글 + 표 셀의 `text` + 그림의 캡션·`alt`.
    // 스키마 v2 에서 셀 `text` 는 **그 셀의 전체 평문**(중첩 표 글 포함)이다. 그래서 셀의 `blocks` 안으로는
    // 글을 찾으러 들어가지 않는다(들어가면 같은 글이 두 번 나온다) — 들어가는 것은 그림의 `alt` 뿐이다.

    /**
     * 흐름도 글 표식. 도형으로 그린 흐름도는 인쇄본에서 그림으로 떠 오고(`flow:true`), 도형 안 문구는
     * `alt` 에 "위→아래·왼→오" 로 이어 붙여 온다. **그 나열 순서는 절차 순서가 아니다**(YES/NO 분기가 있다).
     * AI 가 나열 순서를 절차로 읽지 않게 이 두 줄로 감싸 보낸다. 서버 프롬프트(functions/index.js 의
     * GUIDE_FLOW_TEXT)가 이 글자를 그대로 가리키므로 **한쪽만 고치지 말 것.**
     */
    const val FLOW_OPEN = "〔흐름도 글 — 도형 안 문구 모음. 나열 순서는 절차 순서가 아님〕"
    const val FLOW_CLOSE = "〔흐름도 글 끝〕"

    /** 중첩 한도. 자료가 잘못돼 끝없이 파고드는 일을 막는다(실제 매뉴얼은 표 안의 표 한 겹이다). */
    const val MAX_DEPTH = 4

    /**
     * (가) 섹션을 한 덩어리 글로 편다. 표는 셀을 " | " 로, 행을 줄바꿈으로 잇는다
     * (칸이 사라지면 "기관사 | 열차무선방호" 같은 짝이 붙어 버려 검색이 엉킨다).
     */
    fun plainText(section: ManualSection): String = flatten(section, wrapAlt = false)

    /**
     * (나) AI 에 보낼 본문. 인용문 검증은 서버도 앱도 **"보낸 본문"** 을 기준으로 하므로,
     * 여기서 만든 글을 그대로 보내고 그대로 대조하면 표식이 있어도 저절로 일관된다.
     */
    fun aiBody(section: ManualSection): String = flatten(section, wrapAlt = true)

    /** 블록들 안의 그림을 나온 순서대로 — 표 셀의 `blocks` 안까지 파고든다. */
    fun imagesIn(blocks: List<ManualBlock>, depth: Int = 0): List<ManualBlock> {
        if (depth > MAX_DEPTH) return emptyList()
        val out = ArrayList<ManualBlock>()
        for (b in blocks) {
            if (b.type == "image") out.add(b)
            else if (b.type == "table") {
                for (row in b.rows) for (c in row) out.addAll(imagesIn(c.blocks, depth + 1))
            }
        }
        return out
    }

    private fun flatten(section: ManualSection, wrapAlt: Boolean): String {
        val sb = StringBuilder()
        fun line(s: String) {
            val t = s.trim()
            if (t.isNotEmpty()) { sb.append(t); sb.append('\n') }
        }
        fun altLines(img: ManualBlock) {
            if (img.alt.isBlank()) return
            if (wrapAlt) line(FLOW_OPEN)
            line(img.alt)
            if (wrapAlt) line(FLOW_CLOSE)
        }
        for (b in section.blocks) {
            when (b.type) {
                "table" -> for (row in b.rows) {
                    // 셀 `text` 는 그 셀의 전체 평문이고 **안쪽 흐름도의 alt 까지 담고 있다**(SCHEMA.md §1).
                    // 상황 23-1·23-2 는 흐름도가 1×1 표의 셀 안에 통째로 들어 있어 셀 text == alt 다.
                    val cellTexts = ArrayList<String>(row.size)
                    val flowTexts = ArrayList<String>()
                    for (c in row) {
                        var t = c.text
                        for (img in if (c.blocks.isEmpty()) emptyList() else imagesIn(c.blocks, 1)) {
                            val alt = img.alt
                            if (alt.isBlank()) continue
                            val at = t.indexOf(alt)
                            val inText = at >= 0 || normalizeWs(t).contains(normalizeWs(alt))
                            if (!wrapAlt) {
                                // (가) 셀 글에 이미 들어 있으면 그대로 둔다(한 번만). 안 들어 있을 때만 덧붙인다.
                                if (!inText) flowTexts.add(alt)
                            } else {
                                // (나) 흐름도 글은 **반드시** 표식 안으로. 표 줄에 표식 없이 섞이면
                                // 모델이 상자 글의 나열 순서를 절차 순서로 읽는다.
                                when {
                                    at >= 0 -> { t = t.removeRange(at, at + alt.length); flowTexts.add(alt) }
                                    // 공백만 달라 글자 그대로는 못 덜어 낸다 → 그 셀 글 전체를 흐름도 글로 본다
                                    // (표 글 몇 줄이 표식 안에 들어가는 쪽이, 흐름도 글이 표식 밖에 남는 쪽보다 안전하다)
                                    inText -> { flowTexts.add(t); t = "" }
                                    else -> flowTexts.add(alt)
                                }
                            }
                        }
                        cellTexts.add(t.replace('\n', ' ').trim())
                    }
                    // 흐름도 하나뿐인 1×1 표는 (나)에서 흐름도 글을 덜어 내면 표 줄이 빈다 — 그 빈 줄은 내지 않는다.
                    // (그 밖의 줄은 (가)와 (나)가 글자까지 같아야 한다 — 다른 것은 표식과 흐름도 글의 자리뿐이다)
                    val emptiedByFlow = wrapAlt && flowTexts.isNotEmpty() && cellTexts.none { it.isNotEmpty() }
                    if (!emptiedByFlow) line(cellTexts.joinToString(" | "))
                    for (f in flowTexts) {
                        if (wrapAlt) line(FLOW_OPEN)
                        line(f)
                        if (wrapAlt) line(FLOW_CLOSE)
                    }
                }
                "image" -> { line(b.caption); altLines(b) }
                else -> line(b.text)   // p / h / 모르는 종류
            }
        }
        return sb.toString().trim()
    }

    /**
     * 목록·배지에 붙일 짧은 표지. "3. 역구내 …" → "상황 3", "9-1. …" → "상황 9-1".
     * 번호로 시작하지 않는 섹션(부록·표지)은 빈 값이다.
     */
    fun situationTag(section: ManualSection): String {
        if (section.path.firstOrNull() != GROUP_SOP) return ""
        val m = Regex("^(\\d+(?:-\\d+)?)\\.").find(section.title.trim()) ?: return ""
        return "상황 " + m.groupValues[1]
    }

    /** 상황 바로가기에 올릴 섹션인가 (번호가 붙은 상황만 — "비상상황 유형 선정"은 뺀다) */
    fun isSituation(section: ManualSection): Boolean = situationTag(section).isNotEmpty()

    // ── 실명·전화번호가 든 섹션 가려내기 ────────────────────────
    // 공개된 개인정보처리방침이 "비상연락망·변경연혁 등 직원 이름·전화번호가 담긴 부분은 AI 로 보내지 않습니다"
    // 라고 약속한다. **이 문장이 참이어야 한다.** 뷰어와 검색 결과에는 그대로 나오지만
    // askGuide 의 sources 에는 절대 넣지 않는다 — 부르는 쪽은 [sensitiveIds] 를 쓸 것.
    //
    // 기준은 "의심스러우면 뺀다"이다. 빼서 잃는 것은 AI 답변의 근거 한 건이고(뷰어·검색에는 그대로 나온다),
    // 잘못 보내서 잃는 것은 공개 약속이다.

    // 지역번호 뒤 구분자는 두 글자까지 — "(02) 1234-5678" 는 ")" 와 공백이 연달아 온다
    private val PHONE = Regex("0\\d{1,2}[-)\\s]{0,2}\\d{3,4}[-\\s]?\\d{4}")
    // 지역번호 없이 번호만 적은 꼴(경찰서 연락망 62개가 전부 이 꼴이다). 앞뒤에 숫자·줄표가 붙은 것(날짜·코드)은 뺀다.
    private val PHONE_LOCAL = Regex("(?<![\\d-])\\d{3,4}-\\d{4}(?![\\d-])")
    // 휴대전화는 기관 대표번호일 수 없다 → 하나만 있어도 개인 연락처로 본다
    private val PHONE_MOBILE = Regex("(?<!\\d)01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)")
    private val SENSITIVE_WORDS = listOf("연락망", "연락처", "변경연혁", "임무카드")
    // 표의 머리글이 이것이면 그 표는 사람을 적는 표다(칸 글에서 공백을 뺀 뒤 통째로 같을 때만).
    // "담당자" 는 2026.9판(s042)에서는 직책만 적혀 있지만, 다음 판에 이름이 채워질 수 있는 칸이라 같이 뺀다.
    private val NAME_HEADERS = setOf("성명", "이름", "성함", "담당자")
    private const val PHONE_LIMIT = 5

    /** 기관·부서 번호를 포함해 전화번호 꼴이 몇 개인가 (지역번호형 + 번호만 적은 꼴, 겹쳐 세지 않는다) */
    fun phoneCount(text: String): Int =
        PHONE.findAll(text).count() + PHONE_LOCAL.findAll(PHONE.replace(text, " ")).count()

    private fun hasNameHeader(blocks: List<ManualBlock>, depth: Int = 0): Boolean {
        if (depth > MAX_DEPTH) return false
        for (b in blocks) {
            if (b.type != "table") continue
            for (row in b.rows) for (c in row) {
                // v2 의 셀 text 는 안쪽 표의 글까지 줄 단위로 담고 있어 줄마다 본다
                if (c.text.lineSequence().any { it.filterNot(Char::isWhitespace) in NAME_HEADERS }) return true
                if (hasNameHeader(c.blocks, depth + 1)) return true
            }
        }
        return false
    }

    /** 연락망·명부류인가 (표지/변경연혁, 제목·경로의 낱말, 전화번호 [PHONE_LIMIT]개 이상) — 같은 장의 소절에 물려 준다 */
    private fun isContactLike(section: ManualSection, plain: String): Boolean {
        if (section.path.firstOrNull() == GROUP_COVER) return true
        if (SENSITIVE_WORDS.any { w -> section.title.contains(w) || section.path.any { it.contains(w) } }) return true
        return phoneCount(plain) >= PHONE_LIMIT
    }

    /** 이 섹션 **자체의 내용**으로 본 판정. 같은 장에서 물려받는 것까지 보려면 [sensitiveIds]. */
    fun isSensitive(section: ManualSection): Boolean {
        val plain = plainText(section)
        return isContactLike(section, plain) ||
            PHONE_MOBILE.containsMatchIn(plain) ||
            hasNameHeader(section.blocks)
    }

    /**
     * AI 에 보내면 안 되는 섹션 id 전부. [isSensitive] 에 더해 **같은 장(같은 path)의 소절은 같은 취급**을 한다:
     * 스키마 v2 에서 소절이 제 섹션으로 갈라져 나오면서(SCHEMA.md §5) 연락망 장의 소절 가운데
     * 제목에 "연락망" 이 없고 전화번호도 5개가 안 되는 것이 생길 수 있다(2026.9판의 s090 = 6-6, s065 에서 갈라져 나옴).
     * 물려 주는 쪽은 연락망·명부류([isContactLike])뿐이다 — 머리글 하나 때문에 장 전체를 빼지는 않는다.
     * 그리고 path 가 두 단계 이상(부록의 장)일 때만이다: 표준운영절차는 path 가 한 단계라 섹션 하나 때문에
     * 상황 34개가 통째로 빠지는 일이 없어야 한다.
     */
    fun sensitiveIds(doc: ManualDoc): Set<String> {
        val out = HashSet<String>()
        val contactPaths = HashSet<List<String>>()
        for (s in doc.sections) {
            val plain = plainText(s)
            val contact = isContactLike(s, plain)
            if (contact && s.path.size >= 2) contactPaths.add(s.path)
            if (contact || PHONE_MOBILE.containsMatchIn(plain) || hasNameHeader(s.blocks)) out.add(s.id)
        }
        for (s in doc.sections) if (s.path in contactPaths) out.add(s.id)
        return out
    }

    /**
     * 화면에 그릴 것이 하나라도 있는 섹션인가. 글이 0자여도 그림이 있으면 내용이 있는 것이다
     * (2026.9판의 s076·s090 은 그림뿐이다 — 숨기면 안 된다, SCHEMA.md §5).
     * 정말로 아무것도 없는 섹션만 목록·검색에서 빼서 "눌렀더니 빈 화면" 을 막는다.
     */
    fun hasContent(section: ManualSection): Boolean = hasContent(section.blocks, 0)

    private fun hasContent(blocks: List<ManualBlock>, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        for (b in blocks) {
            when (b.type) {
                "image" -> if (isSafeImageName(b.file)) return true
                "table" -> for (row in b.rows) for (c in row) {
                    if (c.text.isNotBlank() || hasContent(c.blocks, depth + 1)) return true
                }
                else -> if (b.text.isNotBlank()) return true
            }
        }
        return false
    }

    // ── 원본 문서의 오기 안내 ───────────────────────────────────
    // 2026.9판 원본 HWP 에는 "운전"이 "종합"으로 잘못 일괄 치환된 곳이 많다
    // (종합실=운전실, 주의종합=주의운전, 확인종합=확인운전, 종합지시=운전지시 …).
    // 앱은 원문을 고치지 않고 그대로 보여 준다 — 대신 그렇다는 사실을 한 줄로 알린다.
    const val TYPO_NOTICE =
        "원본 문서에 '운전'이 '종합'으로 잘못 적힌 곳이 있습니다(예: 종합실 → 운전실). 원문은 그대로 보여드립니다."
    private val TYPO_MARKERS = listOf("종합실", "주의종합")

    /**
     * 이 매뉴얼에 그 오기가 들어 있는가. **있을 때만** 안내를 띄운다 —
     * 수정본 매뉴얼이 올라오면 앱을 고치지 않아도 안내가 저절로 사라진다.
     * ("종합관제"처럼 본래 '종합'이 맞는 말은 표지로 쓰지 않는다.)
     */
    fun hasDrivingTypo(doc: ManualDoc): Boolean = doc.sections.any { s ->
        val plain = plainText(s)
        s.title.let { t -> TYPO_MARKERS.any { t.contains(it) } } || TYPO_MARKERS.any { plain.contains(it) }
    }

    // ── 원문 대조 ───────────────────────────────────────────────
    /**
     * 공백·줄바꿈 차이를 없앤 형태. 서버(`functions/askGuide.core.js` 의 normalizeSpace)와
     * **글자 집합까지 같아야** 배지가 맞는다.
     *
     * 정규식의 공백 클래스(역슬래시 s)를 쓰면 안 된다 — 뜻이 실행 환경마다 다르다.
     * 서버 JS 는 NBSP·전각 공백(U+3000)·U+FEFF 까지 공백으로 보는데, JVM 단위 테스트에서는
     * ASCII 공백 6개뿐이고 안드로이드(ICU)는 또 다른 집합이다. 한글 문서에는 전각 공백과
     * NBSP 가 실제로 나온다. 그래서 ECMAScript 의 WhiteSpace + LineTerminator 를 그대로 적는다.
     * 끝 다듬기도 `trim()`(코틀린은 U+001C~U+001F·U+0085 까지 깎는다)이 아니라 공백 한 글자만 깎는다
     * — 앞에서 모든 공백 덩어리가 이미 공백 한 글자가 됐으므로 그것으로 충분하다.
     */
    private val JS_WS = Regex(
        "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+"
    )
    fun normalizeWs(text: String): String = text.replace(JS_WS, " ").trim(' ')

    /**
     * AI 에 보낼 본문 자르기 (reg 2000 / manual 6000자 — 서버 BODY_LIMIT 과 같은 값).
     * 서버는 UTF-16 단위로 자르고(`slice`) 코틀린 `take` 도 같다. 다만 자른 끝이
     * 서러게이트 쌍의 앞 절반이면(매뉴얼의 U+F03DA 글머리표) 전송 중에 다른 글자로
     * 바뀌어 서버와 앱이 서로 다른 본문을 들게 된다 — 그 반쪽은 떼고 보낸다.
     * (짧아진 본문은 서버 한도 안이라 서버가 다시 자르지 않는다.)
     */
    fun cutBody(body: String, limit: Int): String {
        val cut = body.take(limit)
        return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    /**
     * 그림 파일 이름 화이트리스트: 영숫자·`_`·`-`·`.` 만, 점으로 시작하지 않고 `..` 없음.
     * manual.json 은 우리 Storage 에서 오지만, 이름이 그대로 `File(dir, name)` 과
     * `<img src>` 에 들어가므로 `../` 가 섞이면 매뉴얼 폴더 밖을 쓰거나 읽게 된다.
     */
    private val SAFE_IMAGE = Regex("^[A-Za-z0-9_-][A-Za-z0-9_.-]*$")
    fun isSafeImageName(name: String): Boolean =
        name.length <= 100 && SAFE_IMAGE.matches(name) && !name.contains("..")

    /**
     * "사고흐름도" 는 소제목 25곳 중 1곳(s014)만 원본 글자 모양이 달라 p 로 남았다.
     * 자료는 그대로 두고 **표시할 때만** level 1 제목처럼 그린다.
     */
    private const val FLOW_TITLE = "사고흐름도"
    private fun isFlowTitle(b: ManualBlock): Boolean = b.type == "p" && b.text.trim() == FLOW_TITLE

    /** AI가 인용한 문장이 진짜 그 원문 안에 있는가 */
    fun quoteMatches(quote: String, body: String): Boolean {
        val q = normalizeWs(quote)
        if (q.isEmpty()) return false
        return normalizeWs(body).contains(q)
    }

    /**
     * 인용문 안에서 형광 칠할 구간을 찾는다. 앞선 구간과 겹치면 다음 자리를 찾고,
     * 못 찾으면 그 형광은 조용히 버린다(엉뚱한 자리를 칠하느니 안 칠하는 게 낫다).
     */
    fun highlightSpans(quote: String, highlights: List<Pair<String, String>>): List<ManualSpan> {
        if (quote.isEmpty() || highlights.isEmpty()) return emptyList()
        val taken = BooleanArray(quote.length)
        val out = ArrayList<ManualSpan>(highlights.size)
        for ((text, kind) in highlights) {
            if (text.isBlank()) continue
            var from = 0
            while (true) {
                val i = quote.indexOf(text, from)
                if (i < 0) break
                val end = i + text.length
                var free = true
                for (k in i until end) if (taken[k]) { free = false; break }
                if (free) {
                    for (k in i until end) taken[k] = true
                    out.add(ManualSpan(i, end, kind))
                    break
                }
                from = i + 1
            }
        }
        return out.sortedBy { it.start }
    }

    // ── WebView 용 HTML ─────────────────────────────────────────
    // 표 293개에 병합 셀이 잔뜩이라 Compose 로 직접 그리지 않는다.
    // 스크립트는 끄고 네트워크도 막으므로, 이 HTML 은 그림 파일만 읽는다.
    private fun css(dark: Boolean): String {
        val fg = if (dark) "#E4E6F0" else "#1A1C2E"
        val bg = if (dark) "#1C1E27" else "#FFFFFF"
        val sub = if (dark) "#9BA2B8" else "#7B8194"
        val secBg = if (dark) "#262238" else "#F3F0FF"
        val h1Bg = if (dark) "#1B2535" else "#EEF5FF"
        val h2Bg = if (dark) "#17302A" else "#EFFAF5"
        val h3Bar = if (dark) "#5A4030" else "#FFD9B8"
        val line = if (dark) "#343A44" else "#E6E9EF"
        val headBg = if (dark) "#20242B" else "#F6F8FB"
        return """
body{margin:0;padding:14px 14px 28px;background:$bg;color:$fg;
 font-family:-apple-system,"Noto Sans KR",sans-serif;font-size:16px;line-height:1.65;
 -webkit-text-size-adjust:100%}
.sec{background:$secBg;border-radius:12px;padding:12px 14px;font-weight:700;font-size:17px;line-height:1.5}
.path{color:$sub;font-size:12px;margin:8px 2px 0}
p{margin:.5em 2px;white-space:pre-wrap}
h1,h2,h3{margin:18px 0 8px;line-height:1.5}
h1{background:$h1Bg;border-radius:10px;padding:9px 12px;font-size:17px;font-weight:700}
h2{background:$h2Bg;border-radius:10px;padding:8px 12px;font-size:16px;font-weight:700}
h3{border-left:3px solid $h3Bar;padding:0 0 0 9px;font-size:15px;font-weight:700}
.tw{overflow-x:auto;margin:10px 0;-webkit-overflow-scrolling:touch}
table{border-collapse:collapse;font-size:14px;min-width:100%}
td{border:1px solid $line;padding:6px 8px;vertical-align:top;white-space:pre-wrap;word-break:break-word}
tr.hd td{background:$headBg;font-weight:700}
td p{margin:.3em 0}
td p:first-child{margin-top:0}
td p:last-child{margin-bottom:0}
td h1,td h2,td h3{margin:6px 0 4px;font-size:14px;padding:4px 8px}
td h3{padding:0 0 0 8px}
td figure{margin:4px 0}
td img{max-width:78vw}
table.in{min-width:0;margin:4px 0;font-size:13px}
table.in td{padding:4px 6px}
figure{margin:12px 0}
img{max-width:100%;height:auto;border-radius:6px}
.cap{color:$sub;font-size:12px;margin-top:4px}
.ed{color:$sub;font-size:12px;margin-top:22px;text-align:center}
.nt{color:$sub;font-size:12px;line-height:1.55;margin-top:10px;text-align:center}
""".trimIndent()
    }

    /**
     * 섹션 하나를 통째로 HTML 로. [imageBase] 는 loadDataWithBaseURL 의 기준 폴더 기준
     * 상대 경로 앞머리(예: "images/") 다.
     */
    fun html(
        section: ManualSection,
        dark: Boolean,
        edition: String = "",
        imageBase: String = "images/",
        /** 맨 아래 작은 회색 안내 한 줄 (원본 오기 안내 등). 비어 있으면 안 그린다. */
        notice: String = ""
    ): String {
        val sb = StringBuilder(4096)
        sb.append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        sb.append("<style>").append(css(dark)).append("</style></head><body>")

        if (section.path.isNotEmpty()) {
            sb.append("<div class=\"path\">")
                .append(escapeHtml(clean(section.path.joinToString(" › "))))
                .append("</div>")
        }
        sb.append("<div class=\"sec\">").append(escapeHtml(clean(section.title))).append("</div>")

        for (b in section.blocks) appendBlock(sb, b, imageBase, 0)

        if (edition.isNotBlank()) {
            sb.append("<div class=\"ed\">").append(escapeHtml(edition)).append("판</div>")
        }
        if (notice.isNotBlank()) {
            sb.append("<div class=\"nt\">").append(escapeHtml(notice)).append("</div>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    /**
     * 그림 한 장만 감싼 1×1 표이면 그 그림, 아니면 null. 셀에 그림 말고 **글이 따로 있으면** 표로 둔다
     * (흐름도 셀은 text == alt 인데, alt 는 어차피 화면에 글로 내지 않으므로 그림만 그려도 잃는 글이 없다).
     */
    fun imageOnlyTable(b: ManualBlock): ManualBlock? {
        if (b.type != "table" || b.rows.size != 1 || b.rows[0].size != 1) return null
        val cell = b.rows[0][0]
        val img = cell.blocks.singleOrNull() ?: return null
        if (img.type != "image" || !isSafeImageName(img.file)) return null
        val onlyAlt = cell.text.isBlank() || normalizeWs(cell.text) == normalizeWs(img.alt)
        return if (onlyAlt) img else null
    }

    /**
     * 블록 하나를 그린다. [depth] 0 = 섹션 바로 아래.
     *
     * 스키마 v2: 표 셀에 `blocks` 가 있으면 그 안을 **같은 함수로 다시** 그린다(표 안의 표, 셀 안 그림).
     * 없으면 예전처럼 셀 `text` 를 그린다. 모르는 종류는 어느 깊이에서든 문단처럼 그린다.
     */
    private fun appendBlock(sb: StringBuilder, b: ManualBlock, imageBase: String, depth: Int) {
        when (b.type) {
            "h" -> {
                val t = clean(b.text).trim()
                if (t.isEmpty()) return
                val tag = "h" + b.level.coerceIn(1, 3)
                sb.append('<').append(tag).append('>').append(escapeHtml(t))
                    .append("</").append(tag).append('>')
            }
            "table" -> {
                if (b.rows.isEmpty()) return
                // 그림 한 장을 감싼 1×1 표(상황별 "사고흐름도" 31곳)는 테두리 없이 그림만 그린다.
                // 셀 안에 두면 그림 폭이 셀에 갇혀 흐름도 글씨가 더 작아진다.
                imageOnlyTable(b)?.let { appendBlock(sb, it, imageBase, depth); return }
                val top = depth == 0
                // 가로 스크롤 상자는 바깥 표에만 둔다 (셀 안에서 또 스크롤되면 손가락이 갇힌다)
                sb.append(if (top) "<div class=\"tw\"><table>" else "<table class=\"in\">")
                b.rows.forEachIndexed { ri, row ->
                    // 바깥 표의 첫 행만 머리 행으로 본다. 원본에 머리 행 표시가 없어
                    // 더 영리하게 굴면 자료 행을 잘못 칠한다(셀 안의 작은 표는 더 그렇다).
                    sb.append(if (top && ri == 0) "<tr class=\"hd\">" else "<tr>")
                    for (c in row) {
                        sb.append("<td")
                        if (c.colspan > 1) sb.append(" colspan=\"").append(c.colspan).append('"')
                        if (c.rowspan > 1) sb.append(" rowspan=\"").append(c.rowspan).append('"')
                        sb.append('>')
                        val before = sb.length
                        if (c.blocks.isNotEmpty() && depth < MAX_DEPTH) {
                            for (cb in c.blocks) appendBlock(sb, cb, imageBase, depth + 1)
                        }
                        // blocks 가 없거나, 있는데 그릴 게 하나도 안 나왔으면(빈 문단·막힌 그림뿐) 글로 그린다
                        if (sb.length == before) sb.append(escapeHtml(clean(c.text)))
                        sb.append("</td>")
                    }
                    sb.append("</tr>")
                }
                sb.append(if (top) "</table></div>" else "</table>")
            }
            "image" -> {
                if (!isSafeImageName(b.file)) return
                // alt(흐름도 도형 안 문구)는 **화면에 글로 내지 않는다** — 그림이 원본이다.
                // 화면낭독기용 alt 속성으로만 싣는다.
                sb.append("<figure><img src=\"").append(escapeHtml(imageBase + b.file))
                    .append("\" alt=\"").append(escapeHtml(clean(b.alt).trim())).append("\">")
                val cap = clean(b.caption).trim()
                if (cap.isNotEmpty()) {
                    sb.append("<div class=\"cap\">").append(escapeHtml(cap)).append("</div>")
                }
                sb.append("</figure>")
            }
            else -> {
                // p 와 "아직 모르는 종류" 는 같은 취급
                val t = clean(b.text)
                if (t.isBlank()) return
                if (isFlowTitle(b)) sb.append("<h1>").append(escapeHtml(t.trim())).append("</h1>")
                else sb.append("<p>").append(escapeHtml(t)).append("</p>")
            }
        }
    }
}
