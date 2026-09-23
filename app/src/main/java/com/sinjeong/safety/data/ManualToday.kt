package com.sinjeong.safety.data

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * "오늘의 비상조치" — 자료 모양과 순수 로직(날짜→항목, 판 일치, 원문 대조).
 * [ManualFormat] 처럼 안드로이드 API 가 들어오지 않는다(JVM 단위 테스트 대상).
 *
 * 자료(`manual/today.json`)는 매뉴얼과 같은 직원 전용 경로에서 **매뉴얼과 함께** 받는다.
 * 해설(hook·easy)은 사람이 쓴 글이지만 keySteps 는 "원문 그대로 인용"이라는 약속이다 —
 * 그 약속이 지켜졌는지는 기기의 매뉴얼 원문과 대조해 배지로 보여 준다.
 */
data class TodayManualItem(
    val sectionId: String,
    val title: String,
    val hook: String,
    val easy: String,
    val keySteps: List<String>,
    val role: String
)

data class TodayManual(val edition: String, val items: List<TodayManualItem>)

/** 화면이 그대로 그릴 수 있게 다 계산해 둔 오늘 항목 */
data class TodayManualCard(
    val item: TodayManualItem,
    /** keySteps 와 같은 순서·같은 개수. 그 문구가 기기의 섹션 원문에 그대로 있는가 */
    val verified: List<Boolean>
)

object ManualToday {

    /**
     * 2026-01-01 부터 그날 **전날까지**의 평일 수. 오늘의 규정이 쓰던 셈법을 그대로 옮긴 것이고
     * [RegulationRepository.todayRegulation] 도 이 함수를 쓴다 — 두 카드가 같은 날짜 감각으로 돈다.
     * 기기 시계의 날짜만 보므로 같은 날에는 모두에게 같은 항목이 나온다.
     * [month] 는 1~12. 2026-01-01 이전 날짜는 0.
     */
    fun weekdayIndex(year: Int, month: Int, day: Int): Int {
        val cur = GregorianCalendar(2026, Calendar.JANUARY, 1)
        val end = GregorianCalendar(year, month - 1, day)
        var count = 0
        while (cur.before(end)) {
            val d = cur.get(Calendar.DAY_OF_WEEK)
            if (d != Calendar.SATURDAY && d != Calendar.SUNDAY) count++
            cur.add(Calendar.DAY_OF_MONTH, 1)
        }
        return count
    }

    fun weekdayIndex(cal: Calendar): Int = weekdayIndex(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )

    fun isWeekend(cal: Calendar): Boolean {
        val d = cal.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    /**
     * 해설과 매뉴얼이 같은 판인가. 판이 다르면 카드를 통째로 숨긴다 —
     * 옛 해설이 새 매뉴얼에 붙으면 "원문 핵심 절차"가 이미 바뀐 절차일 수 있다.
     * 둘 중 하나라도 판 이름이 비어 있으면 확인할 수 없으므로 같다고 보지 않는다.
     */
    fun editionMatches(todayEdition: String, manualEdition: String): Boolean {
        val a = todayEdition.trim()
        return a.isNotEmpty() && a == manualEdition.trim()
    }

    /** 인덱스로 항목 고르기(순환). 음수·빈 목록에도 안전하다. */
    fun pick(items: List<TodayManualItem>, index: Int): TodayManualItem? =
        if (items.isEmpty()) null else items[Math.floorMod(index, items.size)]

    /**
     * 인용 문구가 그 섹션 평문에 그대로 있는가. askGuide 의 quote 검증과 **같은 정규화**다.
     * [sectionPlain] 은 [ManualFormat.plainText] 결과.
     */
    fun stepMatches(step: String, sectionPlain: String): Boolean =
        ManualFormat.quoteMatches(step, sectionPlain)

    /** 제목 앞 번호 — "25. " · "9-1. " · "1.1 " · "2.1.1" */
    private val LEAD_NO = Regex("""^\d+(?:[.-]\d+)*\.?\s*""")

    /**
     * 제목 뒤 사고 코드 묶음의 시작 — 첫 `[`, 또는 (괄호가 붙든 말든) 대문자 1자+숫자 3자리 코드
     * (`N212(S152…)`, `시(P411/…)`, ` R113(S113…)`). `[111(S152…)]` 처럼 글자 없는 코드는 `[` 로 잡힌다.
     * `(PSD)`·`(코로나19 등)`·`(폭음, 아크, 연기발생)` 같은 보통 괄호는 코드가 아니라 남는다.
     */
    private val CODE_TAIL = Regex("""\[|\(?\s*(?<![A-Za-z0-9])[A-Z]\d{3}(?!\d)""")

    /**
     * 카드 헤드라인용 제목 — 앞 번호와 뒤 코드 묶음을 뗀다(**표시 전용**, 자료·뷰어·AI 출처 라벨은 원문 그대로).
     * "25. 지상, 교량구간 열차 강풍/태풍사고 N212(S152, S252)/N214(S154, S254)" → "지상, 교량구간 열차 강풍/태풍사고".
     * 떼고 나서 비면 원래 제목을 쓴다.
     */
    fun cardTitle(title: String): String {
        val t = LEAD_NO.replaceFirst(title.trim(), "")
        val end = CODE_TAIL.find(t)?.range?.first ?: t.length
        return t.substring(0, end).trim().ifEmpty { title.trim() }
    }

    /** 제목 앞 번호("25", "9-1"), 없으면 "". 헤드라인에서 뗀 번호를 작은 라벨에 "상황 25" 로 남긴다. */
    fun situationNo(title: String): String =
        LEAD_NO.find(title.trim())?.value?.trim()?.trimEnd('.') ?: ""

    /**
     * 오늘 보여 줄 카드. 아래 중 하나라도 걸리면 null(= 오늘의 규정 한 장만 보인다):
     * 주말 / 판 불일치 / 항목 없음 / 고른 항목의 섹션이 기기 매뉴얼에 없음.
     */
    fun cardFor(today: TodayManual?, doc: ManualDoc?, cal: Calendar): TodayManualCard? {
        if (today == null || doc == null) return null
        if (isWeekend(cal)) return null
        if (!editionMatches(today.edition, doc.edition)) return null
        val item = pick(today.items, weekdayIndex(cal)) ?: return null
        val section = doc.sections.firstOrNull { it.id == item.sectionId } ?: return null
        val plain = ManualFormat.plainText(section)
        return TodayManualCard(item, item.keySteps.map { stepMatches(it, plain) })
    }
}
