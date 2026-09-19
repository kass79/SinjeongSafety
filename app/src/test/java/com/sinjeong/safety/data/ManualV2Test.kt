package com.sinjeong.safety.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * manual.json **스키마 v2** — 합성 자료 점검.
 *
 * v2 에서 달라진 것:
 *  - 표 셀에 선택 필드 `blocks`(표 안의 표, 셀 안 그림). 있으면 화면은 그것을 그리고, 셀 `text` 는 그 셀의 전체 평문.
 *  - 그림에 선택 필드 `alt`·`flow`. 도형 흐름도를 그림으로 떠 온 것이고 `alt` 는 도형 안 문구. **화면에는 그림만.**
 *  - 평문이 둘로 갈린다: (가) 검색·원문 대조용 [ManualFormat.plainText] / (나) AI 본문 [ManualFormat.aiBody](흐름도 글을 표식으로 감쌈).
 *
 * 여기서 잡으려는 사고:
 *  - 셀 안 그림을 내려받기 목록에서 빠뜨려 화면에 깨진 그림이 나오는 것
 *  - 흐름도 도형 글이 화면에 본문처럼 줄줄이 나오는 것(순서가 절차처럼 읽힌다)
 *  - AI 본문에 표식이 빠져 나열 순서가 절차 순서로 읽히는 것 / 반대로 검색·대조용 평문에 표식이 섞이는 것
 *  - 셀 글이 text 와 blocks 양쪽에서 두 번 나오는 것
 */
class ManualV2Test {

    private fun cp(n: Int): String = String(Character.toChars(n))

    // 한컴 전용문자 화살표(U+F0EF)를 JSON 에 넣으려면 글자 그대로 넣는 수밖에 없다 → 코드포인트로 만들어 끼운다
    private val v2Json = """
    {"title":"매뉴얼","edition":"2026.9","schema":2,"sections":[
      {"id":"s029","path":["비상대응 표준운영절차"],"title":"23-1. 열차 내 화재 <터널>","futureField":{"x":1},
       "blocks":[
        {"type":"h","level":1,"text":"초동조치 시나리오"},
        {"type":"table","rows":[
          [{"text":"구분","colspan":1,"rowspan":1},{"text":"조치내용","colspan":1,"rowspan":1}],
          [{"text":"기관사","colspan":1,"rowspan":1},
           {"text":"열차무선방호 실시\n세부 | 관제 급보\n임무카드 <앞면>","colspan":1,"rowspan":1,
            "blocks":[
              {"type":"p","text":"열차무선방호 실시"},
              {"type":"table","rows":[[{"text":"세부"},{"text":"관제 급보 & \"보고\""}]]},
              {"type":"image","file":"img_055.jpg","caption":"임무카드 <앞면>","w":10,"h":20},
              {"type":"image","file":"../../shared_prefs/x.xml","caption":"나쁜 그림"},
              {"type":"callout","text":"모르는 종류"}
            ]}],
          [{"text":"보고","colspan":2,"rowspan":1,
            "blocks":[{"type":"p","text":"관제 __ARROW__ 승무원"}]}]
        ]},
        {"type":"h","level":1,"text":"사고흐름도"},
        {"type":"image","file":"flow_s029_1.png","caption":"","flow":true,
         "alt":"상황 발생\n관제보고 및 안내방송\nYES\nNO\n<script>alert(1)</script> \"따옴표\""},
        {"type":"table","rows":[
          [{"text":"흐름도","blocks":[
            {"type":"image","file":"flow_s029_2.png","flow":true,"alt":"승객 대피 유도\n소화기 사용"}]}]
        ]},
        {"type":"p","text":"끝 문단"}
       ]}
    ]}
    """.trimIndent().replace("__ARROW__", cp(0xF0EF))

    private val doc by lazy { ManualJson.parseDoc(v2Json) }
    private val sec get() = doc.sections[0]

