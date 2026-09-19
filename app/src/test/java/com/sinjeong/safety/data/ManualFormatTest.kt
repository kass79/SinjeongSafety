package com.sinjeong.safety.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 매뉴얼 변환 로직 점검 (JVM 단위 테스트 — 기기 없이 돈다).
 *   `gradle :app:testDebugUnitTest`
 *
 * 여기서 잡으려는 사고:
 *  - 표 셀에 `<`·`&` 가 들어 있을 때 HTML 이 깨지는 것
 *  - 한컴 전용문자가 □ 로 나오는 것
 *  - 표를 평문화할 때 칸 구분이 사라져 검색이 엉키는 것
 *  - 인용문 대조가 공백 차이로 전부 "원문 아님" 이 되는 것
 *  - 실명·연락처 섹션이 AI 요청에 섞여 들어가는 것
 */
class ManualFormatTest {

    private fun section(
        id: String = "s001",
        path: List<String> = listOf(ManualFormat.GROUP_SOP),
        title: String = "3. 역구내 열차 탈선사고",
        blocks: List<ManualBlock> = emptyList()
    ) = ManualSection(id, path, title, blocks)

    /**
     * 치환 결과를 **코드포인트 숫자로** 못박는다. 1차 구현은 쓰임만 보고 ↗ ↘ → 로 넣었는데
     * 원본(136~138쪽 상황보고체계도)은 ⇦ ⇨ ⇔ 였다 — 왼쪽 보고가 ↗ 로, 양방향이 → 로 보여 보고 방향이 달라 보였다.
     * 값의 근거: 독립 감사가 PDF 글꼴 코드(Wingdings 0xEF/0xF0/0xF3/0x9E)와 확대 렌더링으로 확인.
     * 이 표를 바꾸려면 **원본 인쇄본에서 글리프를 다시 확인**하고 나서 바꿀 것.
     */
    @Test
    fun `한컴 전용문자는 원본 글리프로 바뀐다`() {
        val expected = mapOf(
            0xF09E to 0x00B7,    // · 가운뎃점
            0xF0EF to 0x21E6,    // ⇦ 왼쪽
            0xF0F0 to 0x21E8,    // ⇨ 오른쪽
            0xF0F3 to 0x21D4,    // ⇔ 양방향
            0xF03DA to 0x25A2    // ▢ 둥근 네모 글머리 (보조 평면 → 한 칸짜리 글자)
        )
        for ((from, to) in expected) {
            val out = ManualFormat.clean(cp(from))
            assertEquals("U+" + Integer.toHexString(from), cp(to), out)
            assertEquals(1, out.codePointCount(0, out.length))
        }
        // 예전에 잘못 넣었던 화살표가 다시 들어오지 않게
        val arrows = ManualFormat.clean(cp(0xF0EF) + cp(0xF0F0) + cp(0xF0F3))
        for (wrong in listOf(0x2197, 0x2198, 0x2192)) {   // ↗ ↘ →
            assertFalse("U+" + Integer.toHexString(wrong), arrows.contains(cp(wrong)))
        }
        // 방향이 뒤바뀌지 않았는가: 왼쪽 화살표는 왼쪽으로, 오른쪽 화살표는 오른쪽으로
        assertEquals("가 " + cp(0x21E6) + " 나 " + cp(0x21E8) + " 다",
            ManualFormat.clean("가 " + cp(0xF0EF) + " 나 " + cp(0xF0F0) + " 다"))
        assertEquals("구조·구급", ManualFormat.clean("구조" + cp(0xF09E) + "구급"))
        assertEquals(cp(0x25A2) + " 최근 5년간", ManualFormat.clean(cp(0xF03DA) + " 최근 5년간"))
        // 섞여 있어도 나머지 글자는 건드리지 않는다. 모르는 전용문자는 그대로 둔다(짐작으로 바꾸지 않는다)
        assertEquals("가" + cp(0x21D4) + "나", ManualFormat.clean("가" + cp(0xF0F3) + "나"))
        assertEquals("가" + cp(0xF123) + "나", ManualFormat.clean("가" + cp(0xF123) + "나"))
        assertEquals("보통 글", ManualFormat.clean("보통 글"))
    }

