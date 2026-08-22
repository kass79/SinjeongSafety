package com.sinjeong.safety.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * 날씨 (기상청 / 공공데이터포털)
 *
 * 홈 헤더의 작은 칩에 쓸 두 가지를 모은다.
 * - 현재 시/도에 "지금 발효 중인" 기상특보 (WthrWrnInfoService/getPwnStatus)
 *   위치 옵션이 꺼져 있거나 지역명을 못 얻으면 서울 기준
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
    val warning: String?,   // 그 지역에 발효 중인 특보. 없으면 null
    // 어느 지점 기준인지. "현재 위치" 또는 "신정동".
    // 설정을 켜도 위치를 못 얻으면 조용히 신정동으로 떨어지는데, 그때 화면이
    // 계속 "현재 위치"라고 우기면 사용자가 엉뚱한 지역 날씨로 오해한다.
    val placeLabel: String = "신정동",
    // 특보를 어느 시/도 기준으로 골랐는지 ("서울", "경기도" …).
    // 위치를 안 쓰거나 지역명을 못 얻으면 지금까지처럼 서울이다.
    val warningArea: String = "서울"
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
    // 캐시 키를 v3로 새로 판 이유: v2 저장값에는 격자(nx·ny)가 없다. 현재 위치 기능이
    // 생긴 뒤로는 "언제 받았나"뿐 아니라 "어디 격자로 받았나"까지 맞아야 재사용할 수 있는데,
    // 같은 키를 재사용하면 격자 없는 옛 값이 신정동 것으로 오인돼 1시간 동안 살아남는다.
    // 캐시 키를 v4로 새로 판 이유: v3 저장값에는 특보 지역(warningArea)이 없다. 특보가
    // 서울 고정에서 현재 위치의 시/도 기준으로 바뀌었으므로, 같은 키를 재사용하면 지역이
    // 빠진 옛 값이 "서울" 로 되살아나 한 시간 동안 엉뚱한 지역 특보를 보여 준다.
    private const val KEY_JSON = "weather_now_v4"
    private const val KEY_TIME = "weather_now_v4_time"

    /**
     * 특보 + 기온. 1시간 캐시. 전부 실패하면 null
     *
     * @param useLocation 설정에서 "현재 위치 날씨"를 켰는지. 켰어도 권한이 없거나
     *   마지막 위치를 못 얻으면 조용히 신정동 격자로 떨어진다(날씨가 안 뜨는 일은 없어야 한다).
     */
    suspend fun getWeather(context: Context, useLocation: Boolean): WeatherNow? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // 쓸 격자를 먼저 정한다. 캐시 유효성 판정에 격자가 필요하기 때문이다.
        val loc = if (useLocation) lastKnownLocation(context) else null
        val (nx, ny) = if (loc != null) latLonToGrid(loc.latitude, loc.longitude) else NX to NY
        val label = if (loc != null) "현재 위치" else "신정동"

        // 시간이 남았어도 격자가 다르면 버린다. 안 그러면 스위치를 켠 뒤에도
        // 한 시간 동안 옛 지역 날씨가 그대로 보인다.
        if (System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0L) < CACHE_MS) {
            val cached = prefs.getString(KEY_JSON, null)
            if (cached != null) fromJson(cached, nx, ny)?.let { return it }
        }

        // 특보도 지금 있는 시/도 기준으로 본다. 좌표에서 시도 이름을 못 얻으면 서울로 떨어진다.
        // 지오코딩은 캐시가 비었을 때만 — 1시간에 한 번이면 충분하다.
        val area = (if (loc != null) adminAreaOf(context, loc.latitude, loc.longitude) else null)
            ?: "서울"

        // 둘 중 하나가 죽어도 나머지는 살린다
        val warning = runCatching { fetchWarning(area) }.getOrNull()
        val fcst = runCatching { fetchForecast(nx, ny) }.getOrNull()
        if (warning == null && fcst == null) return null

        val now = WeatherNow(
            tempC = fcst?.first,
            sky = fcst?.second ?: 1,
            pty = fcst?.third ?: 0,
            warning = warning,
            placeLabel = label,
            warningArea = area
        )
        prefs.edit()
            .putString(KEY_JSON, toJson(now, nx, ny))
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
        return now
    }

    // ── 위치 → 격자 ─────────────────────────────────────────────

    /**
     * 위경도 → 기상청 격자(nx, ny). 기상청이 공개한 Lambert Conformal Conic 변환 그대로다.
     *
     * 순수 함수로 둔 이유: 이 식은 한 글자만 틀려도 엉뚱한 지역 날씨가 조용히 뜨므로
     * 손으로 검증할 수 있어야 한다. (서울시청 37.5665,126.9780 → 60,127)
     */
    internal fun latLonToGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val re = 6371.00877 / 5.0        // 지구 반경 / 격자 간격(km)
        val degrad = PI / 180.0
        val slat1 = 30.0 * degrad
        val slat2 = 60.0 * degrad
        val olon = 126.0 * degrad
        val olat = 38.0 * degrad

        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        var ra = tan(PI * 0.25 + lat * degrad * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = lon * degrad - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = floor(ra * sin(theta) + 43 + 0.5).toInt()
        val ny = floor(ro - ra * cos(theta) + 136 + 0.5).toInt()
        return nx to ny
    }

    /**
     * 다른 앱들이 이미 잡아둔 "마지막 위치"만 한 번 읽어 격자로 바꾼다. 못 얻으면 null.
     *
     * requestLocationUpdates / requestSingleUpdate 는 쓰지 않는다 — 위치를 새로 잡으면
     * 배터리를 쓴다. 날씨 조회 자체가 1시간에 한 번이라 최신 위치일 필요도 없다.
     */
    private fun lastKnownLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // 세 공급자 중 가장 최근에 갱신된 것을 고른다. 껐다 켠 GPS 등으로
            // 한참 묵은 값이 섞여 있을 수 있어서 그냥 첫 번째를 쓰면 안 된다.
            listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                LocationManager.GPS_PROVIDER
            ).mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull()   // SecurityException 등은 삼키고 신정동으로 떨어진다
    }

    /**
     * 좌표 → 시/도 짧은 이름("서울", "경기도"). 못 얻으면 null → 호출자가 서울로 떨어진다.
     *
     * 안드로이드 기본 Geocoder 만 쓴다(새 라이브러리 없음). API 33+ 에서 동기
     * getFromLocation 은 deprecated 지만 동작하며, 콜백 버전을 쓰자고 코드를 두 벌
     * 만들 값어치가 없다. 네트워크를 탈 수 있으므로 IO 로 넘긴다.
     */
    @Suppress("DEPRECATION")
    private suspend fun adminAreaOf(context: Context, lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(context, Locale.KOREA).getFromLocation(lat, lon, 1)
                    ?.firstOrNull()?.adminArea
            }.getOrNull()?.let { normalizeArea(it) }
        }

    /**
     * "서울특별시" → "서울", "강원특별자치도" → "강원". t6 의 지역 표기가 짧은 형태라
     * 접미사를 떼어 맞춰 준다. "경기도"처럼 이미 짧은 것은 그대로 둔다.
     *
     * 순수 함수 — 손으로 검증할 수 있어야 한다.
     */
    internal fun normalizeArea(adminArea: String?): String? {
        val s = adminArea?.trim().orEmpty()
        if (s.isBlank()) return null
        // 긴 접미사부터 떼야 "특별자치도"가 "특별시"·"자치도"로 어긋나 잘리지 않는다
        val n = s.removeSuffix("특별자치시").removeSuffix("특별자치도")
            .removeSuffix("특별시").removeSuffix("광역시")
        return n.takeIf { it.isNotBlank() }
    }

    // ── 기상특보 현황 ────────────────────────────────────────────

    private suspend fun fetchWarning(area: String): String? = withContext(Dispatchers.IO) {
        val url = "https://apis.data.go.kr/1360000/WthrWrnInfoService/getPwnStatus" +
            "?serviceKey=$SERVICE_KEY&pageNo=1&numOfRows=1&dataType=JSON&stnId=$STN_SEOUL"
        parseWarningFor(get(url)?.let { extractT6(it) }, area)
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
     * t6 파싱 — 주어진 시/도에 발효 중인 특보만 골라 "폭염주의보 · 호우주의보" 로 묶는다.
     *
     * t6 실제 형식:
     *   o 폭염경보 : 전라남도(광양, 순천), 광주, 울산(울산서부)
     *   o 폭염주의보 : 충청남도(공주), 대전, 대구, 부산
     * 발효 중인 게 없으면 "o 없 음" / "o 없음" 같은 값이 온다.
     *
     * 순수 함수로 빼 둔 이유: 실제 응답 문자열로 눈이 아니라 손으로 검증할 수 있어야 한다.
     */
    internal fun parseWarningFor(t6: String?, area: String): String? {
        if (t6.isNullOrBlank() || area.isBlank()) return null
        val found = t6.lines()
            .map { it.trim() }
            .filter { it.startsWith("o ") || it.startsWith("o\t") }
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx < 0) return@mapNotNull null
                val kind = line.substring(2, idx).trim()          // "o " 다음부터 ':' 앞까지
                val areas = line.substring(idx + 1)
                if (kind.endsWith("경보") || kind.endsWith("주의보")) {
                    if (areaMatches(areas, area)) kind else null
                } else null
            }
            .distinct()
        return if (found.isEmpty()) null else found.joinToString(" · ")
    }

    /**
     * "경기도(안산, 고양), 인천" 같은 지역 목록에 area 가 들어 있는가.
     *
     * 괄호 안은 세부 구역이라 떼어내고 시/도 이름만 남긴 뒤, **양방향 부분일치**로 본다.
     * t6 는 "전북자치도", Geocoder 는 "전북특별자치도"→"전북" 처럼 길이가 서로 달라서
     * 한 방향만 보면 놓친다. 한 글자 토큰은 우연히 걸리므로 제외한다.
     */
    private fun areaMatches(areas: String, area: String): Boolean =
        areas.split(',')
            .map { it.substringBefore('(').replace(")", "").trim() }
            .any { it.length >= 2 && (it.contains(area) || area.contains(it)) }

    // ── 초단기예보(기온·하늘·강수) ───────────────────────────────

    /** Triple(기온, SKY, PTY). 하나라도 못 읽으면 그 자리는 기본값 */
    private suspend fun fetchForecast(nx: Int, ny: Int): Triple<Int?, Int, Int>? = withContext(Dispatchers.IO) {
        // 초단기예보는 매시 30분 발표분이 45분에 풀린다.
        // 현재시각 -1시간의 시(HH)를 쓰면 그 슬롯은 항상 존재한다(날짜 넘어감은 Calendar가 처리).
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -1) }
        val baseDate = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(cal.time)
        val baseTime = SimpleDateFormat("HH", Locale.KOREA).format(cal.time) + "30"

        val url = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst" +
            "?serviceKey=$SERVICE_KEY&pageNo=1&numOfRows=60&dataType=JSON" +
            "&base_date=$baseDate&base_time=$baseTime&nx=$nx&ny=$ny"
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

    private fun toJson(w: WeatherNow, nx: Int, ny: Int): String = JSONObject().apply {
        put("temp", w.tempC ?: JSONObject.NULL)
        put("sky", w.sky)
        put("pty", w.pty)
        put("warn", w.warning ?: JSONObject.NULL)
        put("place", w.placeLabel)
        put("warnArea", w.warningArea)
        put("nx", nx)
        put("ny", ny)
    }.toString()

    /** 저장된 격자가 이번에 쓸 격자와 다르면 null — 호출자가 새로 받는다 */
    private fun fromJson(s: String, nx: Int, ny: Int): WeatherNow? = runCatching {
        val o = JSONObject(s)
        if (o.optInt("nx", -1) != nx || o.optInt("ny", -1) != ny) return@runCatching null
        WeatherNow(
            tempC = if (o.isNull("temp")) null else o.optInt("temp"),
            sky = o.optInt("sky", 1),
            pty = o.optInt("pty", 0),
            warning = if (o.isNull("warn")) null else o.optString("warn").takeIf { it.isNotBlank() },
            placeLabel = o.optString("place").takeIf { it.isNotBlank() } ?: "신정동",
            warningArea = o.optString("warnArea").takeIf { it.isNotBlank() } ?: "서울"
        )
    }.getOrNull()
}
