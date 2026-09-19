package com.sinjeong.safety.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * **실제 매뉴얼**(2026.9판 스키마 v2: 91개 섹션)을 실제 생성기에 통과시키는 스윕.
 *
 * 매뉴얼에는 직원 실명이 있어 저장소에 없다(그래서 CI 에도 없다) → 파일이 없으면 **건너뛴다**.
 * 찾는 순서: 시스템 속성 `manualBuildDir` → 환경변수 `MANUAL_BUILD_DIR` →
 * 작업 폴더에서 위로 올라가며 `manual_build/`(이 PC 에서는 저장소 바로 옆에 있다).
 *
 * 합성 자료 테스트는 "내가 떠올린 모양"만 검사한다. 표 290개(셀 안의 표 57개)·소제목 158개를 가진 진짜 자료는
 * 빈 셀·병합·특수문자가 떠올리지 못한 조합으로 나온다 — 그래서 HTML 을 XML 파서에 넣어
 * 태그 짝과 이스케이프를 기계로 확인한다.
 */
class ManualRealDataTest {

    private fun buildDir(): File? {
        val named = listOfNotNull(System.getProperty("manualBuildDir"), System.getenv("MANUAL_BUILD_DIR"))
            .map { File(it) }
        var up: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val walked = ArrayList<File>()
        repeat(4) { up?.let { walked.add(File(it, "manual_build")); up = it.parentFile } }
        return (named + walked).firstOrNull { File(it, "manual.json").isFile }
    }

    private fun realDoc(): ManualDoc {
        val dir = buildDir()
        assumeTrue("manual_build/manual.json 이 없어 건너뜀 (CI 에는 없는 게 정상)", dir != null)
        return ManualJson.parseDoc(File(dir, "manual.json").readText())
    }

    /** body 안쪽을 XML 로 읽을 수 있게 다듬는다 (우리 생성기는 img 만 닫지 않는다) */
    private fun parseBody(html: String) {
        val body = html.substringAfter("<body>").substringBeforeLast("</body>")
        val xml = "<root>" + body.replace(Regex("<img ([^>]*)>"), "<img $1/>") + "</root>"
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }

    private fun count(html: String, needle: String) = html.split(needle).size - 1

    /** 섹션의 모든 블록 — 스키마 v2 의 표 셀 `blocks` 안까지(화면이 그리는 범위와 같다) */
    private fun allBlocks(blocks: List<ManualBlock>, depth: Int = 0): List<ManualBlock> {
        if (depth > ManualFormat.MAX_DEPTH) return emptyList()
        val out = ArrayList<ManualBlock>()
        for (b in blocks) {
            out.add(b)
            if (b.type == "table") for (row in b.rows) for (c in row) out.addAll(allBlocks(c.blocks, depth + 1))
        }
        return out
    }