    @Test
    fun `HTML 은 반드시 이스케이프된다`() {
        val s = section(
            blocks = listOf(
                ManualBlock(type = "p", text = "<script>alert('x')</script> & \"따옴표\"")
            )
        )
        val html = ManualFormat.html(s, dark = false)
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&amp;"))
    }

    @Test
    fun `표는 병합 정보를 살리고 첫 행만 머리 행이 된다`() {
        val s = section(
            blocks = listOf(
                ManualBlock(
                    type = "table",
                    rows = listOf(
                        listOf(ManualCell("시간"), ManualCell("조치내용")),
                        listOf(ManualCell("H+5분", rowspan = 3), ManualCell("급보", colspan = 2))
                    )
                )
            )
        )
        val html = ManualFormat.html(s, dark = false)
        assertTrue(html.contains("<tr class=\"hd\">"))
        assertEquals(1, Regex("class=\"hd\"").findAll(html).count())
        assertTrue(html.contains("rowspan=\"3\""))
        assertTrue(html.contains("colspan=\"2\""))
        // 병합이 1이면 속성을 붙이지 않는다 (쓸데없이 길어진다)
        assertFalse(html.contains("colspan=\"1\""))
    }

    @Test
    fun `모르는 block type 은 문단처럼 그린다`() {
        val s = section(blocks = listOf(ManualBlock(type = "callout", text = "새 종류")))
        val html = ManualFormat.html(s, dark = false)
        assertTrue(html.contains("<p>새 종류</p>"))
    }

    @Test
    fun `소제목은 단계별 태그로 나간다`() {
        val s = section(
            blocks = listOf(
                ManualBlock(type = "h", level = 1, text = "가"),
                ManualBlock(type = "h", level = 3, text = "나"),
                ManualBlock(type = "h", level = 9, text = "다")   // 범위를 벗어나도 안전하게
            )
        )
        val html = ManualFormat.html(s, dark = false)
        assertTrue(html.contains("<h1>가</h1>"))
        assertTrue(html.contains("<h3>나</h3>"))
        assertTrue(html.contains("<h3>다</h3>"))
    }

    @Test
    fun `평문화는 표 칸을 구분자로 잇고 소제목도 넣는다`() {
        val s = section(
            blocks = listOf(
                ManualBlock(type = "h", level = 1, text = "초동조치"),
                ManualBlock(type = "p", text = "사고흐름도"),
                ManualBlock(
                    type = "table",
                    rows = listOf(listOf(ManualCell("기관사"), ManualCell("열차무선방호\n실시")))
                ),
                ManualBlock(type = "image", file = "img_002.jpg", caption = "흐름도")
            )
        )
        val plain = ManualFormat.plainText(s)
        assertTrue(plain.contains("초동조치"))
        assertTrue(plain.contains("기관사 | 열차무선방호 실시"))
        assertTrue(plain.contains("흐름도"))
    }

    @Test
    fun `상황 표지는 번호가 붙은 표준운영절차에서만 나온다`() {
        assertEquals("상황 3", ManualFormat.situationTag(section(title = "3. 역구내 열차 탈선사고")))
        assertEquals("상황 9-1", ManualFormat.situationTag(section(title = "9-1. 열차 내 혼잡")))
        assertEquals("", ManualFormat.situationTag(section(title = "비상상황 유형 선정")))
        assertEquals(
            "",
            ManualFormat.situationTag(section(path = listOf("부 록"), title = "1.1 사고분류"))
        )
    }

    @Test
    fun `인용문 대조는 공백 차이를 무시한다`() {
        val body = "① 열차무선방호 실시 및\n   운전관제 급보"
        assertTrue(ManualFormat.quoteMatches("열차무선방호 실시 및 운전관제 급보", body))
        assertTrue(ManualFormat.quoteMatches("  열차무선방호   실시  ", body))
        assertFalse(ManualFormat.quoteMatches("열차 무선 방호", body))   // 낱말이 다르면 불일치
        assertFalse(ManualFormat.quoteMatches("   ", body))
    }

    // 보이지 않는 글자는 소스에 그대로 두지 않고 코드포인트로 만든다
    private fun cp(n: Int): String = String(Character.toChars(n))

