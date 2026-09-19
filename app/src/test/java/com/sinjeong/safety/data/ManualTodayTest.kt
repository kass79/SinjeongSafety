package com.sinjeong.safety.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * "오늘의 비상조치" 순수 로직 — 날짜→항목, 판 일치, 원문 대조, today.json 읽기.
 *
 * 여기서 잡으려는 사고:
 *  - 같은 날인데 사람마다 다른 항목이 나오는 것 (날짜 셈법이 시각·요일에 흔들림)
 *  - 옛 해설(today.json)이 새 판 매뉴얼에 붙어 나오는 것
 *  - "원문 그대로"라던 절차가 실은 매뉴얼에 없는 문장인데 배지가 붙는 것
 */
class ManualTodayTest {

    private fun item(sid: String, vararg steps: String) =
        TodayManualItem(sid, "제목 $sid", "한 줄", "쉬운 설명", steps.toList(), "기관사")

    private fun doc(edition: String = "2026.9") = ManualDoc(
        title = "매뉴얼", edition = edition,
        sections = listOf(
            ManualSection(
                "s003", listOf(ManualFormat.GROUP_SOP), "1. 역 구내 충돌사고",
                listOf(
                    ManualBlock(type = "h", level = 1, text = "초동조치"),
                    ManualBlock(
                        type = "table",
                        rows = listOf(listOf(ManualCell("기관사"), ManualCell("열차무선방호 실시 및\n운전관제 급보")))
                    ),
                    ManualBlock(type = "p", text = "차량상태 및   인명피해 현황 파악")
                )
            )
        )
    )

    private fun day(y: Int, m: Int, d: Int, hour: Int = 12): Calendar =
        GregorianCalendar(y, m - 1, d, hour, 30)

    @Test
    fun `평일 수 셈법은 오늘의 규정과 같다`() {
        // 2026-01-01 은 목요일. "그날 전날까지의 평일 수"다.
        assertEquals(0, ManualToday.weekdayIndex(2026, 1, 1))
        assertEquals(1, ManualToday.weekdayIndex(2026, 1, 2))   // 금
        assertEquals(2, ManualToday.weekdayIndex(2026, 1, 3))   // 토 (목·금 이틀)
        assertEquals(2, ManualToday.weekdayIndex(2026, 1, 4))   // 일
        assertEquals(2, ManualToday.weekdayIndex(2026, 1, 5))   // 월
        assertEquals(3, ManualToday.weekdayIndex(2026, 1, 6))
        assertEquals(7, ManualToday.weekdayIndex(2026, 1, 12))  // 한 주 뒤 월요일 = +5
        assertEquals(0, ManualToday.weekdayIndex(2025, 12, 31)) // 기준일 이전
        // 하루 중 시각이 달라도 같은 값 (새벽 첫차 기관사와 막차 기관사가 같은 카드를 본다)
        assertEquals(
            ManualToday.weekdayIndex(day(2026, 9, 21, 0)),
            ManualToday.weekdayIndex(day(2026, 9, 21, 23))
        )
        // 평일은 하루에 하나씩 넘어간다
        assertEquals(
            ManualToday.weekdayIndex(day(2026, 9, 21)) + 1,
            ManualToday.weekdayIndex(day(2026, 9, 22))
        )
    }

    @Test
    fun `항목은 순환하고 빈 목록에도 안전하다`() {
        val items = listOf(item("a"), item("b"), item("c"))
        assertEquals("a", ManualToday.pick(items, 0)!!.sectionId)
        assertEquals("c", ManualToday.pick(items, 2)!!.sectionId)
        assertEquals("a", ManualToday.pick(items, 3)!!.sectionId)
        assertEquals("c", ManualToday.pick(items, -1)!!.sectionId)
        assertNull(ManualToday.pick(emptyList(), 5))
    }

    @Test
    fun `판이 다르면 카드를 숨긴다`() {
        assertTrue(ManualToday.editionMatches("2026.9", "2026.9"))
        assertTrue(ManualToday.editionMatches(" 2026.9 ", "2026.9"))
        assertFalse(ManualToday.editionMatches("2026.9", "2027.3"))
        // 한쪽이라도 판 이름이 없으면 확인할 수 없다 → 같다고 보지 않는다
        assertFalse(ManualToday.editionMatches("", ""))
        assertFalse(ManualToday.editionMatches("2026.9", ""))
    }

    @Test
    fun `핵심 절차는 기기 원문과 대조해 배지를 단다`() {
        val today = TodayManual(
            "2026.9",
            listOf(item("s003",
                "열차무선방호 실시 및 운전관제 급보",     // 표 셀 안 줄바꿈 → 공백 차이뿐
                "차량상태 및 인명피해 현황 파악",          // 원문은 공백 3칸
                "열차 무선방호를 실시하고 관제에 급보"))   // 고쳐 쓴 문장 → 불일치
        )
        // 2026-01-01(목) → index 0
        val card = ManualToday.cardFor(today, doc(), day(2026, 1, 1))
        assertNotNull(card)
        assertEquals(listOf(true, true, false), card!!.verified)
        assertEquals(card.item.keySteps.size, card.verified.size)
    }

    @Test
    fun `주말 판 불일치 없는 섹션이면 카드가 없다`() {
        val today = TodayManual("2026.9", listOf(item("s003", "초동조치")))
        assertNotNull(ManualToday.cardFor(today, doc(), day(2026, 1, 2)))          // 금
        assertNull(ManualToday.cardFor(today, doc(), day(2026, 1, 3)))             // 토
        assertNull(ManualToday.cardFor(today, doc(), day(2026, 1, 4)))             // 일
        assertNull(ManualToday.cardFor(today, doc("2027.3"), day(2026, 1, 2)))     // 판 불일치
        assertNull(ManualToday.cardFor(null, doc(), day(2026, 1, 2)))              // today.json 없음
        assertNull(ManualToday.cardFor(today, null, day(2026, 1, 2)))              // 매뉴얼 없음
        assertNull(ManualToday.cardFor(TodayManual("2026.9", emptyList()), doc(), day(2026, 1, 2)))
        val ghost = TodayManual("2026.9", listOf(item("s999", "초동조치")))
        assertNull(ManualToday.cardFor(ghost, doc(), day(2026, 1, 2)))             // 없는 섹션
    }

    @Test
    fun `today json 은 너그럽게 읽되 열쇠 없는 항목은 버린다`() {
        val json = """
            {"edition":" 2026.9 ","items":[
              {"sectionId":"s003","title":"충돌","hook":"급보 먼저","easy":"설명",
               "keySteps":["하나","","둘"],"role":"기관사"},
              {"sectionId":"","title":"열쇠 없음"},
              {"sectionId":"s004"},
              "엉뚱한 값",
              {"sectionId":"s005","title":"칸이 비어도 된다"}
            ]}
        """.trimIndent()
        val t = ManualJson.parseToday(json)
        assertEquals("2026.9", t.edition)
        assertEquals(listOf("s003", "s005"), t.items.map { it.sectionId })
        assertEquals(listOf("하나", "둘"), t.items[0].keySteps)
        assertEquals("", t.items[1].role)
        assertTrue(t.items[1].keySteps.isEmpty())
        // items 가 아예 없어도 터지지 않는다
        assertTrue(ManualJson.parseToday("{}").items.isEmpty())
    }
}
