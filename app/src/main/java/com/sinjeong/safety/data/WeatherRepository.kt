package com.sinjeong.safety.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 날씨 (기상청 / 공공데이터포털)
 *
 * 홈 헤더의 작은 칩에 쓸 두 가지를 모은다.
 * - 서울에 "지금 발효 중인" 기상특보  (WthrWrnInfoService/getPwnStatus)
 * - 신정동 기준 현재 기온·하늘상태    (VilageFcstInfoService_2.0/getUltraSrtFcst)
 *
 * 설계 원칙
 * - 실패하면 그 조각만 null. 특보 조회가 죽어도 기온은 살고, 반대도 마찬가지다.
 * - 호출량 보호: 1시간에 한 번만 실제 조회하고 그 사이엔 저장값을 쓴다.
 *   (일반 인증키 하루 한도 안에서 승무원 전원이 써도 안전하도록)
 *
 * ※ 예전엔 getWthrWrnMsg(통보문)를 부르고 응답 전체에서 "폭염주의보" 같은 글자를
 *   정규식으로 긁었는데, **해제 통보문에도 "폭염주의보 해제"처럼 그 글자가 들어 있어서**
 *   특보가 풀린 뒤에도 계속 발효 중으로 뜨는 버그가 있었다. 통보문은 "발표/해제 이벤트"라
 *   현재 상태를 알 수 없다. 그래서 "현황"인 getPwnStatus 의 t6(전국 발효 현황)로 바꿨다.
 */
/** 헤더 칩 한 줄에 필요한 전부 */
data class WeatherNow(
    val tempC: Int?,        // 기온(℃). 못 받으면 null
    val sky: Int,           // 1 맑음 / 3 구름많음 / 4 흐림
    val pty: Int,           // 0 없음 / 1 비 / 2 비눈 / 3 눈 / 4 소나기
    val warning: String?    // 서울에 발효 중인 특보. 없으면 null
) {
    /** 헤더 칩에 쓸 이모지 한 글자. 밤낮은 구분하지 않는다(아이콘 두 벌 관리할 값어치가 없다) */
    val emoji: String
        get() = when (pty) {
            1 -> "🌧"
            2 -> "🌨"
            3 -> "❄️"
            4 -> "🌦"
            else -> when (sky) {
                1 -> "☀️"
                3 -> "⛅"
                else -> "☁️"
            }
        }
}

object WeatherRepository {

    // 공공데이터포털 일반 인증키 (이미 URL 인코딩된 형태 그대로 사용해야 한다.
    // 다시 인코딩하면 %2B가 %252B로 바뀌어 인증에 실패한다)
    private const val SERVICE_KEY =
        "XMXiJ0ib%2BxhyschjrOEKEH%2BLiBVAOtF9twd2uxLnqsmJpcc4CHQINyj%2Fs9P3HVn9IjVM3q3ROhpLUEga28xQSw%3D%3D"

    private const val STN_SEOUL = "109"
    private const val NX = 58   // 신정동(양천구) 격자
    private const val NY = 125
    private const val CACHE_MS = 60 * 60 * 1000L   // 1시간

    private const val PREF = "safety_prefs"
    // 캐시 키를 v2로 새로 판 이유: 옛 키(weather_warning_text)에는 위 버그로 생긴
    // 잘못된 "폭염주의보" 값이 이미 폰에 1시간짜리로 박혀 있다. 같은 키를 재사용하면
    // 고친 직후에도 그 값이 살아나 "안 고쳐진 것처럼" 보인다.
    private const val KEY_JSON = "weather_now_v2"
    private const val KEY_TIME = "weather_now_v2_time"

