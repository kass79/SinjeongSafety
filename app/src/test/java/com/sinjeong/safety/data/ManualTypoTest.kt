package com.sinjeong.safety.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 매뉴얼 **원본 문서의 오기**("운전"→"종합" 일괄 치환) 대응 점검.
 *
 * 앱은 원문을 고치지 않는다. 그래서 ① 그렇다는 안내를 (오기가 있는 판에서만) 띄우고,
 * ② "운전실 비상사다리"로 물어도 원문 "종합실 비상사다리"가 잡히게 동의어로 잇는다.
 * 여기서 지키려는 것은 ②의 부작용이다 — 규정 9권 검색이 "종합" 때문에 흔들리면 안 된다.
 */
class ManualTypoTest {

    private fun doc(vararg texts: String) = ManualDoc(
        "매뉴얼", "2026.9",
        texts.mapIndexed { i, t ->
            ManualSection("s%03d".format(i + 1), listOf(ManualFormat.GROUP_SOP), "$i. 제목",
                listOf(ManualBlock(type = "p", text = t)))
        }
    )

    @Test
    fun `오기 안내는 그 오기가 들어 있는 판에서만 뜬다`() {
        assertTrue(ManualFormat.hasDrivingTypo(doc("정상", "종합실 비상사다리를 설치한다")))
        assertTrue(ManualFormat.hasDrivingTypo(doc("주의종합으로 진입한다")))
        // 표 셀 안에 있어도 잡는다
        val inTable = ManualDoc("매뉴얼", "2026.9", listOf(
            ManualSection("s001", listOf(ManualFormat.GROUP_SOP), "1. 제목", listOf(
                ManualBlock(type = "table", rows = listOf(listOf(ManualCell("기관사"), ManualCell("종합실 확인"))))
            ))
        ))
        assertTrue(ManualFormat.hasDrivingTypo(inTable))
        // 수정본: 바른 말만 있으면 안내가 사라진다. '종합관제'는 본래 맞는 말이라 표지가 아니다.
        assertFalse(ManualFormat.hasDrivingTypo(doc("운전실 비상사다리", "종합관제센터에 급보", "주의운전")))
        assertFalse(ManualFormat.hasDrivingTypo(ManualDoc("매뉴얼", "2026.9", emptyList())))

        // 섹션 하단 안내는 이스케이프되어 나가고, 안 주면 안 그린다
        val s = doc("본문").sections[0]
        assertTrue(ManualFormat.html(s, false, notice = ManualFormat.TYPO_NOTICE)
            .contains("<div class=\"nt\">원본 문서에 &#39;운전&#39;이"))
        assertFalse(ManualFormat.html(s, false).contains("class=\"nt\""))
    }

    // ── 검색 ────────────────────────────────────────────────────
    private fun regBooks(): List<RegBook> {
        val f = listOf("app/src/main/assets/regulations.json", "src/main/assets/regulations.json")
            .map { File(it) }.firstOrNull { it.isFile }
        assumeTrue("regulations.json 을 못 찾아 건너뜀", f != null)
        val root = JSONObject(f!!.readText())
        return root.keys().asSequence().toList().sorted().map { name ->
            val arr = root.getJSONArray(name)
            RegBook(name, "", 0L, "", false, (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RegArticle(o.optString("n"), o.optString("t"), o.optString("b"))
            })
        }
    }

    private val typoWords = listOf("종합실", "주의종합", "확인종합", "종합지시")

    @Test
    fun `동의어를 넣어도 규정 9권 검색은 흔들리지 않는다`() {
        val books = regBooks()
        // 전제: 오기 낱말은 규정에 한 번도 안 나온다 (나오기 시작하면 이 동의어를 다시 생각해야 한다)
        for (w in typoWords) {
            val n = books.sumOf { b -> b.articles.count { it.title.contains(w) || it.body.contains(w) } }
            assertEquals("규정에 '$w' 가 나온다", 0, n)
        }
        // 대표 질의: 걸린 낱말(marks)에 오기 낱말이 끼지 않고, 1위는 내가 친 말을 실제로 담고 있다
        for (q in listOf("운전실", "주의운전", "확인운전", "운전지시", "운전실 출입문 고장")) {
            val hits = RegulationSearch.search(books, q)
            assertTrue("'$q' 결과 없음", hits.isNotEmpty())
            val top = hits[0]
            // (여러 낱말 질의는 1위가 첫 낱말을 꼭 담는다는 보장이 없어 한 낱말 질의만 본다)
            if (' ' !in q) assertTrue("'$q' 1위(${top.book} ${top.article.num})에 '$q' 가 없다",
                top.article.title.contains(q) || top.article.body.contains(q))
            for (h in hits.take(10)) assertTrue(h.marks.none { it in typoWords })
        }
        // '종합'이 들어간 바른 말은 새 동의어를 타지 않는다 → '운전실' 조문이 끌려오지 않는다
        for (q in listOf("종합관제", "종합평정", "종합제어반")) {
            val hits = RegulationSearch.search(books, q)
            for (h in hits.take(10)) {
                assertTrue("'$q' → ${h.marks}", h.marks.none { it == "운전실" || it == "주의운전" || it in typoWords })
            }
        }
    }

    @Test
    fun `운전실로 물어도 원문의 종합실 섹션이 잡힌다`() {
        var up: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var found: File? = null
        repeat(4) { up?.let { d -> File(d, "manual_build/manual.json").takeIf { it.isFile }?.let { found = found ?: it }; up = d.parentFile } }
        assumeTrue("manual_build/manual.json 이 없어 건너뜀 (CI 에는 없는 게 정상)", found != null)
        val doc = ManualJson.parseDoc(found!!.readText())
        assertTrue("이 판에는 오기가 있어야 한다(없으면 수정본 — 동의어·안내를 걷어낼 때다)",
            ManualFormat.hasDrivingTypo(doc))

        // ManualRepository.asBook 과 같은 모양 (저장소는 Firebase 를 물고 있어 JVM 에서 못 부른다)
        val manual = RegBook(ManualRepository.BOOK_NAME, "", 0L, "", false, doc.sections.map {
            RegArticle(ManualFormat.situationTag(it), it.title, ManualFormat.plainText(it), it.id)
        })
        val only = listOf(manual)

        val want = doc.sections.filter { ManualFormat.plainText(it).contains("종합실 비상사다리") }.map { it.id }
        assumeTrue("원문에 '종합실 비상사다리' 가 없다", want.isNotEmpty())
        val top = RegulationSearch.search(only, "운전실 비상사다리").take(5)
        assertTrue("상위 5건 ${top.map { it.article.sid }} 에 $want 가 없다", top.any { it.article.sid in want })
        assertTrue(top.first { it.article.sid in want }.marks.contains("종합실"))
        println("운전실 비상사다리 → " + top.map { it.article.sid + ":" + Math.round(it.score) })

        for ((q, w) in listOf("주의운전" to "주의종합", "확인운전" to "확인종합", "운전지시" to "종합지시")) {
            val ids = doc.sections.filter { ManualFormat.plainText(it).contains(w) }.map { it.id }.toSet()
            val got = RegulationSearch.search(only, q).map { it.article.sid }.toSet()
            assertTrue("'$q' 로 '$w' 섹션 $ids 가 다 안 잡힌다", got.containsAll(ids))
        }
    }
}