    /**
     * 서버(functions/askGuide.core.js 의 normalizeSpace = JS 정규식 공백 클래스)와 **같은 표**.
     * 같은 사례가 functions/selfcheck.js 8-1) 에 있다 — 한쪽만 고치면 서버는 "원문 일치"인데
     * 앱은 "AI 정리"로 뜬다. 실제 2026.9판 매뉴얼에 전각 공백(U+3000)이 들어 있다.
     */
    @Test
    fun `공백 글자 집합은 서버와 같다`() {
        val same = listOf(0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x20, 0xa0, 0x1680, 0x2000, 0x200a,
            0x2028, 0x2029, 0x202f, 0x205f, 0x3000, 0xfeff)
        for (n in same) {
            val ws = cp(n)
            assertEquals("U+" + Integer.toHexString(n), "가 나", ManualFormat.normalizeWs("가$ws${ws}나$ws"))
        }
        // 서버가 공백으로 보지 않는 것은 앱도 건드리지 않는다 (코틀린 trim 은 이것들을 깎는다)
        for (n in listOf(0x85, 0x1c, 0x1f, 0x200b, 0x180e)) {
            val x = cp(n)
            assertEquals("U+" + Integer.toHexString(n), "가${x}나$x", ManualFormat.normalizeWs("가${x}나$x"))
        }
        // 전각 공백·NBSP 로 띄운 원문을 보통 공백으로 인용해도 일치다
        val body = "열차무선방호" + cp(0x3000) + "실시 및" + cp(0xa0) + "운전관제 급보"
        assertTrue(ManualFormat.quoteMatches("열차무선방호 실시 및 운전관제 급보", body))
    }

    @Test
    fun `본문 자르기는 서버와 같은 길이이고 반쪽 서러게이트를 남기지 않는다`() {
        assertEquals(6000, ManualFormat.cutBody("가".repeat(7000), 6000).length)
        assertEquals("짧다", ManualFormat.cutBody("짧다", 2000))
        // 한컴 글머리표(U+F03DA)는 UTF-16 두 칸이다. 자른 끝이 그 앞 절반이면 떼어 낸다
        val bullet = cp(0xF03DA)
        val cut = ManualFormat.cutBody("가".repeat(1999) + bullet + "나", 2000)
        assertEquals(1999, cut.length)
        assertFalse(cut.last().isHighSurrogate())
        // 쌍이 온전히 들어가면 그대로 둔다
        assertEquals(2000, ManualFormat.cutBody("가".repeat(1998) + bullet + "나", 2000).length)
    }

    @Test
    fun `그림 파일 이름은 화이트리스트만 통과한다`() {
        for (ok in listOf("img_002.jpg", "A-1.png", "fig.v2.webp")) {
            assertTrue(ok, ManualFormat.isSafeImageName(ok))
        }
        for (bad in listOf("", "../x.png", "a/b.png", "a\\b.png", "..", ".hidden", "a..b.png",
            "x.png?y=1", "그림.png", "a b.png", "%2e%2e.png", "a".repeat(101))) {
            assertFalse(bad, ManualFormat.isSafeImageName(bad))
        }
        val s = section(
            blocks = listOf(
                ManualBlock(type = "image", file = "../../shared_prefs/secret.xml", caption = "나쁜 그림"),
                ManualBlock(type = "image", file = "img_001.jpg", caption = "<b>캡션</b>")
            )
        )
        // 내려받기 목록(imageFiles)과 HTML 양쪽에서 빠진다
        assertEquals(listOf("img_001.jpg"), s.imageFiles)
        val html = ManualFormat.html(s, dark = false)
        assertFalse(html.contains("shared_prefs"))
        assertEquals(1, Regex("<img ").findAll(html).count())
        assertTrue(html.contains("&lt;b&gt;캡션&lt;/b&gt;"))   // 캡션도 이스케이프
    }

    @Test
    fun `제목과 경로와 표 셀도 이스케이프된다`() {
        val s = section(
            path = listOf("<i>경로</i>"), title = "<u>제목</u> & 끝",
            blocks = listOf(
                ManualBlock(type = "h", level = 2, text = "<h9>소제목</h9>"),
                ManualBlock(type = "table", rows = listOf(listOf(ManualCell("<td>셀</td>\"'"))))
            )
        )
        val html = ManualFormat.html(s, dark = true, edition = "<2026>")
        for (raw in listOf("<i>", "<u>", "<h9>", "<td>셀", "<2026>")) assertFalse(raw, html.contains(raw))
        assertTrue(html.contains("&lt;u&gt;제목&lt;/u&gt; &amp; 끝"))
        assertTrue(html.contains("&quot;&#39;"))
    }