    @Test
    fun `실제 매뉴얼 전 섹션이 생성기를 통과한다`() {
        val doc = realDoc()
        assertTrue("섹션이 없다", doc.sections.isNotEmpty())
        assertTrue("판 이름이 없다", doc.edition.isNotBlank())

        var hTotal = 0; val hByLevel = IntArray(4); var flow = 0
        var tables = 0; var images = 0; var maxHtml = 0; var maxId = ""
        var cellsWithBlocks = 0; var flowImages = 0; var altChars = 0; var puaLeft = 0
        var tablesTop = 0; var imageOnlyTables = 0; val files = LinkedHashSet<String>()
        val t0 = System.nanoTime()

        for (s in doc.sections) {
            // v2: 표 셀의 blocks 안까지 센다(v1 자료에서는 맨 위 블록만 있으므로 값이 같다)
            val all = allBlocks(s.blocks)
            val hs = all.filter { it.type == "h" && it.text.isNotBlank() }
            val flowPs = all.count { it.type == "p" && it.text.trim() == "사고흐름도" }
            val tblAll = all.count { it.type == "table" && it.rows.isNotEmpty() }
            val imgOnly = all.count { ManualFormat.imageOnlyTable(it) != null }   // 테두리 없이 그림만 그리는 1×1 표
            val tbl = tblAll - imgOnly
            tablesTop += s.blocks.count { it.type == "table" && it.rows.isNotEmpty() }
            imageOnlyTables += imgOnly
            files.addAll(s.imageFiles)
            val img = all.count { it.type == "image" && it.file.isNotBlank() }
            cellsWithBlocks += all.filter { it.type == "table" }.sumOf { t -> t.rows.sumOf { r -> r.count { it.blocks.isNotEmpty() } } }
            flowImages += all.count { it.type == "image" && it.flow }
            altChars += all.filter { it.type == "image" }.sumOf { it.alt.length }
            for (b in all) {
                val texts = listOf(b.text, b.caption, b.alt) + b.rows.flatten().map { it.text }
                for (t in texts) {
                    var k = 0
                    while (k < t.length) {
                        val c = t.codePointAt(k)
                        if (c in 0xE000..0xF8FF || c >= 0xF0000) puaLeft++
                        k += Character.charCount(c)
                    }
                }
            }
            hTotal += hs.size; flow += flowPs; tables += tblAll; images += img
            hs.forEach { hByLevel[it.level.coerceIn(1, 3)]++ }

            // 그림 이름은 전부 화이트리스트 안이어야 한다 (아니면 그 그림이 조용히 사라진다)
            for (b in all.filter { it.type == "image" }) {
                assertTrue("${s.id}: 그림 이름 ${b.file}", ManualFormat.isSafeImageName(b.file))
            }

            for (dark in listOf(false, true)) {
                val html = ManualFormat.html(s, dark, doc.edition)
                if (html.length > maxHtml) { maxHtml = html.length; maxId = s.id }

                // ① 태그 짝·이스케이프: XML 파서가 끝까지 읽어야 한다
                try { parseBody(html) } catch (e: Exception) {
                    throw AssertionError("${s.id} dark=$dark: HTML 이 깨졌다 — ${e.message}", e)
                }
                // ② 소제목이 빠짐없이 제 단계 태그로 나갔는가 (사고흐름도 단독 p 는 h1 로 더해진다)
                val want = IntArray(4)
                hs.forEach { want[it.level.coerceIn(1, 3)]++ }
                want[1] += flowPs
                for (lv in 1..3) assertEquals("${s.id}: h$lv 개수", want[lv], count(html, "<h$lv>"))
                // ③ 표·그림 개수
                assertEquals("${s.id}: 표", tbl, count(html, "<table"))
                assertEquals("${s.id}: 그림", img, count(html, "<img "))
                // ④ 한컴 전용문자가 남지 않았는가 (남으면 기기에서 □ 로 나온다)
                var i = 0
                while (i < html.length) {
                    val c = html.codePointAt(i)
                    assertFalse("${s.id}: 전용문자 U+" + Integer.toHexString(c),
                        c in 0xE000..0xF8FF || c >= 0xF0000)
                    i += Character.charCount(c)
                }
                assertFalse(html.contains("<script", ignoreCase = true))
            }

            // ⑤ 평문(검색·AI 재료)에 소제목이 들어 있는가
            val plain = ManualFormat.normalizeWs(ManualFormat.plainText(s))
            for (h in hs) {
                assertTrue("${s.id}: 평문에 소제목 '${h.text.trim()}' 없음",
                    plain.contains(ManualFormat.normalizeWs(h.text)))
            }
        }

        val ms = (System.nanoTime() - t0) / 1_000_000
        val blockedSet = ManualFormat.sensitiveIds(doc)
        val blocked = doc.sections.map { it.id }.filter { it in blockedSet }   // 배열 순서대로
        println("실데이터 스윕: ${doc.edition}판 섹션 ${doc.sections.size}개 × 밝은/어두운 = ${doc.sections.size * 2}회 통과")
        println("  소제목 $hTotal (L1 ${hByLevel[1]} / L2 ${hByLevel[2]} / L3 ${hByLevel[3]}) + 사고흐름도 단독 p $flow")
        println("  표 $tables (최상위 $tablesTop + 셀 안 ${tables - tablesTop}, 그중 그림만 감싼 1×1 표 $imageOnlyTables 는 그림으로 그림) · " +
            "그림 블록 $images · 그림 파일 ${files.size} (flow_ ${files.count { it.startsWith("flow_") }}, print_ ${files.count { it.startsWith("print_") }})")
        println("  가장 큰 HTML $maxId ${maxHtml / 1024}KB · 전체 ${ms}ms")
        // 내려받기 목록(= 섹션들의 imageFiles)에 든 파일이 실제로 있는가 — 없으면 기기에서 받기가 통째로 실패한다
        val imgDir = listOf(File(buildDir(), "upload/manual/images"), File(buildDir(), "images")).firstOrNull { it.isDirectory }
        if (imgDir != null) {
            val missing = files.filter { !File(imgDir, it).isFile }
            assertTrue("그림 파일이 없다: $missing", missing.isEmpty())
            println("  그림 파일 ${files.size}개 모두 ${imgDir.name} 폴더에 있음")
        }
        // 0자 섹션: 글은 없어도 그릴 것(그림)이 있으면 목록에 남는다
        val noText = doc.sections.filter { ManualFormat.plainText(it).isBlank() }
        println("  글 0자 섹션 ${noText.map { it.id }} · 그중 그릴 것이 없는 섹션 ${noText.filter { !ManualFormat.hasContent(it) }.map { it.id }} · 목록에 올리는 섹션 ${doc.visibleSections.size}")
        println("  AI 제외 섹션 ${blocked.size}개: $blocked")
        println("  v2 표지: blocks 가진 셀 $cellsWithBlocks · 흐름도 그림 $flowImages · alt 글자 $altChars · 자료에 남은 전용문자 $puaLeft (v2 는 0 이어야 한다)")
        assertTrue("소제목(h) 블록이 하나도 없다 — 옛 자료인가?", hTotal > 0)
    }