    private fun count(s: String, needle: String) = s.split(needle).size - 1

    @Test
    fun `v2 자료를 읽는다 - 셀 blocks 와 그림 alt flow 그리고 모르는 필드`() {
        assertEquals(1, doc.sections.size)
        val table = sec.blocks[1]
        val cell = table.rows[1][1]
        assertEquals(listOf("p", "table", "image", "image", "callout"), cell.blocks.map { it.type })
        assertEquals("관제 급보 & \"보고\"", cell.blocks[1].rows[0][1].text)      // 표 안의 표
        assertTrue(table.rows[0][0].blocks.isEmpty())                              // blocks 없는 셀은 예전 그대로
        val flow = sec.blocks[3]
        assertTrue(flow.flow)
        assertTrue(flow.alt.startsWith("상황 발생\n관제보고"))
        assertFalse(cell.blocks[2].flow)                                           // 보통 그림
        assertEquals("", cell.blocks[2].alt)
        // v1 자료(선택 필드가 아예 없음)도 그대로 읽힌다
        val v1 = ManualJson.parseDoc(
            """{"title":"t","edition":"e","sections":[{"id":"s1","path":[],"title":"x","blocks":[
               {"type":"table","rows":[[{"text":"a","colspan":1,"rowspan":1}]]},
               {"type":"image","file":"img_001.jpg","caption":"c"}]}]}"""
        )
        assertTrue(v1.sections[0].blocks[0].rows[0][0].blocks.isEmpty())
        assertEquals("", v1.sections[0].blocks[1].alt)
        assertFalse(v1.sections[0].blocks[1].flow)
    }

    @Test
    fun `그림 목록은 셀 안까지 모으고 이름 화이트리스트를 지킨다`() {
        // 나온 순서대로, 흐름도 그림(flow_*.png) 포함, 경로 조작 이름은 빠진다 — 이 목록이 곧 내려받기 목록이다
        assertEquals(listOf("img_055.jpg", "flow_s029_1.png", "flow_s029_2.png"), sec.imageFiles)
        // 같은 그림을 여러 셀에서 써도 한 번만
        val twice = ManualSection("s", emptyList(), "t", listOf(
            ManualBlock(type = "table", rows = listOf(listOf(
                ManualCell("a", blocks = listOf(ManualBlock(type = "image", file = "img_055.jpg"))),
                ManualCell("b", blocks = listOf(ManualBlock(type = "image", file = "img_055.jpg")))
            )))
        ))
        assertEquals(listOf("img_055.jpg"), twice.imageFiles)
    }

    @Test
    fun `화면은 셀 blocks 를 다시 그리고 흐름도 글은 글로 내지 않는다`() {
        for (dark in listOf(false, true)) {
            val html = ManualFormat.html(sec, dark, doc.edition)
            val body = html.substringAfter("<body>").substringBeforeLast("</body>")

            // 태그 짝·이스케이프: XML 파서가 끝까지 읽어야 한다
            val xml = "<root>" + body.replace(Regex("<img ([^>]*)>"), "<img $1/>") + "</root>"
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))

            // 표 안의 표: 바깥 표 2개(가로 스크롤 상자 포함) + 셀 안 표 1개. 머리 행은 바깥 표의 첫 행만
            assertEquals(2, count(body, "<div class=\"tw\"><table>"))
            assertEquals(1, count(body, "<table class=\"in\">"))
            assertEquals(2, count(body, "<tr class=\"hd\">"))
            assertTrue(body.contains("<td>관제 급보 &amp; &quot;보고&quot;</td>"))
            // blocks 가 있는 셀은 text 를 또 그리지 않는다(같은 글이 두 번 나오면 안 된다)
            assertEquals(1, count(body, "열차무선방호 실시"))
            assertFalse(body.contains("세부 | 관제 급보"))
            // 셀 안 그림·흐름도 그림 셋. 경로 조작 이름은 안 나온다
            assertEquals(3, count(body, "<img "))
            assertTrue(body.contains("src=\"images/flow_s029_1.png\""))
            assertTrue(body.contains("src=\"images/flow_s029_2.png\""))
            assertFalse(body.contains("shared_prefs"))
            assertTrue(body.contains("임무카드 &lt;앞면&gt;"))                     // 셀 안 그림의 캡션도 이스케이프
            // 모르는 종류는 셀 안에서도 문단처럼
            assertTrue(body.contains("<p>모르는 종류</p>"))
            // 전용문자 화살표는 셀 안에서도 원본 글리프로
            assertTrue(body.contains("<p>관제 " + cp(0x21E6) + " 승무원</p>"))
            assertFalse(body.contains(cp(0xF0EF)))