    /** 특보 + 기온. 1시간 캐시. 전부 실패하면 null */
    suspend fun getWeather(context: Context): WeatherNow? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0L) < CACHE_MS) {
            val cached = prefs.getString(KEY_JSON, null)
            if (cached != null) fromJson(cached)?.let { return it }
        }

        // 둘 중 하나가 죽어도 나머지는 살린다
        val warning = runCatching { fetchWarning() }.getOrNull()
        val fcst = runCatching { fetchForecast() }.getOrNull()
        if (warning == null && fcst == null) return null

        val now = WeatherNow(
            tempC = fcst?.first,
            sky = fcst?.second ?: 1,
            pty = fcst?.third ?: 0,
            warning = warning
        )
        prefs.edit()
            .putString(KEY_JSON, toJson(now))
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
        return now
    }

    // ── 기상특보 현황 ────────────────────────────────────────────

    private suspend fun fetchWarning(): String? = withContext(Dispatchers.IO) {
        val url = "https://apis.data.go.kr/1360000/WthrWrnInfoService/getPwnStatus" +
            "?serviceKey=$SERVICE_KEY&pageNo=1&numOfRows=1&dataType=JSON&stnId=$STN_SEOUL"
        parseSeoulWarning(get(url)?.let { extractT6(it) })
    }

    /** 응답에서 t6(전국 발효 현황) 문자열만 꺼낸다. 형식이 바뀌어도 죽지 않게 전부 opt로 더듬는다 */
    private fun extractT6(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val items = root.optJSONObject("response")
            ?.optJSONObject("body")
            ?.optJSONObject("items")
            ?.optJSONArray("item") ?: return null
        return items.optJSONObject(0)?.optString("t6")?.takeIf { it.isNotBlank() }
    }

    /**
     * t6 파싱 — 서울에 발효 중인 특보만 골라 "폭염주의보 · 호우주의보" 로 묶는다.
     *
     * t6 실제 형식:
     *   o 폭염경보 : 전라남도(광양, 순천), 광주, 울산(울산서부)
     *   o 폭염주의보 : 충청남도(공주), 대전, 대구, 부산
     * 발효 중인 게 없으면 "o 없 음" / "o 없음" 같은 값이 온다.
     *
     * 순수 함수로 빼 둔 이유: 실제 응답 문자열로 눈이 아니라 손으로 검증할 수 있어야 한다.
     */
    internal fun parseSeoulWarning(t6: String?): String? {
        if (t6.isNullOrBlank()) return null
        val found = t6.lines()
            .map { it.trim() }
            .filter { it.startsWith("o ") || it.startsWith("o\t") }
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx < 0) return@mapNotNull null
                val kind = line.substring(2, idx).trim()          // "o " 다음부터 ':' 앞까지
                val areas = line.substring(idx + 1)
                // 지역 목록에 "서울"이 든 줄만 채택. 다른 시도 이름에 "서울"이 들어가는 경우는 없다.
                if (kind.endsWith("경보") || kind.endsWith("주의보")) {
                    if (areas.contains("서울")) kind else null
                } else null
            }
            .distinct()
        return if (found.isEmpty()) null else found.joinToString(" · ")
    }

    // ── 초단기예보(기온·하늘·강수) ───────────────────────────────

    /** Triple(기온, SKY, PTY). 하나라도 못 읽으면 그 자리는 기본값 */
    private suspend fun fetchForecast(): Triple<Int?, Int, Int>? = withContext(Dispatchers.IO) {
        // 초단기예보는 매시 30분 발표분이 45분에 풀린다.
        // 현재시각 -1시간의 시(HH)를 쓰면 그 슬롯은 항상 존재한다(날짜 넘어감은 Calendar가 처리).
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -1) }
        val baseDate = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(cal.time)
        val baseTime = SimpleDateFormat("HH", Locale.KOREA).format(cal.time) + "30"

        val url = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst" +
            "?serviceKey=$SERVICE_KEY&pageNo=1&numOfRows=60&dataType=JSON" +
            "&base_date=$baseDate&base_time=$baseTime&nx=$NX&ny=$NY"
        parseForecast(get(url) ?: return@withContext null)
    }

    private fun parseForecast(body: String): Triple<Int?, Int, Int>? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val items = root.optJSONObject("response")
            ?.optJSONObject("body")
            ?.optJSONObject("items")
            ?.optJSONArray("item") ?: return null

        // 카테고리별로 fcstTime 이 가장 이른 것 = 지금에 가장 가까운 예보
        val earliest = HashMap<String, Pair<String, String>>()   // category → (fcstTime, value)
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val cat = o.optString("category")
            if (cat != "T1H" && cat != "SKY" && cat != "PTY") continue
            val time = o.optString("fcstTime")
            val prev = earliest[cat]
            if (prev == null || time < prev.first) earliest[cat] = time to o.optString("fcstValue")
        }
        if (earliest.isEmpty()) return null

        return Triple(
            earliest["T1H"]?.second?.toFloatOrNull()?.toInt(),
            earliest["SKY"]?.second?.toIntOrNull() ?: 1,
            earliest["PTY"]?.second?.toIntOrNull() ?: 0
        )
    }

    // ── 공용 ────────────────────────────────────────────────────

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun toJson(w: WeatherNow): String = JSONObject().apply {
        put("temp", w.tempC ?: JSONObject.NULL)
        put("sky", w.sky)
        put("pty", w.pty)
        put("warn", w.warning ?: JSONObject.NULL)
    }.toString()

    private fun fromJson(s: String): WeatherNow? = runCatching {
        val o = JSONObject(s)
        WeatherNow(
            tempC = if (o.isNull("temp")) null else o.optInt("temp"),
            sky = o.optInt("sky", 1),
            pty = o.optInt("pty", 0),
            warning = if (o.isNull("warn")) null else o.optString("warn").takeIf { it.isNotBlank() }
        )
    }.getOrNull()
}