    @Test
    fun `단독 사고흐름도 문단은 1단계 제목처럼 그린다`() {
        val s = section(
            blocks = listOf(
                ManualBlock(type = "p", text = " 사고흐름도 "),
                ManualBlock(type = "p", text = "사고흐름도(예시)"),          // 정확히 그 글자가 아니면 문단
                ManualBlock(type = "table", rows = listOf(listOf(ManualCell("사고흐름도"))))
            )
        )
        val html = ManualFormat.html(s, dark = false)
        assertTrue(html.contains("<h1>사고흐름도</h1>"))
        assertTrue(html.contains("<p>사고흐름도(예시)</p>"))
        assertEquals(1, Regex("<h1>").findAll(html).count())
        // 자료는 그대로다 — 평문(검색·AI 재료)에는 보통 줄로 남는다
        assertTrue(ManualFormat.plainText(s).contains("사고흐름도"))
    }

    @Test
    fun `제목 색은 밝은 화면과 어두운 화면이 따로다`() {
        val s = section(blocks = listOf(ManualBlock(type = "h", level = 1, text = "가")))
        val light = ManualFormat.html(s, dark = false)
        val dark = ManualFormat.html(s, dark = true)
        // 섹션 제목 / L1 / L2 / L3 왼쪽 막대
        for (c in listOf("#F3F0FF", "#EEF5FF", "#EFFAF5", "#FFD9B8")) {
            assertTrue(c, light.contains(c)); assertFalse(c, dark.contains(c))
        }
        for (c in listOf("#262238", "#1B2535", "#17302A", "#5A4030")) {
            assertTrue(c, dark.contains(c)); assertFalse(c, light.contains(c))
        }
        assertTrue(light.contains("h3{border-left:3px solid #FFD9B8"))
    }

    @Test
    fun `괄호와 공백이 연달아 오는 전화번호도 센다`() {
        val phones = (1..5).joinToString("\n") { "운용 (02) 6110-${6840 + it}" }
        assertTrue(
            ManualFormat.isSensitive(
                section(path = listOf("부 록"), title = "3. 발생내역",
                    blocks = listOf(ManualBlock(type = "p", text = phones)))
            )
        )
    }

    @Test
    fun `형광 구간은 겹치지 않고 못 찾으면 버린다`() {
        val quote = "관제에 급보하고 관제 지시를 따른다"
        val spans = ManualFormat.highlightSpans(
            quote,
            listOf("관제" to "contact", "급보" to "action", "없는말" to "caution")
        )
        assertEquals(2, spans.size)
        // 시작 위치 순으로 정렬되고, 서로 겹치지 않는다
        assertEquals(listOf(0, 4), spans.map { it.start })
        assertEquals(listOf("contact", "action"), spans.map { it.kind })
        for (i in 1 until spans.size) assertTrue(spans[i].start >= spans[i - 1].end)
        // 같은 낱말이 두 번 나오면 두 번째 형광은 다음 자리로 밀린다(겹쳐 칠하지 않는다)
        val twice = ManualFormat.highlightSpans(quote, listOf("관제" to "a", "관제" to "b"))
        assertEquals(listOf(0, 9), twice.map { it.start })
    }

    @Test
    fun `실명 연락처 섹션은 AI 후보에서 빠진다`() {
        assertTrue(ManualFormat.isSensitive(section(path = listOf(ManualFormat.GROUP_COVER))))
        assertTrue(
            ManualFormat.isSensitive(
                section(path = listOf("부 록"), title = "6.1 주요 유관기관 비상연락망")
            )
        )
        val phones = (1..5).joinToString("\n") { "담당자 02-6311-${1000 + it}" }
        assertTrue(
            ManualFormat.isSensitive(
                section(path = listOf("부 록"), title = "7.1 장비현황",
                    blocks = listOf(ManualBlock(type = "p", text = phones)))
            )
        )
        // 전화번호가 몇 개뿐인 보통 상황 섹션은 그대로 쓴다
        assertFalse(
            ManualFormat.isSensitive(
                section(blocks = listOf(ManualBlock(type = "p", text = "관제 02-6311-1234 로 급보")))
            )
        )
    }
}
