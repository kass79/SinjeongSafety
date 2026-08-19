package com.sinjeong.safety.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 기상청 기상특보 (공공데이터포털)
 *
 * 서울(stnId=109)에 발효 중인 특보를 읽어 "폭염경보 · 호우주의보" 같은
 * 짧은 문구로 돌려준다. 홈 화면 상단 배지에 쓴다.
 *
 * 설계 원칙
 * - 실패하면 null. 배지가 안 뜰 뿐 앱은 평소대로 동작한다.
 * - 호출량 보호: 1시간에 한 번만 실제 조회하고 그 사이엔 저장값을 쓴다.
 *   (일반 인증키 하루 한도 안에서 전 직원이 써도 안전하도록)
 */
object WeatherRepository {

    // 공공데이터포털 일반 인증키 (이미 URL 인코딩된 형태 그대로 사용해야 한다.
    // 다시 인코딩하면 %2B가 %252B로 바뀌어 인증에 실패한다)
    private const val SERVICE_KEY =
        "XMXiJ0ib%2BxhyschjrOEKEH%2BLiBVAOtF9twd2uxLnqsmJpcc4CHQINyj%2Fs9P3HVn9IjVM3q3ROhpLUEga28xQSw%3D%3D"

    private const val STN_SEOUL = "109"
    private const val CACHE_MS = 60 * 60 * 1000L   // 1시간

    private const val PREF = "safety_prefs"
    private const val KEY_TEXT = "weather_warning_text"
    private const val KEY_TIME = "weather_warning_time"

    /** 발효 중 특보 문구. 없거나 조회 실패면 null */
    suspend fun getWarningText(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong(KEY_TIME, 0L)
        if (System.currentTimeMillis() - cachedAt < CACHE_MS) {
            return prefs.getString(KEY_TEXT, null)?.takeIf { it.isNotBlank() }
        }

        val text = runCatching { fetch() }.getOrNull()
        prefs.edit()
            .putString(KEY_TEXT, text ?: "")
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
        return text
    }

    private suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        val url = "https://apis.data.go.kr/1360000/WthrWrnInfoService/getWthrWrnMsg" +
            "?serviceKey=$SERVICE_KEY&pageNo=1&numOfRows=10&dataType=JSON&stnId=$STN_SEOUL"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            parseWarnings(body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 응답에서 특보 현황 텍스트를 찾아 "폭염경보 · 호우주의보"처럼 정리한다.
     * 기상청 응답 형식이 바뀌어도 죽지 않도록 전부 opt로 더듬는다.
     */
    private fun parseWarnings(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val items = root.optJSONObject("response")
            ?.optJSONObject("body")
            ?.optJSONObject("items")
            ?.optJSONArray("item") ?: return null
        if (items.length() == 0) return null

        // 가장 최근 통보문에서 특보 종류를 추출한다
        val first = items.optJSONObject(0) ?: return null
        val allText = buildString {
            for (key in first.keys()) append(first.optString(key)).append(' ')
        }

        val found = Regex("(폭염|호우|대설|강풍|태풍|한파|건조|황사|폭풍해일)(경보|주의보)")
            .findAll(allText)
            .map { it.value }
            .distinct()
            .toList()

        return if (found.isEmpty()) null else found.joinToString(" · ")
    }
}