    /**
     * 공개 개인정보처리방침: "비상연락망·변경연혁 등 직원 이름·전화번호가 담긴 부분은 AI 로 보내지 않습니다."
     * 이 문장이 **이 판에서 참인지** 기계로 본다. 이름은 메모리에서만 다루고, 출력은 섹션 id·건수·가린 꼴뿐이다.
     *
     * 방법: 제외된 섹션의 "사람을 적는 자리"에서 이름 꼴을 거둔다 —
     *  ① 라벨–값 행("성 명 | 값". 임무카드는 이름을 **한 글자씩 띄어** 적는다)
     *  ② 한 칸에 "이름 줄 + 전화번호 줄"(비상연락망)
     *  ③ 성명류 머리글이 있는 표에서 칸 전체가 2~4자 한글 토큰뿐인 칸(변경연혁표의 담당·검토·확인)
     * 그리고 그 글자들이 **전송 대상 섹션의 AI 본문**에 낱말로(붙여 쓴 꼴이든 띄어 쓴 꼴이든) 나오는지 찾는다.
     * 같은 자리에 적힌 직책·부서 낱말은 허용 목록으로 거른다. **허용 목록에 사람 이름을 넣지 말 것** —
     * 새 토큰이 걸려 이 테스트가 깨지면, 매뉴얼에서 그 자리를 직접 보고 낱말인지 이름인지 가린 뒤에 넣는다.
     * 끝 글자로 낱말을 가리는 식(…처·…실·…기)은 쓰지 말 것: 이름의 흔한 끝 글자(호·원·식·기·성…)와 겹쳐 진짜 이름이 빠진다.
     */
    @Test
    fun `AI 로 보내는 섹션에 직원 이름과 개인 연락처가 남아 있지 않다`() {
        val doc = realDoc()
        val blocked = ManualFormat.sensitiveIds(doc)
        val labels = setOf("성명", "이름", "성함", "담당자", "검토자", "확인자")
        val phone = Regex("0\\d{1,2}[-)\\s]{0,2}\\d{3,4}[-\\s]?\\d{4}|(?<![\\d-])\\d{3,4}-\\d{4}(?![\\d-])")
        val mobile = Regex("(?<!\\d)01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)")
        val nameLike = Regex("^[가-힣](\\s?[가-힣]){1,3}$")          // 2~4자, 글자 사이 공백 허용
        // 사람을 적는 자리에 함께 적힌 직책·부서·보통 낱말(2026.9판에서 하나씩 눈으로 확인한 것). 사람 이름은 넣지 않는다.
        val commonWords = setOf("비고", "운전관제", "구조", "기술본부", "운용", "소속", "팀장", "영업본부", "대책반장",
            "언론처장", "처장", "승무본부", "군자", "안전문", "소장", "기획본부")
        fun squeeze(t: String) = t.filterNot(Char::isWhitespace)
        fun mask(w: String) = w.first() + "○".repeat(w.length - 1)

        println("AI 제외 섹션 ${blocked.size}개 (id | 까닭 | 전화번호 꼴 수 | 제목)")
        val names = HashSet<String>()
        for (s in doc.sections) {
            if (s.id !in blocked) continue
            val why = if (ManualFormat.isSensitive(s)) "제 내용" else "같은 장의 연락망 소절"
            println("  ${s.id} | $why | ${ManualFormat.phoneCount(ManualFormat.plainText(s))} | ${s.title.take(34)}")
            for (t in allBlocks(s.blocks).filter { it.type == "table" }) {
                val hasLabel = t.rows.any { r -> r.any { c -> c.text.lineSequence().any { squeeze(it) in labels } } }
                for (row in t.rows) {
                    // ① 라벨–값
                    for (i in 0 until row.size - 1) {
                        if (squeeze(row[i].text) in labels) {
                            val v = row[i + 1].text.lineSequence().firstOrNull()?.trim().orEmpty()
                            if (nameLike.matches(v)) names.add(squeeze(v))
                        }
                    }
                    for (c in row) {
                        val lines = c.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                        // ② 이름 줄 + 전화번호 줄
                        for (k in 0 until lines.size - 1) {
                            if (nameLike.matches(lines[k]) && phone.containsMatchIn(lines[k + 1])) names.add(squeeze(lines[k]))
                        }
                        // ③ 성명 표의, 이름 꼴 토큰뿐인 칸
                        if (hasLabel && squeeze(c.text) !in labels) {
                            val toks = c.text.split('/', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                            if (toks.isNotEmpty() && toks.all { nameLike.matches(it) }) toks.forEach { names.add(squeeze(it)) }
                        }
                    }
                }
            }
        }
        names.removeAll(commonWords)

        val eligible = doc.visibleSections.filter { it.id !in blocked }
        var hits = 0
        for (s in eligible) {
            val body = s.title + " / " + ManualFormat.aiBody(s)
            // 개인 연락처
            assertFalse("${s.id}: 휴대전화 번호가 있다", mobile.containsMatchIn(body))
            val phones = ManualFormat.phoneCount(body)
            assertTrue("${s.id}: 전화번호 꼴이 5개 이상이다", phones < 5)
            if (phones > 0) println("  전송 대상인데 전화번호 꼴이 있는 섹션: ${s.id} ($phones 개 — 기관·부서 번호인지 눈으로 확인할 것)")
            // 이름: 붙여 쓴 꼴과 한 글자씩 띄어 쓴 꼴 모두
            for (n in names) {
                val pat = Regex("(?<![가-힣])" + n.map { Regex.escape(it.toString()) }.joinToString("[ \\t]*") + "(?![가-힣])")
                val found = pat.findAll(body).count()
                if (found > 0) { hits += found; println("  ★ ${s.id}: 제외 섹션의 이름 꼴 ${mask(n)}(${n.length}자)이 $found 번 나온다") }
            }
        }
        println("전송 대상 ${eligible.size}개 섹션 · 제외 섹션에서 거둔 이름 꼴 ${names.size}종(허용 낱말 제외) · 전송 대상에 남은 것 $hits 건")
        assertTrue("이름을 하나도 못 거뒀다 — 거두는 기준이 자료와 안 맞는다", names.size >= 20)
        assertEquals("AI 로 보내는 섹션에 직원 이름 꼴이 남아 있다(위 ★ 줄)", 0, hits)
    }

    @Test
    fun `실제 오늘의 비상조치가 실제 매뉴얼과 맞물린다`() {
        val doc = realDoc()
        val f = File(buildDir(), "today.json")
        assumeTrue("today.json 이 없어 건너뜀", f.isFile)
        val today = ManualJson.parseToday(f.readText())

        assertTrue("항목이 없다", today.items.isNotEmpty())
        assertTrue("판이 다르다: today=${today.edition} manual=${doc.edition}",
            ManualToday.editionMatches(today.edition, doc.edition))

        val byId = doc.sections.associateBy { it.id }
        var steps = 0; var matched = 0
        val misses = ArrayList<String>()
        for (it in today.items) {
            val s = byId[it.sectionId]
            assertTrue("${it.sectionId}: 매뉴얼에 없는 섹션", s != null)
            // 실명·연락처 섹션을 해설 카드로 돌리지 않는다
            assertFalse("${it.sectionId}: AI 제외(실명) 섹션이 오늘의 비상조치에 들어 있다",
                ManualFormat.isSensitive(s!!))
            val plain = ManualFormat.plainText(s)
            for (k in it.keySteps) {
                steps++
                if (ManualToday.stepMatches(k, plain)) matched++ else misses.add("${it.sectionId}: $k")
            }
        }
        println("오늘의 비상조치: 항목 ${today.items.size}개 · 핵심 절차 $steps 개 중 원문 일치 $matched 개")
        misses.forEach { println("  불일치 → $it") }
        // 문구는 검수 중이라 전부 일치를 강제하지는 않는다(불일치는 앱에서 회색 배지로 드러난다).
        // 다만 절반도 안 맞으면 대조 로직이나 자료가 통째로 어긋난 것이다.
        assertTrue("원문 일치가 절반도 안 된다 ($matched/$steps)", matched * 2 >= steps)
    }
}
