package com.sinjeong.safety

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.provider.OpenableColumns
import com.sinjeong.safety.data.Attachment
import com.sinjeong.safety.data.Comment
import com.sinjeong.safety.data.ConfirmReport
import com.sinjeong.safety.data.CrewConfirm
import com.sinjeong.safety.data.LinkAttachment
import com.sinjeong.safety.data.Post
import com.sinjeong.safety.data.effectiveDate
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.messaging.FirebaseMessaging
import com.sinjeong.safety.data.AiRepository
import com.sinjeong.safety.data.Answer
import com.sinjeong.safety.data.Briefing
import com.sinjeong.safety.data.BriefingRepository
import com.sinjeong.safety.data.CrewRepository
import com.sinjeong.safety.data.Question
import com.sinjeong.safety.data.QuestionRepository
import com.sinjeong.safety.data.PostRepository
import com.sinjeong.safety.data.WeatherNow
import com.sinjeong.safety.data.WeatherRepository
import com.sinjeong.safety.data.Tags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class UiMessage(val text: String, val isError: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PostRepository()
    private val crewRepo = CrewRepository()
    private val questionRepo = QuestionRepository()
    private val briefingRepo = BriefingRepository()
    private val aiRepo = AiRepository()
    private val appContext: Context = app.applicationContext
    private val prefs = app.getSharedPreferences("safety_prefs", Context.MODE_PRIVATE)

    // ── 원본 데이터 ──────────────────────────────────────────────
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    // 질의응답 목록 (최신순 — 저장소가 이미 정렬해서 준다)
    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions.asStateFlow()

    // 출무점호 목록 (최신 날짜부터 — 저장소가 문서 id 순으로 정렬해서 준다).
    // 화면에서는 첫 번째가 곧 최신 한 건이므로 '오늘 것' 필드는 따로 두지 않는다.
    private val _briefings = MutableStateFlow<List<Briefing>>(emptyList())
    val briefings: StateFlow<List<Briefing>> = _briefings.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }
    /** 화면에서 직접 안내를 띄울 때 (입력 검증 등) */
    fun showMessage(text: String, isError: Boolean = false) {
        _message.value = UiMessage(text, isError)
    }

    // ── 필터 상태 ────────────────────────────────────────────────
    private val _selectedCategory = MutableStateFlow<String?>(null)   // null = 전체 카테고리
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── 로그인 / 읽음 상태 ───────────────────────────────────────
    // 관리자 판정은 '로그인 여부'가 아니라 '관리자 계정인지'로 한다.
    // 승무원 계정도 Firebase 로그인 상태이므로, 예전처럼 isLoggedIn()을 쓰면
    // 승무원 전원에게 글쓰기·삭제 권한이 열린다.
    private val _isAdmin = MutableStateFlow(repo.adminEmail() == CrewRepository.ADMIN_EMAIL)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()
    val adminEmail: String? get() = repo.adminEmail()

    // ── 승무원 로그인 상태 ───────────────────────────────────────
    private val _crewEmpNo = MutableStateFlow(crewRepo.currentEmpNo())
    val crewEmpNo: StateFlow<String?> = _crewEmpNo.asStateFlow()

    private val _crewName = MutableStateFlow(prefs.getString("crew_name", null))
    val crewName: StateFlow<String?> = _crewName.asStateFlow()

    // 이 기기가 한 번이라도 사번+PIN 으로 확인된 적이 있는가 (기기 로컬 표식).
    // Firebase Auth 는 세션이 하나뿐이라 관리자로 로그인하면 승무원 세션이 대체되고,
    // 관리자 로그아웃 후에는 아무 계정도 없는 상태가 된다. 그때 로그인 게이트가 다시 뜨면
    // 승무원은 영문도 모른 채 처음부터 로그인해야 한다 — 그걸 막는 표식이다.
    private val _crewVerifiedOnce =
        MutableStateFlow(prefs.getBoolean("crew_verified_once", false))

    /** 사번+PIN 확인에 성공한 순간 표식을 남긴다 (등록·로그인 공통) */
    private fun markCrewVerified() {
        _crewVerifiedOnce.value = true
        prefs.edit().putBoolean("crew_verified_once", true).apply()
    }

    // ── 날씨 칩 (기온·하늘·기상특보) ────────────────────────────
    private val _weather = MutableStateFlow<WeatherNow?>(null)
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()

    // 현재 위치 기준으로 볼지 (기기별 저장, 기본 꺼짐 = 지금까지처럼 신정동 고정)
    private val _useLocationWeather =
        MutableStateFlow(prefs.getBoolean("weather_use_location", false))
    val useLocationWeather: StateFlow<Boolean> = _useLocationWeather.asStateFlow()

    /** 현재 위치 날씨 켜기/끄기. 한 시간 기다리게 하지 않도록 그 자리에서 다시 조회한다. */
    fun setUseLocationWeather(on: Boolean) {
        _useLocationWeather.value = on
        prefs.edit().putBoolean("weather_use_location", on).apply()
        _message.value = UiMessage(
            if (on) "현재 위치 기준으로 날씨를 보여드립니다" else "신정동 기준으로 날씨를 보여드립니다"
        )
        loadWeather()
    }

    /** 날씨 1회 조회. 실패하면 헤더 칩이 안 뜰 뿐 앱은 정상 */
    private fun loadWeather() {
        viewModelScope.launch {
            _weather.value = runCatching {
                WeatherRepository.getWeather(appContext, _useLocationWeather.value)
            }.getOrNull()
        }
    }

    // ── 알림 설정 (기기별 저장) ─────────────────────────────────
    private val _notificationsEnabled =
        MutableStateFlow(prefs.getBoolean("notify_new_posts", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    /** 새 글 알림 켜기/끄기. FCM 토픽 구독을 함께 바꾼다. */
    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notify_new_posts", enabled).apply()
        val fm = FirebaseMessaging.getInstance()
        if (enabled) fm.subscribeToTopic(TOPIC_NEW_POSTS) else fm.unsubscribeFromTopic(TOPIC_NEW_POSTS)
        _message.value = UiMessage(if (enabled) "새 글 알림을 켰습니다" else "새 글 알림을 껐습니다")
    }

    /** 로그인 강제 스위치. 서버에서 읽어오며, 못 읽으면 false로 둔다. */
    private val _requireLogin = MutableStateFlow(false)
    val requireLogin: StateFlow<Boolean> = _requireLogin.asStateFlow()

    /** 로그인 화면을 띄워야 하는가 (강제 ON + 승무원 미로그인 + 관리자도 아님 + 확인된 적 없는 기기) */
    val needCrewLogin: StateFlow<Boolean> =
        // 한 번이라도 사번+PIN 으로 확인된 기기라면, 관리자 로그아웃처럼 세션이 끊겨도 앱을 막지 않는다.
        // 로그인 게이트는 '처음 쓰는 기기'를 거르는 장치이지 매번 다시 로그인시키는 장치가 아니다.
        // 쓰기(확인·댓글·질문)는 여전히 로그인이 필요하다 — 보안 규칙이 서버에서 막는다.
        combine(_requireLogin, _crewEmpNo, _isAdmin, _crewVerifiedOnce) { require, empNo, admin, verified ->
            require && empNo == null && !admin && !verified
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _readIds = MutableStateFlow(
        prefs.getStringSet("read_ids", emptySet())?.toSet() ?: emptySet()
    )
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    // 확인(읽음) 처리한 게시물 (기기 로컬)
    private val _confirmedIds = MutableStateFlow(
        prefs.getStringSet("confirmed_ids", emptySet())?.toSet() ?: emptySet()
    )
    val confirmedIds: StateFlow<Set<String>> = _confirmedIds.asStateFlow()

    // ── 즐겨찾기 (계정에 저장 → 폰을 바꿔도 유지) ───────────────
    // 기기에도 함께 두어 오프라인이나 로그인 전에도 바로 보이게 한다.
    private val _favoriteIds = MutableStateFlow(
        prefs.getStringSet("favorite_ids", emptySet())?.toSet() ?: emptySet()
    )
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    /** 즐겨찾기만 모아 보기 */
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    fun isFavorite(postId: String): Boolean = postId in _favoriteIds.value

    fun toggleFavorite(postId: String) {
        val updated = if (postId in _favoriteIds.value) _favoriteIds.value - postId
                      else _favoriteIds.value + postId
        _favoriteIds.value = updated
        prefs.edit().putStringSet("favorite_ids", updated).apply()
        viewModelScope.launch { runCatching { crewRepo.saveFavorites(updated) } }
    }

    fun setShowFavoritesOnly(on: Boolean) { _showFavoritesOnly.value = on }

    /** 로그인 직후 서버에 저장된 즐겨찾기를 불러온다. */
    private fun syncFavorites() {
        viewModelScope.launch {
            val server = runCatching { crewRepo.loadFavorites() }.getOrNull() ?: return@launch
            // 기기에만 있던 것과 합쳐서 잃어버리지 않게 한다
            val merged = server + _favoriteIds.value
            _favoriteIds.value = merged
            prefs.edit().putStringSet("favorite_ids", merged).apply()
            if (merged != server) runCatching { crewRepo.saveFavorites(merged) }
        }
    }

    // ── 필터링된 피드 (핀 고정 우선 + 카테고리/검색) ─────────────
    val filteredPosts: StateFlow<List<Post>> =
        combine(
            _posts, _selectedCategory, _searchQuery, _showFavoritesOnly, _favoriteIds
        ) { posts, cat, query, favOnly, favIds ->
            posts.asSequence()
                .filter { !favOnly || it.id in favIds }
                .filter { cat == null || it.category == cat }
                .filter {
                    query.isBlank() ||
                        it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
                }
                // 고정 글을 맨 위로, 그 다음은 '자료 날짜' 순.
                // 서버 쿼리는 createdAt(올린 시각) 순이므로 여기서 다시 정렬한다.
                // 쿼리를 docDate 순으로 바꾸면 그 값이 없는 예전 글이 아예 빠지므로 그렇게 하지 않는다.
                .sortedWith(
                    compareByDescending<Post> { it.pinned }
                        .thenByDescending { it.effectiveDate?.toDate()?.time ?: 0L }
                )
                .toList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── 지난 자료 아카이브 (연도 → 월 묶음) ──────────────────
    data class ArchiveMonth(val month: Int, val posts: List<Post>)
    data class ArchiveYear(val year: Int, val total: Int, val months: List<ArchiveMonth>)

    val archive: StateFlow<List<ArchiveYear>> =
        filteredPosts.map { posts ->
            val cal = java.util.Calendar.getInstance()
            posts.mapNotNull { post ->
                val d = post.effectiveDate?.toDate() ?: return@mapNotNull null
                cal.time = d
                Triple(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, post)
            }
            .groupBy { it.first }                     // 연도별
            .toSortedMap(reverseOrder())              // 최신 연도부터
            .map { (year, rows) ->
                val months = rows.groupBy { it.second }
                    .toSortedMap(reverseOrder())      // 최신 월부터
                    .map { (m, list) -> ArchiveMonth(m, list.map { it.third }) }
                ArchiveYear(year, rows.size, months)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 로그인 강제 스위치를 서버에서 확인 (실패 시 false 유지 → 앱은 그대로 열린다)
        viewModelScope.launch {
            _requireLogin.value = crewRepo.requireLogin()
        }
        if (crewRepo.isCrewLoggedIn()) syncFavorites()
        // 날씨·기상특보 확인
        loadWeather()
        viewModelScope.launch {
            repo.postsFlow().collect { result ->
                _isLoading.value = false
                result.onSuccess { _posts.value = it }
                result.onFailure {
                    _message.value = UiMessage("게시물을 불러오지 못했습니다: ${it.localizedMessage}", true)
                }
            }
        }
        // 질의응답. 실패해도 앱 본 기능은 멀쩡해야 하므로 조용히 비워 둔다.
        viewModelScope.launch {
            questionRepo.questionsFlow().collect { result ->
                result.onSuccess { _questions.value = it }
            }
        }
        // 출무점호도 마찬가지 — 못 읽으면 홈 카드가 안 보일 뿐이다.
        viewModelScope.launch {
            briefingRepo.briefingsFlow().collect { result ->
                result.onSuccess { _briefings.value = it }
            }
        }
    }

    // ── 액션 ────────────────────────────────────────────────────
    fun toggleCategory(category: String) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun selectAll() { _selectedCategory.value = null }
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun isConfirmed(postId: String): Boolean = postId in _confirmedIds.value

    fun confirmRead(postId: String) {
        if (postId in _confirmedIds.value) return
        val updated = _confirmedIds.value + postId
        _confirmedIds.value = updated
        prefs.edit().putStringSet("confirmed_ids", updated).apply()
        val empNo = _crewEmpNo.value
        val name = _crewName.value
        viewModelScope.launch { runCatching { repo.confirmRead(postId, empNo, name) } }
    }

    /**
     * 확인 현황(관리자용). 명단 전체에서 확인한 사람을 빼면 아직 안 본 사람이 된다.
     * 이름은 등록할 때 받은 것만 있으므로 없는 사람은 화면에서 사번으로 표시한다.
     * 옛 버전은 사람 없이 수만 세었으므로 그 차이를 anonymous로 따로 알려 준다.
     */
    fun loadConfirmReport(postId: String, onResult: (ConfirmReport) -> Unit) {
        viewModelScope.launch {
            val confirmed = repo.loadConfirms(postId)
            val roster = runCatching { crewRepo.effectiveRoster(appContext) }.getOrDefault(emptySet())
            val names = crewRepo.allNames()
            val done = confirmed.map { it.empNo }.toSet()
            val pending = (roster - done).sorted()
                .map { CrewConfirm(empNo = it, name = names[it].orEmpty()) }
            val counted = _posts.value.firstOrNull { it.id == postId }?.confirms ?: 0L
            onResult(
                ConfirmReport(
                    confirmed = confirmed.map { c ->
                        if (c.name.isBlank()) c.copy(name = names[c.empNo].orEmpty()) else c
                    },
                    pending = pending,
                    anonymous = (counted - confirmed.size).coerceAtLeast(0L)
                )
            )
        }
    }

    fun togglePin(postId: String, pinned: Boolean) {
        viewModelScope.launch {
            try { repo.setPinned(postId, pinned) }
            catch (e: Exception) { _message.value = UiMessage("고정 변경 실패: ${e.localizedMessage}", true) }
        }
    }

    fun markRead(postId: String) {
        if (postId in _readIds.value) return
        val updated = _readIds.value + postId
        _readIds.value = updated
        prefs.edit().putStringSet("read_ids", updated).apply()
        // 기기당 1회만 조회수 증가 (실패해도 조용히 무시)
        viewModelScope.launch {
            runCatching { repo.incrementViews(postId) }
        }
    }

    fun postById(id: String): Post? = _posts.value.find { it.id == id }

    fun login(id: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.login(id, password)
                _isAdmin.value = repo.adminEmail() == CrewRepository.ADMIN_EMAIL
                if (!_isAdmin.value) {
                    repo.logout()
                    _message.value = UiMessage("관리자 계정이 아닙니다", true)
                    return@launch
                }
                _message.value = UiMessage("관리자 모드로 로그인했습니다")
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("로그인 실패: 아이디 또는 비밀번호를 확인하세요", true)
            }
        }
    }

    fun logout() {
        repo.logout()
        _isAdmin.value = false
        // 실제로 Firebase 세션이 사라졌으니 사번도 비운다(정직해야 한다).
        // 다만 crew_verified_once 는 남겨 둔다 — 승무원이 나가겠다고 한 적이 없으므로
        // 로그인 게이트로 튕기지 않고 홈 화면이 그대로 보여야 한다.
        _crewEmpNo.value = null
        _message.value = UiMessage("관리자 모드를 종료했습니다. 확인·댓글을 쓰려면 다시 로그인하세요.")
    }

    // ── 승무원 로그인 ────────────────────────────────────────────
    /** 등록 1단계: 명단 확인 후, 예전에 등록한 이름이 있으면 돌려준다. */
    fun crewCheckEmpNo(empNo: String, onOk: (String?) -> Unit) {
        viewModelScope.launch {
            if (!crewRepo.isInRoster(appContext, empNo)) {
                _message.value = UiMessage("사업소 명단에 없는 사번입니다", true)
                return@launch
            }
            // 이미 등록된 사번인지는 계정 생성 단계에서 판정한다.
            // (계정 존재 여부를 미리 묻는 API는 사용하지 않는다)
            onOk(crewRepo.savedName(empNo))
        }
    }

    /** 등록 2단계: 계정 생성 */
    fun crewRegister(empNo: String, name: String, pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                crewRepo.register(empNo, name, pin)
                _crewEmpNo.value = empNo.trim()
                _crewName.value = name.trim()
                prefs.edit().putString("crew_name", name.trim()).apply()
                markCrewVerified()
                syncFavorites()
                _message.value = UiMessage("${name.trim()} 님, 환영합니다")
                onSuccess()
            } catch (e: FirebaseAuthUserCollisionException) {
                _message.value = UiMessage("이미 등록된 사번입니다. 로그인해주세요", true)
            } catch (e: Exception) {
                _message.value = UiMessage("등록에 실패했습니다: ${e.localizedMessage}", true)
            }
        }
    }

    /** 로그인 */
    fun crewSignIn(empNo: String, pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                crewRepo.signIn(empNo, pin)
                _crewEmpNo.value = empNo.trim()
                val n = crewRepo.savedName(empNo)
                if (n != null) {
                    _crewName.value = n
                    prefs.edit().putString("crew_name", n).apply()
                }
                markCrewVerified()
                syncFavorites()
                onSuccess()
            } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                // 그 사번으로 만든 계정이 아예 없다
                _message.value = UiMessage("아직 등록하지 않은 사번입니다. 아래 '처음이신가요? 등록하기'를 눌러 PIN을 만들어주세요", true)
            } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                // PIN 불일치. 다만 이메일 열거 보호가 켜져 있으면 '계정 없음'도 여기로 온다.
                _message.value = UiMessage("PIN이 맞지 않습니다. 등록한 적이 없다면 '처음이신가요? 등록하기'를 눌러주세요", true)
            } catch (e: Exception) {
                // 설정 문제(로그인 제공업체 미사용 등)를 눈으로 보려면 원문이 필요하다
                _message.value = UiMessage("로그인 실패: ${e.localizedMessage}", true)
            }
        }
    }

    fun crewSignOut() {
        crewRepo.signOut()
        _crewEmpNo.value = null
        _isAdmin.value = false
        // 관리자 로그아웃(logout)과 달리 승무원이 스스로 나간 것이므로 기기 표식도 지운다.
        // 의도적으로 나갔으면 다시 로그인 게이트가 뜨는 게 맞다 (폰을 넘겨줄 때 등).
        _crewVerifiedOnce.value = false
        prefs.edit().putBoolean("crew_verified_once", false).apply()
        _message.value = UiMessage("로그아웃했습니다")
    }

    /** content:// URI에서 파일명/크기 조회 */
    fun fileInfo(uri: Uri): Pair<String, Long> {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment ?: "파일"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        return name to size
    }

    fun mimeOf(uri: Uri): String =
        getApplication<Application>().contentResolver.getType(uri) ?: "application/octet-stream"

    fun savePost(
        editingId: String?,
        category: String, title: String, content: String,
        keptAttachments: List<Attachment>,   // 수정 시 유지할 기존 첨부
        newFileUris: List<Uri>,              // 새로 업로드할 파일들
        links: List<LinkAttachment> = emptyList(),  // 링크 첨부
        docDate: com.google.firebase.Timestamp? = null,  // 자료 날짜(과거 자료 정리용)
        onDone: () -> Unit
    ) {
        if (title.isBlank() || content.isBlank()) {
            _message.value = UiMessage("제목과 내용을 입력해주세요", true)
            return
        }
        viewModelScope.launch {
            try {
                _isUploading.value = true
                // 1) 새 파일들 Storage 업로드
                val uploaded = newFileUris.map { uri ->
                    val (name, size) = fileInfo(uri)
                    val mime = mimeOf(uri)
                    val isVideo = mime.startsWith("video/")
                    val isImage = mime.startsWith("image/")
                    // 사진은 업로드 직전에 자동으로 줄여서 올리므로 원본 기준은 넉넉하게 잡는다.
                    // 1GB짜리 동영상은 업로드에 수 분이 걸린다 — 진행 표시가 끝날 때까지 기다려야 한다.
                    val limit = when {
                        isVideo -> 1024L * 1024 * 1024
                        isImage -> 50L * 1024 * 1024
                        else -> 20L * 1024 * 1024
                    }
                    if (size > limit) {
                        val mb = when {
                            isVideo -> "1GB"
                            isImage -> "50MB"
                            else -> "20MB"
                        }
                        val what = when {
                            isVideo -> "동영상은"
                            isImage -> "사진은"
                            else -> "파일당"
                        }
                        throw IllegalArgumentException("'$name' — $what 최대 $mb 까지 첨부할 수 있어요")
                    }
                    repo.uploadAttachment(getApplication<Application>(), uri, name, mime, size)
                }
                val attachments = keptAttachments + uploaded

                // 2) Firestore 저장
                if (editingId == null) {
                    repo.addPost(category, "", title, content, attachments, links, docDate)
                    _message.value = UiMessage("게시물이 등록되었습니다")
                } else {
                    repo.updatePost(editingId, category, "", title, content, attachments, links, docDate)
                    _message.value = UiMessage("게시물이 수정되었습니다")
                }
                onDone()
            } catch (e: Exception) {
                _message.value = UiMessage("저장 실패: ${e.localizedMessage}", true)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun deletePost(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.deletePost(id)
                _message.value = UiMessage("게시물이 삭제되었습니다")
                onDone()
            } catch (e: Exception) {
                _message.value = UiMessage("삭제 실패: ${e.localizedMessage}", true)
            }
        }
    }

    // ── 게시물 댓글 ─────────────────────────────────────────────
    // 작성자 표기는 질의응답과 같은 규칙(questionAuthor)을 쓴다.

    fun commentsFlow(postId: String): Flow<Result<List<Comment>>> = repo.commentsFlow(postId)

    fun addComment(postId: String, content: String, onSuccess: () -> Unit) {
        if (content.isBlank()) return
        val (name, empNo) = questionAuthor()
        viewModelScope.launch {
            try {
                repo.addComment(postId, content, name, empNo, _isAdmin.value)
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("댓글 등록 실패: ${e.localizedMessage}", true)
            }
        }
    }

    /**
     * 삭제는 관리자 또는 본인만. 규칙에서도 막지만, 화면 조건이 어긋나 눌리는 경우를 위해
     * 여기서도 막는다. 판정에 사번이 필요해 id 대신 댓글을 통째로 받는다.
     */
    fun deleteComment(postId: String, comment: Comment) {
        if (!canDeleteComment(comment)) return
        viewModelScope.launch {
            try {
                repo.deleteComment(postId, comment.id)
            } catch (e: Exception) {
                _message.value = UiMessage("삭제 실패: ${e.localizedMessage}", true)
            }
        }
    }

    /** 그 댓글을 지울 수 있는 사람인가 (관리자이거나 본인). 화면의 삭제 아이콘 노출 조건. */
    fun canDeleteComment(comment: Comment): Boolean =
        _isAdmin.value ||
            (comment.authorEmpNo.isNotBlank() && comment.authorEmpNo == _crewEmpNo.value)

    // ── 질의응답 ────────────────────────────────────────────────
    /**
     * 실명제. 관리자는 이름만 남기고 사번은 비운다(관리자 계정에는 사번이 없다).
     * 한 곳에 모아두지 않으면 질문과 답변의 작성자 표기가 어긋난다.
     */
    private fun questionAuthor(): Pair<String, String> =
        if (_isAdmin.value) "관리자" to ""
        else (_crewName.value ?: "이름 미등록") to (_crewEmpNo.value ?: "")

    fun answersFlow(questionId: String): Flow<Result<List<Answer>>> =
        questionRepo.answersFlow(questionId)

    fun addQuestion(title: String, content: String, imageUris: List<Uri>, onSuccess: () -> Unit) {
        if (title.isBlank() || content.isBlank()) {
            _message.value = UiMessage("제목과 내용을 입력해주세요", true)
            return
        }
        val (name, empNo) = questionAuthor()
        viewModelScope.launch {
            try {
                _isUploading.value = true
                // 사진 업로드는 게시물과 같은 경로를 쓴다 (축소·EXIF 처리가 거기 들어 있다)
                val uploaded = imageUris.take(QuestionRepository.MAX_IMAGES).map { uri ->
                    val (fileName, size) = fileInfo(uri)
                    repo.uploadAttachment(appContext, uri, fileName, mimeOf(uri), size)
                }
                questionRepo.addQuestion(title, content, name, empNo, uploaded, emptyList())
                _message.value = UiMessage("질문이 등록되었습니다")
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("등록 실패: ${e.localizedMessage}", true)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun addAnswer(questionId: String, content: String, onSuccess: () -> Unit) {
        if (content.isBlank()) return
        val (name, empNo) = questionAuthor()
        viewModelScope.launch {
            try {
                questionRepo.addAnswer(questionId, content, name, empNo, _isAdmin.value)
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("답변 등록 실패: ${e.localizedMessage}", true)
            }
        }
    }

    fun deleteQuestion(id: String, onSuccess: () -> Unit) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                questionRepo.deleteQuestion(id)
                _message.value = UiMessage("질문을 삭제했습니다")
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("삭제 실패: ${e.localizedMessage}", true)
            }
        }
    }

    fun deleteAnswer(questionId: String, answerId: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                questionRepo.deleteAnswer(questionId, answerId)
            } catch (e: Exception) {
                _message.value = UiMessage("삭제 실패: ${e.localizedMessage}", true)
            }
        }
    }

    // ── 출무점호 ────────────────────────────────────────────────
    fun saveBriefing(
        raw: String,
        fileUris: List<Uri>,
        links: List<LinkAttachment>,
        onSuccess: () -> Unit
    ) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                _isUploading.value = true
                // 사진 축소·EXIF 처리가 게시물 첨부와 똑같이 필요하므로 그 경로를 그대로 쓴다.
                val attachments = fileUris.map { uri ->
                    val (name, size) = fileInfo(uri)
                    repo.uploadAttachment(getApplication<Application>(), uri, name, mimeOf(uri), size)
                }
                briefingRepo.save(raw, attachments, links)
                _message.value = UiMessage("출무점호를 등록했습니다")
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("등록 실패: ${e.localizedMessage}", true)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun deleteBriefing(id: String, onSuccess: () -> Unit) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                briefingRepo.delete(id)
                _message.value = UiMessage("출무점호를 삭제했습니다")
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("삭제 실패: ${e.localizedMessage}", true)
            }
        }
    }

    fun viewQuestion(id: String) {
        viewModelScope.launch { questionRepo.incrementViews(id) }
    }

    // ── 규정 AI 답변 ────────────────────────────────────────────
    // 실제 호출은 서버(Cloud Functions)가 한다 — 앱에는 API 키가 없다.
    private val _aiAnswer = MutableStateFlow<String?>(null)
    val aiAnswer: StateFlow<String?> = _aiAnswer.asStateFlow()
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()
    fun clearAiAnswer() { _aiAnswer.value = null }

    /** 로그인 여부. 서버가 어차피 거부하므로 헛되이 왕복하지 않고 앱에서 먼저 막는다. */
    private fun aiAllowed(): Boolean {
        if (crewRepo.isCrewLoggedIn() || _isAdmin.value) return true
        _message.value = UiMessage("AI 답변은 로그인 후 이용할 수 있습니다", true)
        return false
    }

    /** 검색 결과 상위 조문을 근거로 AI에게 답을 요청한다. 로그인 필수(서버가 거부한다). */
    fun askRegulationAi(question: String, articles: List<Map<String, String>>) {
        if (!aiAllowed()) return
        viewModelScope.launch {
            _aiLoading.value = true
            try {
                _aiAnswer.value = aiRepo.askRegulation(question, articles)
            } catch (e: Exception) {
                _message.value = UiMessage("AI 답변 실패: ${e.localizedMessage}", true)
            } finally {
                _aiLoading.value = false
            }
        }
    }

    /** 글 3줄 요약. 결과는 바로 본문에 넣지 않고 화면이 사람에게 먼저 보여 준다. */
    fun summarizeWithAi(title: String, content: String, onResult: (String) -> Unit) {
        if (!aiAllowed()) return
        viewModelScope.launch {
            _aiLoading.value = true
            try {
                onResult(aiRepo.summarizePost(title, content))
            } catch (e: Exception) {
                _message.value = UiMessage("AI 요약 실패: ${e.localizedMessage}", true)
            } finally {
                _aiLoading.value = false
            }
        }
    }

    companion object {
        const val TOPIC_NEW_POSTS = "new_posts"
    }

}
