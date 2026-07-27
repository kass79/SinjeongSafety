package com.sinjeong.safety.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** 규정 조문 하나 */
data class RegArticle(
    val num: String,      // 제○조
    val title: String,    // 제목
    val body: String      // 본문 전문
)

/** 규정집 하나 (예: 운전취급규정) */
data class RegBook(
    val name: String,
    val icon: String,
    val bgColor: Long,
    val subtitle: String,
    val hasOriginalFile: Boolean,        // 원본 hwpx 다운로드 여부
    val articles: List<RegArticle>
)

/** 오늘의 규정 (친근한 해설 포함) */
data class TodayReg(
    val reg: String,
    val num: String,
    val title: String,
    val emoji: String,
    val fun_: String,
    val body: String
)

/**
 * assets에서 규정 데이터를 읽어오는 로더.
 * 앱 시작 시 한 번 로드해서 메모리에 캐시.
 */
object RegulationRepository {

    // 규정집 표시 순서·메타데이터 (고정 4권)
    private val bookMeta = listOf(
        BookMeta("전동차승무원업무예규", "🚇", 0xFFE9F1FD, "우리 승무원 필수", false),
        BookMeta("운전취급규정", "🛤️", 0xFFE9F6ED, "원본 파일 포함(신호기 그림)", true),
        BookMeta("인사규정", "👤", 0xFFFDF0E3, "임용·승진·전보", false),
        BookMeta("취업규칙", "📋", 0xFFFBF4DC, "근무·휴가·복무", false)
    )

    private data class BookMeta(
        val name: String, val icon: String, val bg: Long,
        val subtitle: String, val hasFile: Boolean
    )

    @Volatile private var cachedBooks: List<RegBook>? = null
    @Volatile private var cachedToday: List<TodayReg>? = null

    suspend fun loadBooks(context: Context): List<RegBook> = withContext(Dispatchers.IO) {
        cachedBooks?.let { return@withContext it }
        val json = context.assets.open("regulations.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val books = bookMeta.mapNotNull { meta ->
            val arr = root.optJSONArray(meta.name) ?: return@mapNotNull null
            val articles = ArrayList<RegArticle>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                articles.add(RegArticle(o.getString("n"), o.getString("t"), o.getString("b")))
            }
            RegBook(meta.name, meta.icon, meta.bg, meta.subtitle, meta.hasFile, articles)
        }
        cachedBooks = books
        books
    }

    suspend fun loadToday(context: Context): List<TodayReg> = withContext(Dispatchers.IO) {
        cachedToday?.let { return@withContext it }
        val json = context.assets.open("today_regulations.json")
            .bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        val list = ArrayList<TodayReg>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                TodayReg(
                    reg = o.getString("reg"),
                    num = o.getString("num"),
                    title = o.getString("title"),
                    emoji = o.getString("emoji"),
                    fun_ = o.getString("fun"),
                    body = o.getString("body")
                )
            )
        }
        cachedToday = list
        list
    }

    /** 전체 조문 수 */
    suspend fun totalCount(context: Context): Int =
        loadBooks(context).sumOf { it.articles.size }

    /** 전체 규정집에서 키워드 검색 */
    suspend fun search(context: Context, query: String): List<Pair<String, RegArticle>> {
        if (query.isBlank()) return emptyList()
        val books = loadBooks(context)
        val result = ArrayList<Pair<String, RegArticle>>()
        for (book in books) {
            for (a in book.articles) {
                if (a.title.contains(query, true) || a.body.contains(query, true)) {
                    result.add(book.name to a)
                }
            }
        }
        return result
    }

    /**
     * 오늘의 규정 — 평일만 로테이션 (주말은 null 반환).
     * 2026-01-01부터 평일 수를 세어 인덱스로 사용 → 매 평일 다른 규정.
     */
    suspend fun todayRegulation(context: Context): TodayReg? {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return null

        val pool = loadToday(context)
        if (pool.isEmpty()) return null

        val start = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var count = 0
        val cur = start.clone() as Calendar
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (cur.before(today)) {
            val d = cur.get(Calendar.DAY_OF_WEEK)
            if (d != Calendar.SATURDAY && d != Calendar.SUNDAY) count++
            cur.add(Calendar.DAY_OF_MONTH, 1)
        }
        return pool[count % pool.size]
    }

    fun isWeekend(): Boolean {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    // ─────────────────────────────────────────────
    // 즐겨찾기 / 최근 본 조문 (기기 로컬 저장, 서버 불필요)
    // 저장 형식: "규정집이름|제○조" 를 줄바꿈으로 이어붙임
    // ─────────────────────────────────────────────

    private const val PREF_NAME = "regulation_prefs"
    private const val KEY_RECENT = "recent_articles"
    private const val KEY_FAVORITE = "favorite_articles"
    private const val RECENT_MAX = 8

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun keyOf(book: String, num: String) = "$book|$num"

    private fun readKeys(context: Context, key: String): List<String> =
        prefs(context).getString(key, "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun writeKeys(context: Context, key: String, list: List<String>) {
        prefs(context).edit().putString(key, list.joinToString("\n")).apply()
    }

    /** 조문을 열 때 호출. 최근 목록 맨 앞으로 올리고 중복 제거. */
    fun addRecent(context: Context, book: String, num: String) {
        val k = keyOf(book, num)
        val updated = (listOf(k) + readKeys(context, KEY_RECENT).filter { it != k }).take(RECENT_MAX)
        writeKeys(context, KEY_RECENT, updated)
    }

    fun isFavorite(context: Context, book: String, num: String): Boolean =
        readKeys(context, KEY_FAVORITE).contains(keyOf(book, num))

    /** 별표 토글. 토글 후 즐겨찾기 상태를 반환. */
    fun toggleFavorite(context: Context, book: String, num: String): Boolean {
        val k = keyOf(book, num)
        val cur = readKeys(context, KEY_FAVORITE)
        return if (cur.contains(k)) {
            writeKeys(context, KEY_FAVORITE, cur.filter { it != k })
            false
        } else {
            writeKeys(context, KEY_FAVORITE, listOf(k) + cur)
            true
        }
    }

    /** 저장된 키를 실제 조문으로 되살린다. 삭제·개정으로 못 찾는 항목은 조용히 건너뛴다. */
    private suspend fun resolve(context: Context, keys: List<String>): List<Pair<String, RegArticle>> {
        if (keys.isEmpty()) return emptyList()
        val books = loadBooks(context)
        return keys.mapNotNull { k ->
            val parts = k.split("|")
            if (parts.size != 2) return@mapNotNull null
            val book = books.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val article = book.articles.firstOrNull { it.num == parts[1] } ?: return@mapNotNull null
            book.name to article
        }
    }

    suspend fun getRecentArticles(context: Context): List<Pair<String, RegArticle>> =
        resolve(context, readKeys(context, KEY_RECENT))

    suspend fun getFavoriteArticles(context: Context): List<Pair<String, RegArticle>> =
        resolve(context, readKeys(context, KEY_FAVORITE))
}
