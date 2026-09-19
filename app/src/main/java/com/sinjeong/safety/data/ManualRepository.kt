package com.sinjeong.safety.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 비상대응 현장조치 매뉴얼 — 내려받기·보관·읽기.
 *
 * 왜 APK 에 넣지 않는가: 매뉴얼에는 직원 실명과 연락처가 들어 있다. 저장소·APK 에는
 * 실명을 두지 않는다는 규칙(CLAUDE.md)이 있어, 로그인한 직원만 Cloud Storage 에서
 * 받아 **기기 내부 저장소**(filesDir)에 둔다. 외부 저장소·공유 캐시는 쓰지 않는다.
 *
 * 터널에서도 열려야 하므로 한 번 받으면 통째로 오프라인 동작한다.
 * 그래서 교체는 "임시 폴더에 전부 받고 → 성공했을 때만 갈아끼우기" 로만 한다.
 * 중간에 끊겨 반쪽 자료가 남으면, 정작 급할 때 그림 없는 매뉴얼을 보게 된다.
 */
object ManualRepository {

    /** 규정 뷰어·검색에서 이 매뉴얼을 부르는 이름 (RegBook.name 으로도 쓴다) */
    const val BOOK_NAME = "비상대응 현장조치 매뉴얼"

    private const val DIR = "manual"
    private const val TMP_DIR = "manual.tmp"
    // 교체 도중에만 잠깐 생기는 옛 자료 폴더. 이름을 바꾸면 res/xml 의 백업 제외 규칙 두 파일도 고칠 것.
    private const val OLD_DIR = "manual.old"
    private const val JSON = "manual.json"
    private const val TODAY = "today.json"
    private const val IMAGES = "images"

    private const val PREF = "manual_prefs"
    private const val KEY_VERSION = "version"
    private const val KEY_EDITION = "edition"
    private const val KEY_VERIFIED_AT = "verified_at"

    // 받을 게 없어도 이만큼 지나면 직원 확인을 다시 한다 (아래 reverifyIfDue)
    private const val REVERIFY_MS = 7L * 24 * 60 * 60 * 1000

    // Storage 경로 / Firestore 안내 문서 — 서버 담당과 맞춘 계약
    private const val REMOTE_JSON = "manual/manual.json"
    private const val REMOTE_TODAY = "manual/today.json"   // 선택 파일 (오늘의 비상조치)
    private const val REMOTE_IMAGES = "manual/images/"
    private const val CONFIG_DOC = "manual"

    /** 화면이 그대로 그릴 수 있는 진행 상태 */
    data class SyncState(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val failed: Boolean = false,
        /** 직원 확인에서 걸렸다 (명단에 없는 계정). 다시 눌러도 소용없다는 뜻이라 따로 둔다. */
        val denied: Boolean = false
    ) {
        val percent: Int get() = if (total <= 0) 0 else (done * 100 / total).coerceIn(0, 100)
    }

    /** 내려받기 시도의 결말 */
    sealed class SyncResult {
        /** 받을 게 없었다 (최신이거나, 오프라인인데 기기 저장본이 있다) */
        object Unchanged : SyncResult()
        data class Updated(val edition: String) : SyncResult()
        /** 직원 확인 실패 — 기기 저장본도 지웠다 */
        object Denied : SyncResult()
        object Failed : SyncResult()
    }

    const val DENIED_MESSAGE = "명단에서 확인되지 않아 매뉴얼을 받을 수 없습니다"

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    // 앱 시작 시 한 번 + 매뉴얼 화면 진입 시 두 곳에서 부르므로 겹쳐 받지 않게 잠근다
    private val lock = Mutex()

    // 폴더 갈아끼우기·지우기·복구는 이 잠금 안에서만 한다. wipe 는 로그아웃 버튼에서 곧바로
    // 불리는 보통 함수라 위의 코루틴 Mutex 를 잡을 수 없다 — 파일 조작만 따로 잠근다.
    private val fileLock = Any()

    // wipe 할 때마다 올린다. 받는 도중에 로그아웃하면 이 값이 달라져 있으므로,
    // 다 받고 나서도 갈아끼우지 않고 버린다(세션 없는 기기에 실명 자료가 되살아나면 안 된다).
    @Volatile private var wipeGen = 0

    @Volatile private var cached: ManualDoc? = null
    @Volatile private var cachedToday: TodayManual? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun dir(context: Context) = File(context.filesDir, DIR)
    private fun jsonFile(context: Context) = File(dir(context), JSON)

    /** 그림 파일의 실제 위치 (Coil 로 로컬 로드할 때 쓴다) */
    fun imageFile(context: Context, file: String) = File(File(dir(context), IMAGES), file)