            // ★ 흐름도 도형 글은 **alt 속성에만** 있고 화면 글로는 안 나온다
            val visible = body.replace(Regex("<img [^>]*>"), "").replace(Regex("<[^>]+>"), " ")
            assertFalse(visible.contains("관제보고 및 안내방송"))
            assertFalse(visible.contains("소화기 사용"))
            assertTrue(body.contains("alt=\"상황 발생"))
            // alt 안의 위험한 글자도 이스케이프된다
            assertFalse(html.contains("<script>"))
            assertTrue(body.contains("&lt;script&gt;alert(1)&lt;/script&gt; &quot;따옴표&quot;\">"))
            // 표식은 화면에 절대 안 나온다 (AI 본문 전용)
            assertFalse(html.contains("흐름도 글"))
        }
    }

    @Test
    fun `평문은 둘이다 - 검색 대조용에는 표식이 없고 AI 본문에는 있다`() {
        val plain = ManualFormat.plainText(sec)
        val ai = ManualFormat.aiBody(sec)

        // (가) 검색·원문 대조용: p/h 글 + 셀 text + 그림 alt. 표식 없음
        assertFalse(plain.contains(ManualFormat.FLOW_OPEN))
        assertFalse(plain.contains(ManualFormat.FLOW_CLOSE))
        assertTrue(plain.contains("기관사 | 열차무선방호 실시 세부 | 관제 급보 임무카드 <앞면>"))   // 셀 text 그대로(줄바꿈만 공백)
        assertTrue(plain.contains("관제보고 및 안내방송"))                                         // 섹션 바로 아래 흐름도의 alt
        assertTrue(plain.contains("승객 대피 유도\n소화기 사용"))                                  // 셀 안 흐름도의 alt
        // 셀의 blocks 글을 또 모으지 않는다 — text 가 이미 전체 평문이다
        assertEquals(1, count(plain, "열차무선방호 실시"))
        assertEquals(1, count(plain, "관제 급보"))
        // 오늘의 비상조치 keyStep 대조는 (가)로 한다 → 흐름도 안 문구를 인용한 keyStep 도 '원문 일치'
        assertTrue(ManualToday.stepMatches("관제보고 및 안내방송", plain))

        // (나) AI 본문 = (가) + alt 구간을 두 줄로 감싼 것
        assertEquals(2, count(ai, ManualFormat.FLOW_OPEN))
        assertEquals(2, count(ai, ManualFormat.FLOW_CLOSE))
        val open = ai.indexOf(ManualFormat.FLOW_OPEN)
        val close = ai.indexOf(ManualFormat.FLOW_CLOSE)
        val inside = ai.substring(open, close)
        assertTrue(inside.contains("상황 발생\n관제보고 및 안내방송\nYES\nNO"))
        assertFalse(inside.contains("기관사 |"))                                                   // 표 글은 표식 밖
        assertTrue(ai.indexOf("기관사 | 열차무선방호") < open)
        assertTrue(ai.trimEnd().endsWith("끝 문단"))
        // 표식 줄을 걷어 내면 (가)와 글자까지 같다 — 둘은 같은 재료로 만든다
        val stripped = ai.lines()
            .filter { it != ManualFormat.FLOW_OPEN && it != ManualFormat.FLOW_CLOSE }.joinToString("\n")
        assertEquals(plain, stripped)
        // 인용문 대조는 "보낸 본문" 기준: 흐름도 안 문구도, 표 글도 (나)에서 그대로 찾힌다
        assertTrue(ManualFormat.quoteMatches("관제보고 및 안내방송", ai))
        assertTrue(ManualFormat.quoteMatches("기관사 | 열차무선방호 실시", ai))
        // 표식 줄을 건너뛰어 앞뒤를 이어 붙인 인용은 원문이 아니다
        assertFalse(ManualFormat.quoteMatches("사고흐름도 상황 발생", ai))
        assertTrue(ManualFormat.quoteMatches("사고흐름도 상황 발생", plain))
    }

    @Test
    fun `셀 text 가 이미 흐름도 글을 담고 있으면 또 넣지 않는다`() {
        val s = ManualSection("s", emptyList(), "t", listOf(
            ManualBlock(type = "table", rows = listOf(listOf(
                ManualCell(
                    text = "상황 발생\n승객 대피   유도",
                    blocks = listOf(ManualBlock(type = "image", file = "flow_1.png", flow = true,
                        alt = "상황 발생 승객 대피 유도"))
                )
            )))
        ))
        assertEquals(1, count(ManualFormat.plainText(s), "상황 발생"))
        // alt 가 없는 그림은 어느 쪽에도 표식을 만들지 않는다
        val noAlt = ManualSection("s", emptyList(), "t",
            listOf(ManualBlock(type = "image", file = "img_001.jpg", caption = "그림 설명")))
        assertEquals("그림 설명", ManualFormat.aiBody(noAlt))
        assertEquals(ManualFormat.plainText(noAlt), ManualFormat.aiBody(noAlt))
    }

    @Test
    fun `옛 자리 표시는 화면에서만 걸러 내고 빈 blocks 는 글로 되돌아간다`() {
        val s = ManualSection("s067", emptyList(), "8. 개인별 임무카드", listOf(
            ManualBlock(type = "table", rows = listOf(listOf(
                ManualCell("[그림: img_055.jpg]개인별 임무카드(기관사)"),
                ManualCell("[표]  앞면"),
                // blocks 가 있는데 그릴 게 하나도 안 나오면(빈 문단·막힌 그림) text 를 그린다 — 빈 칸보다 낫다
                ManualCell("되돌아간 글", blocks = listOf(
                    ManualBlock(type = "p", text = "   "),
                    ManualBlock(type = "image", file = "../x.png")
                ))
            )))
        ))
        val html = ManualFormat.html(s, dark = false)
        assertTrue(html.contains("<td>개인별 임무카드(기관사)</td>"))
        assertFalse(html.contains("[그림:"))
        assertFalse(html.contains("[표]"))
        assertTrue(html.contains("<td>되돌아간 글</td>"))
        // 자료·검색·인용 대조용 평문은 건드리지 않는다(서버에 보낸 글과 같아야 대조가 맞는다)
        assertTrue(ManualFormat.plainText(s).contains("[그림: img_055.jpg]개인별 임무카드(기관사)"))
        // 대괄호가 들어간 보통 글은 그대로 둔다
        assertEquals("[주의] 출입문 [1-1]", ManualFormat.clean("[주의] 출입문 [1-1]"))
    }

    @Test
    fun `끝없이 파고드는 자료에도 멈춘다`() {
        // 표 안의 표를 한도보다 깊게 쌓는다
        var inner = ManualBlock(type = "table", rows = listOf(listOf(ManualCell("바닥"))))
        repeat(ManualFormat.MAX_DEPTH + 4) {
            inner = ManualBlock(type = "table", rows = listOf(listOf(ManualCell("층", blocks = listOf(inner)))))
        }
        val s = ManualSection("s", emptyList(), "t", listOf(inner))
        val html = ManualFormat.html(s, dark = false)
        // 한도까지만 표로 그리고 그 아래는 셀 text 로 그린다 — 터지지 않고, 태그 짝도 맞는다
        assertEquals(count(html, "<table"), count(html, "</table>"))
        assertTrue(count(html, "<table") <= ManualFormat.MAX_DEPTH + 1)
        assertTrue(html.contains("<td>층</td>"))
        assertTrue(ManualFormat.imagesIn(s.blocks).isEmpty())
    }

    // ── 실데이터(v2)에서 확정된 것들 ─────────────────────────────

    private fun flowImg(file: String, alt: String) =
        ManualBlock(type = "image", file = file, flow = true, alt = alt)

    /**
     * SCHEMA.md §1: 셀 `text` 는 **안쪽 흐름도의 alt 까지** 담는다. 상황 23-1·23-2 는 흐름도가 1×1 표의 셀 안에
     * 통째로 들어 있어 셀 text == alt 다. 이 글이 AI 본문의 표 줄에 표식 없이 섞이면 모델이 나열 순서를 절차로 읽는다.
     */
    @Test
    fun `흐름도가 통째로 든 셀의 글은 AI 본문에서 반드시 표식 안으로 간다`() {
        val alt = "상황 발생\n관제보고 및 안내방송\nYES\nNO\n승객 대피 유도"
        val s = ManualSection("s029", listOf(ManualFormat.GROUP_SOP), "23-1. 역구내 폭음", listOf(
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell("기관사"), ManualCell("열차무선방호 실시")))),
            ManualBlock(type = "h", level = 1, text = "사고흐름도"),
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell(alt, blocks = listOf(flowImg("flow_s029_1.png", alt)))))),
            ManualBlock(type = "p", text = "끝 문단")
        ))
        val plain = ManualFormat.plainText(s)
        val ai = ManualFormat.aiBody(s)

        // (가) 검색·원문 대조용: 한 번만, 표식 없이
        assertEquals(1, count(plain, "관제보고 및 안내방송"))
        assertFalse(plain.contains(ManualFormat.FLOW_OPEN))
        assertTrue(ManualToday.stepMatches("관제보고 및 안내방송", plain))
        assertTrue(ManualToday.stepMatches("상황 발생 관제보고 및 안내방송", plain))   // 줄 차이는 공백 차이일 뿐

        // (나) AI 본문: 역시 한 번만, 그리고 그 한 번이 **표식 안**이다
        assertEquals(1, count(ai, "관제보고 및 안내방송"))
        assertEquals(1, count(ai, ManualFormat.FLOW_OPEN))
        assertEquals(1, count(ai, ManualFormat.FLOW_CLOSE))
        val open = ai.indexOf(ManualFormat.FLOW_OPEN)
        val close = ai.indexOf(ManualFormat.FLOW_CLOSE)
        val at = ai.indexOf("관제보고 및 안내방송")
        assertTrue("흐름도 글이 표식 밖에 있다", at in (open + 1) until close)
        // 상자 하나가 한 줄 — 공백으로 뭉개지 않는다(모델이 상자 경계를 본다)
        assertTrue(ai.substring(open, close).contains("상황 발생\n관제보고 및 안내방송\nYES\nNO\n승객 대피 유도"))
        // 표식 밖의 글은 (가)와 같다: 표 줄은 표식 앞에, 끝 문단은 표식 뒤에. 빈 표 줄("|")은 남기지 않는다
        assertTrue(ai.indexOf("기관사 | 열차무선방호 실시") in 0 until open)
        assertTrue(ai.indexOf("끝 문단") > close)
        assertFalse(ai.lines().any { it.trim() == "|" || it.isBlank() })
        // 표식만 걷어 내면 두 평문은 공백 차이를 빼고 같다(같은 재료다)
        val stripped = ai.lines().filter { it != ManualFormat.FLOW_OPEN && it != ManualFormat.FLOW_CLOSE }.joinToString("\n")
        assertEquals(ManualFormat.normalizeWs(plain), ManualFormat.normalizeWs(stripped))
        // 인용문 대조는 "보낸 본문" 기준
        assertTrue(ManualFormat.quoteMatches("관제보고 및 안내방송", ai))
    }

    @Test
    fun `셀에 제 글과 흐름도가 함께 있으면 흐름도 글만 표식으로 옮긴다`() {
        val alt = "출입문 차단\n관계 차단기 Off"
        // 글자 그대로 들어 있는 경우: 그 부분만 덜어 낸다
        val exact = ManualSection("s", emptyList(), "t", listOf(ManualBlock(type = "table", rows = listOf(listOf(
            ManualCell("조치"),
            ManualCell("먼저 관제에 보고\n$alt", blocks = listOf(ManualBlock(type = "p", text = "먼저 관제에 보고"), flowImg("flow_1.png", alt)))
        )))))
        val ai = ManualFormat.aiBody(exact)
        assertTrue(ai.contains("조치 | 먼저 관제에 보고\n" + ManualFormat.FLOW_OPEN + "\n" + alt + "\n" + ManualFormat.FLOW_CLOSE))
        assertEquals(1, count(ai, "출입문 차단"))
        assertEquals(1, count(ManualFormat.plainText(exact), "출입문 차단"))

        // 공백만 달라 글자 그대로는 못 덜어 내는 경우: 그 셀 글 전체를 흐름도 글로 본다(표식 밖에 남기지 않는다)
        val fuzzy = ManualSection("s", emptyList(), "t", listOf(ManualBlock(type = "table", rows = listOf(listOf(
            ManualCell("출입문   차단 관계 차단기 Off", blocks = listOf(flowImg("flow_1.png", alt)))
        )))))
        val ai2 = ManualFormat.aiBody(fuzzy)
        assertTrue(ai2.startsWith(ManualFormat.FLOW_OPEN))
        assertTrue(ai2.trimEnd().endsWith(ManualFormat.FLOW_CLOSE))
        assertEquals(1, count(ai2, "차단기 Off"))
    }

    @Test
    fun `그림 한 장을 감싼 1x1 표는 테두리 없이 그림만 그린다`() {
        val alt = "상황 발생\nYES"
        val s = ManualSection("s003", emptyList(), "t", listOf(
            // 상황별 "사고흐름도" 그림 꼴: 글 없는 1×1 표
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell("", blocks = listOf(ManualBlock(type = "image", file = "img_002.jpg")))))),
            // 흐름도 셀: text == alt (alt 는 어차피 화면에 글로 내지 않는다)
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell(alt, blocks = listOf(flowImg("flow_s029_1.png", alt)))))),
            // 그림 말고 **제 글이 따로 있으면** 표로 둔다 — 글을 잃으면 안 된다
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell("그림 설명 글", blocks = listOf(
                ManualBlock(type = "p", text = "그림 설명 글"), ManualBlock(type = "image", file = "img_003.jpg")))))),
            // 막힌 파일 이름이면 그림으로 풀지 않는다(표로 그리고, 셀은 text 로 되돌아간다)
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell("", blocks = listOf(ManualBlock(type = "image", file = "../x.png"))))))
        ))
        val html = ManualFormat.html(s, dark = false)
        assertEquals(2, count(html, "<table"))
        assertEquals(3, count(html, "<img "))
        assertTrue(html.contains("</div><figure><img src=\"images/img_002.jpg\""))       // 제목 띠 바로 뒤에 그림이 온다(td 안이 아니다)
        assertTrue(html.contains("<p>그림 설명 글</p><figure><img src=\"images/img_003.jpg\""))
        assertFalse(html.contains("x.png"))
        assertEquals(count(html, "<table"), count(html, "</table>"))
    }

    @Test
    fun `글이 0자여도 그림이 있으면 내용이 있는 섹션이다`() {
        val imageOnly = ManualSection("s090", listOf("부 록", "6. 협력"), "6-6 비상대응지도",
            listOf(ManualBlock(type = "image", file = "img_054.jpg")))
        val imageInTable = ManualSection("s076", listOf("부 록", "4. 대피"), "4.2 수습·복구체계",
            listOf(ManualBlock(type = "table", rows = listOf(listOf(ManualCell("", blocks = listOf(ManualBlock(type = "image", file = "img_040.png"))))))))
        val titleOnly = ManualSection("s099", listOf("부 록"), "제목뿐", emptyList())
        val blankOnly = ManualSection("s098", listOf("부 록"), "빈 칸뿐", listOf(
            ManualBlock(type = "p", text = "  \n "),
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell(" "), ManualCell("")))),
            ManualBlock(type = "image", file = "../막힌이름.png")
        ))
        val text = ManualSection("s003", listOf(ManualFormat.GROUP_SOP), "1. 충돌", listOf(ManualBlock(type = "p", text = "본문")))

        assertEquals("", ManualFormat.plainText(imageOnly))
        assertTrue(ManualFormat.hasContent(imageOnly))        // 숨기면 안 된다(SCHEMA.md §5) — 눌러도 빈 화면이 아니다
        assertTrue(ManualFormat.hasContent(imageInTable))
        assertTrue(ManualFormat.hasContent(text))
        assertFalse(ManualFormat.hasContent(titleOnly))       // 이런 것만 목록·검색에서 뺀다
        assertFalse(ManualFormat.hasContent(blankOnly))

        // 목록·검색·바로가기에 올리는 섹션: 그릴 것이 있는 것만, **배열 순서 그대로**(id 순서가 아니다)
        val doc = ManualDoc("m", "2026.9", listOf(imageOnly, titleOnly, text, blankOnly, imageInTable))
        assertEquals(listOf("s090", "s003", "s076"), doc.visibleSections.map { it.id })
        assertEquals(5, doc.sections.size)                    // 자료 자체는 그대로다(바로가기는 id 로 찾아 안내문을 띄운다)
    }

    // ── AI 제외(실명·전화) ───────────────────────────────────────
    private fun mk(id: String, path: List<String>, title: String, vararg blocks: ManualBlock) =
        ManualSection(id, path, title, blocks.toList())
    private fun p(text: String) = ManualBlock(type = "p", text = text)
    private fun table(vararg rows: List<String>) =
        ManualBlock(type = "table", rows = rows.map { r -> r.map { ManualCell(it) } })

    @Test
    fun `사람을 적는 표와 개인 연락처가 있는 섹션은 AI 에서 빠진다`() {
        val sop = listOf(ManualFormat.GROUP_SOP)
        // 머리글이 성명·이름·성함·담당자 (칸 글의 공백을 빼고 통째로 같을 때)
        assertTrue(ManualFormat.isSensitive(mk("a", sop, "1. 충돌", table(listOf("직   책", "성   명"), listOf("대책반장", "가나다")))))
        assertTrue(ManualFormat.isSensitive(mk("a", sop, "1. 충돌", table(listOf("직책", "담 당 자"), listOf("대책반장", "승무계획처장")))))
        // 표 안의 표에 있어도 (v2: 셀 text 가 안쪽 표 글을 줄 단위로 담는다 / blocks 로도 온다)
        assertTrue(ManualFormat.isSensitive(mk("a", sop, "1. 충돌",
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell("임무카드\n성 명\n가나다")))))))
        assertTrue(ManualFormat.isSensitive(mk("a", sop, "1. 충돌",
            ManualBlock(type = "table", rows = listOf(listOf(ManualCell("", blocks = listOf(table(listOf("이름"), listOf("가나다"))))))))))
        // 문장 속의 낱말은 머리글이 아니다
        assertFalse(ManualFormat.isSensitive(mk("a", sop, "1. 충돌", p("담당자는 관제에 보고한다"), table(listOf("구분", "담당업무"), listOf("기관사", "성명 확인 후 인계")))))

        // 휴대전화는 하나만 있어도 개인 연락처다. 기관 대표번호 몇 개는 괜찮다
        assertTrue(ManualFormat.isSensitive(mk("a", sop, "1. 충돌", p("당직 010-1234-5678"))))
        assertTrue(ManualFormat.isSensitive(mk("a", sop, "1. 충돌", p("당직 01012345678"))))
        assertFalse(ManualFormat.isSensitive(mk("a", sop, "1. 충돌", p("운용 (02) 1234-5678 / FAX (02) 1234-5679"))))

        // 지역번호 없이 번호만 적은 연락망(경찰서 연락망이 이 꼴이다)도 센다. 날짜·코드는 세지 않는다
        val local = (1..5).joinToString("\n") { "○○경찰서 | 1234-${1000 + it}" }
        assertEquals(5, ManualFormat.phoneCount(local))
        assertTrue(ManualFormat.isSensitive(mk("a", listOf("부 록", "7. 현황"), "7.9 관할", p(local))))
        assertEquals(0, ManualFormat.phoneCount("2026-09-19 개정, 문서 2026-0919-01, 코드 R111/S211, 1588-1234-5"))
        assertEquals(2, ManualFormat.phoneCount("(02) 1234-5678, 02-1234-5679"))          // 겹쳐 세지 않는다
        // 제목·경로의 낱말
        assertTrue(ManualFormat.isSensitive(mk("a", listOf("부 록", "8. 개인별 임무카드(신정)"), "8. 개인별 임무카드(신정)", p("본문"))))
    }

    @Test
    fun `연락망 장에서 갈라져 나온 소절은 부모와 같은 취급이다`() {
        val contactChapter = listOf("부 록", "6. 비상대응 협력 및 지원체계")
        val orgChapter = listOf("부 록", "2. 비상대응 조직 운영체계")
        val sop = listOf(ManualFormat.GROUP_SOP)
        val phones = (1..6).joinToString("\n") { "기관 02-6311-${1000 + it}" }
        val doc = ManualDoc("m", "2026.9", listOf(
            mk("s001", listOf(ManualFormat.GROUP_COVER), "변경연혁표", p("개정")),
            mk("s003", sop, "1. 충돌", p("본문")),
            mk("s099", sop, "99. 전화가 많은 상황", p(phones)),                  // 표준운영절차는 path 가 한 단계 → 옆 상황에 물려 주지 않는다
            mk("s042", orgChapter, "2.2.1 담당업무", table(listOf("직책", "담당자"), listOf("대책반장", "승무계획처장"))),
            mk("s043", orgChapter, "2.2.2 초기대응반", p("본문")),                // 머리글 하나 때문에 장 전체를 빼지는 않는다
            mk("s065", contactChapter, "6.4 경찰서 연락망", p("본문")),
            mk("s090", contactChapter, "6-6 비상대응지도", ManualBlock(type = "image", file = "img_054.jpg")),   // 제목에 낱말도 번호도 없다
            mk("s066", listOf("부 록", "7. 시설·장비 현황"), "7.1.1 소방시설", p("소화기 3개"))
        ))
        assertEquals(setOf("s001", "s099", "s042", "s065", "s090"), ManualFormat.sensitiveIds(doc))
        // 섹션 하나만 보는 판정으로는 s090 을 못 잡는다 — 그래서 부르는 쪽은 sensitiveIds 를 써야 한다
        assertFalse(ManualFormat.isSensitive(doc.sections.first { it.id == "s090" }))
    }
}