    /**
     * 갈아끼우는 도중에 앱이 죽었으면 옛 자료를 되살린다.
     * 교체는 manual → manual.old, manual.tmp → manual 두 번의 이름 바꾸기인데, 그 사이에
     * 죽으면 manual 은 없고 manual.old 만 남는다. 되살리지 않으면 터널에서 매뉴얼이 통째로 없다.
     */
    private fun recover(context: Context) {
        synchronized(fileLock) {
            val old = File(context.filesDir, OLD_DIR)
            if (old.exists()) {
                if (!jsonFile(context).exists() && File(old, JSON).exists()) {
                    dir(context).deleteRecursively()
                    old.renameTo(dir(context))
                } else {
                    old.deleteRecursively()
                }
            }
        }
    }

    fun isDownloaded(context: Context): Boolean {
        recover(context)
        return jsonFile(context).exists()
    }

    /** 기기에 저장된 판 이름 ("2026.9"). 아직 없으면 빈 값. */
    fun savedEdition(context: Context): String =
        prefs(context).getString(KEY_EDITION, "").orEmpty()

    /** 기기에 저장된 자료를 읽어 들인다. 없거나 깨졌으면 null. */
    suspend fun load(context: Context): ManualDoc? = withContext(Dispatchers.IO) {
        val f = jsonFile(context)
        // 파일부터 본다 — 캐시만 믿으면 로그아웃으로 지운 뒤에도 메모리의 실명 자료가 화면에 나온다
        if (!isDownloaded(context)) { cached = null; return@withContext null }
        cached?.let { return@withContext it }
        val gen = wipeGen
        val doc = runCatching { ManualJson.parseDoc(f.readText()) }
            .onFailure { Log.w("Manual", "매뉴얼 파싱 실패", it) }
            .getOrNull()
        // 읽는 사이에 지워졌으면 캐시에 올리지 않는다
        if (gen != wipeGen) return@withContext null
        cached = doc
        doc
    }

    /**
     * 오늘의 비상조치 자료. **선택 파일**이다 — 없거나 깨졌으면 null 이고,
     * 그래도 매뉴얼 뷰어·검색·AI 는 아무 영향 없이 돈다.
     */
    suspend fun loadToday(context: Context): TodayManual? = withContext(Dispatchers.IO) {
        if (!isDownloaded(context)) { cachedToday = null; return@withContext null }
        cachedToday?.let { return@withContext it }
        val f = File(dir(context), TODAY)
        if (!f.exists()) return@withContext null
        val gen = wipeGen
        val today = runCatching { ManualJson.parseToday(f.readText()) }
            .onFailure { Log.w("Manual", "today.json 파싱 실패", it) }
            .getOrNull()
        if (gen != wipeGen) return@withContext null
        cachedToday = today
        today
    }

    // ── 직원 확인 ───────────────────────────────────────────────
    /**
     * Storage 의 `manual/` 은 커스텀 클레임 `crew == true` 를 가진 계정만 읽는다.
     * 그 클레임은 서버가 붙여 주므로, 확인을 받은 뒤 **토큰을 강제로 새로 받아야**
     * 방금 붙은 클레임이 Storage 요청에 실려 간다(캐시된 토큰에는 없다).
     *
     * 돌려주는 값: true=확인됨 / false=명단에 없음 / null=확인 자체를 못 했다(통신 실패).
     * null 과 false 를 뭉뚱그리면 안 된다 — 터널에서 잠깐 못 물어본 것을
     * "퇴직자" 로 취급해 기기에 받아 둔 매뉴얼까지 지워 버린다.
     */
    private suspend fun verifyCrew(): Boolean? {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val ok = runCatching {
            val res = com.google.firebase.functions.FirebaseFunctions
                .getInstance("asia-northeast3")
                .getHttpsCallable("verifyCrew").call().await()
            @Suppress("UNCHECKED_CAST")
            (res.data as? Map<String, Any?>)?.get("ok") as? Boolean
        }.getOrElse {
            Log.w("Manual", "verifyCrew 호출 실패", it)
            return null
        }
        // 응답에 ok 가 없거나 Boolean 이 아니면 "명단에 없음"이 아니라 "확인 못 함"이다.
        // false 로 뭉개면 서버가 한 번 이상한 답을 줬을 때 멀쩡한 직원의 매뉴얼이 지워진다.
        if (ok == null) { Log.w("Manual", "verifyCrew 응답 모양이 다르다"); return null }
        if (!ok) return false
        return runCatching { user.getIdToken(true).await(); true }.getOrElse {
            Log.w("Manual", "토큰 갱신 실패", it)
            null
        }
    }

    /** 권한 때문에 막힌 것인가 (토큰에 클레임이 아직 없을 때 나온다) */
    private fun isPermissionError(e: Throwable): Boolean {
        val se = e as? com.google.firebase.storage.StorageException ?: return false
        return se.httpResultCode == 403 ||
            se.errorCode == com.google.firebase.storage.StorageException.ERROR_NOT_AUTHORIZED
    }

    // ── 내려받기 ────────────────────────────────────────────────
    /**
     * 필요할 때만 받는다. 실패는 조용히 넘긴다 — 터널·비행기 모드에서 앱을 막을 이유가 없다.
     * 화면은 [sync] 를 보고 "다시 받기" 를 내밀면 된다.
     */
    suspend fun syncIfNeeded(context: Context, force: Boolean = false): SyncResult = lock.withLock {
        withContext(Dispatchers.IO) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: return@withContext SyncResult.Unchanged
            val gen = wipeGen

            val local = prefs(context).getLong(KEY_VERSION, -1L)
            val remote = runCatching {
                FirebaseFirestore.getInstance()
                    .collection("config").document(CONFIG_DOC).get().await()
            }.getOrNull()
            val remoteVersion = remote?.getLong("version") ?: -1L
            val remoteEdition = remote?.getString("edition").orEmpty()

            val have = isDownloaded(context)
            val needed = force || !have || (remoteVersion > local)
            if (!needed) return@withContext reverifyIfDue(context)
            // 서버 안내 문서를 못 읽었는데 이미 받아 둔 게 있으면 그대로 쓴다
            if (remote == null && have && !force) return@withContext SyncResult.Unchanged

            when (verifyCrew()) {
                false -> {
                    wipe(context)
                    _sync.value = SyncState(denied = true)
                    return@withContext SyncResult.Denied
                }
                null -> {
                    // 물어보지 못했을 뿐이다. 있는 자료는 그대로 두고 다음 기회에 다시 시도한다.
                    if (have) return@withContext SyncResult.Unchanged
                    _sync.value = SyncState(failed = true)
                    return@withContext SyncResult.Failed
                }
                true -> Unit
            }

            _sync.value = SyncState(running = true, done = 0, total = 1)
            val tmp = File(context.filesDir, TMP_DIR)
            try {
                val doc = try {
                    downloadAll(tmp)
                } catch (e: Exception) {
                    // 방금 붙은 클레임이 토큰에 아직 없을 수 있다. **한 번만** 다시 받아 재시도한다
                    // (두 번째도 403 이면 그대로 실패로 끝난다 — 여기서 돌지 않는다).
                    if (!isPermissionError(e)) throw e
                    Log.w("Manual", "Storage 권한 오류 — 토큰 갱신 후 1회 재시도", e)
                    if (verifyCrew() != true) throw e
                    downloadAll(tmp)
                }

                // 여기까지 왔으면 전부 성공 — 이제서야 갈아끼운다
                val swapped = synchronized(fileLock) {
                    // 받는 사이에 로그아웃했거나 다른 계정이 됐으면 버린다
                    if (gen != wipeGen || FirebaseAuth.getInstance().currentUser?.uid != uid) {
                        return@synchronized false
                    }
                    // 옛 폴더를 지우고 나서 옮기면, 그 사이에 죽거나 옮기기가 실패했을 때
                    // 기기에 아무것도 안 남는다. 옆으로 치워 두고 → 새것을 들이고 → 그다음에 지운다.
                    val target = dir(context)
                    val old = File(context.filesDir, OLD_DIR)
                    old.deleteRecursively()
                    if (target.exists() && !target.renameTo(old)) {
                        throw IllegalStateException("옛 매뉴얼 폴더를 치우지 못했다")
                    }
                    if (!tmp.renameTo(target)) {
                        old.renameTo(target)   // 되돌린다
                        throw IllegalStateException("매뉴얼 폴더 교체 실패")
                    }
                    old.deleteRecursively()
                    cached = doc
                    cachedToday = null         // 다음 loadToday 가 새 파일을 읽는다
                    true
                }
                if (!swapped) {
                    tmp.deleteRecursively()
                    _sync.value = SyncState()
                    return@withContext SyncResult.Unchanged
                }

                val edition = remoteEdition.ifBlank { doc.edition }
                prefs(context).edit()
                    .putLong(KEY_VERSION, if (remoteVersion >= 0) remoteVersion else local + 1)
                    .putString(KEY_EDITION, edition)
                    .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
                    .apply()
                _sync.value = _sync.value.copy(running = false, failed = false, denied = false)
                SyncResult.Updated(edition)
            } catch (e: Exception) {
                Log.w("Manual", "매뉴얼 내려받기 실패", e)
                tmp.deleteRecursively()
                // 로그아웃으로 끊긴 것이면 실패 표시를 남기지 않는다
                _sync.value = if (gen != wipeGen) SyncState() else SyncState(running = false, failed = true)
                SyncResult.Failed
            }
        }
    }

    /**
     * 받을 게 없을 때도 가끔은 직원 확인을 다시 한다.
     * 확인은 "내려받을 때"만 했는데, 그러면 이미 받아 둔 퇴직자 기기는 새 판이 나올 때까지
     * 실명이 든 자료를 계속 들고 있다(퇴직 차단은 **다시 로그인할 때**만 걸린다).
     * 통신 실패(null)는 아무것도 하지 않는다 — 터널에서 매뉴얼이 사라지면 안 된다.
     */
    private suspend fun reverifyIfDue(context: Context): SyncResult {
        val last = prefs(context).getLong(KEY_VERIFIED_AT, 0L)
        val now = System.currentTimeMillis()
        if (now - last in 0 until REVERIFY_MS) return SyncResult.Unchanged
        return when (verifyCrew()) {
            false -> {
                wipe(context)
                _sync.value = SyncState(denied = true)
                SyncResult.Denied
            }
            true -> {
                prefs(context).edit().putLong(KEY_VERIFIED_AT, now).apply()
                SyncResult.Unchanged
            }
            null -> SyncResult.Unchanged
        }
    }

    /** 임시 폴더에 manual.json + 그림 전부. 하나라도 실패하면 예외를 그대로 올린다. */
    private suspend fun downloadAll(tmp: File): ManualDoc {
        tmp.deleteRecursively()
        tmp.mkdirs()
        // 버킷 이름을 적지 않는다 — 로컬 google-services.json 은 껍데기라 값이 틀리고,
        // 진짜 값은 CI 가 넣어 준다. 기본 버킷을 그대로 쓰면 둘 다 맞는다.
        val storage = FirebaseStorage.getInstance().reference

        val tmpJson = File(tmp, JSON)
        storage.child(REMOTE_JSON).getFile(tmpJson).await()
        val doc = ManualJson.parseDoc(tmpJson.readText())

        // 오늘의 비상조치(선택). 서버에 **없는 것**만 눈감아 준다 — 통신이 끊긴 것까지 넘기면
        // today.json 만 빠진 채로 "받기 완료"가 되어 다음 판이 나올 때까지 카드가 안 나온다.
        val tmpToday = File(tmp, TODAY)
        try {
            storage.child(REMOTE_TODAY).getFile(tmpToday).await()
            // 깨진 파일은 두지 않는다 (매뉴얼은 그대로 쓴다)
            runCatching { ManualJson.parseToday(tmpToday.readText()) }
                .onFailure { Log.w("Manual", "today.json 이 깨져 있어 버린다", it); tmpToday.delete() }
        } catch (e: com.google.firebase.storage.StorageException) {
            if (e.errorCode != com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            tmpToday.delete()
        }

        // imageFiles 는 이름 화이트리스트를 통과한 것만 준다 (ManualFormat.isSafeImageName)
        val files = doc.sections.flatMap { it.imageFiles }.distinct()
        val total = files.size + 1
        _sync.value = SyncState(running = true, done = 1, total = total)

        val imgDir = File(tmp, IMAGES)
        imgDir.mkdirs()
        files.forEachIndexed { i, name ->
            storage.child(REMOTE_IMAGES + name).getFile(File(imgDir, name)).await()
            _sync.value = SyncState(running = true, done = i + 2, total = total)
        }
        return doc
    }

    /**
     * 기기에서 지운다. 로그아웃·퇴직 차단 때 부른다 —
     * 실명이 든 자료를 로그인하지 않은 기기에 남겨 두지 않는다.
     */
    fun wipe(context: Context) {
        synchronized(fileLock) {
            wipeGen++
            cached = null
            cachedToday = null
            runCatching {
                dir(context).deleteRecursively()
                File(context.filesDir, TMP_DIR).deleteRecursively()
                File(context.filesDir, OLD_DIR).deleteRecursively()
                prefs(context).edit().clear().apply()
            }
        }
        _sync.value = SyncState()
    }

    // ── 검색 엔진에 태우기 ──────────────────────────────────────
    /**
     * 매뉴얼을 규정집 한 권처럼 꾸며 [RegulationSearch] 에 그대로 넘긴다.
     * 범위 제한(규정만/비상조치만/전체)은 search 에 넘기는 책 목록을 거르는 것으로 한다 —
     * 검색 함수에 스코프 인자를 새로 만들 이유가 없다.
     */
    fun asBook(doc: ManualDoc): RegBook = RegBook(
        name = BOOK_NAME,
        icon = "🚨",
        bgColor = 0xFFFFEDE6,
        subtitle = "비상대응 현장조치 · 직원 전용",
        hasOriginalFile = false,
        // 그릴 것이 하나도 없는 섹션은 검색에도 올리지 않는다(눌렀더니 빈 화면). 순서는 배열 순서 그대로.
        articles = doc.visibleSections.map { s ->
            RegArticle(
                num = ManualFormat.situationTag(s),
                title = s.title,
                body = ManualFormat.plainText(s),
                sid = s.id
            )
        }
    )
}
