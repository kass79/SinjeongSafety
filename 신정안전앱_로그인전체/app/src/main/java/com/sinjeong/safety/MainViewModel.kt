package com.sinjeong.safety

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.provider.OpenableColumns
import com.sinjeong.safety.data.Attachment
import com.sinjeong.safety.data.LinkAttachment
import com.sinjeong.safety.data.Post
import com.sinjeong.safety.data.effectiveDate
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.sinjeong.safety.data.CrewRepository
import com.sinjeong.safety.data.PostRepository
import com.sinjeong.safety.data.Tags
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
    private val appContext: Context = app.applicationContext
    private val prefs = app.getSharedPreferences("safety_prefs", Context.MODE_PRIVATE)

    // ── 원본 데이터 ──────────────────────────────────────────────
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

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

    /** 로그인 강제 스위치. 서버에서 읽어오며, 못 읽으면 false로 둔다. */
    private val _requireLogin = MutableStateFlow(false)
    val requireLogin: StateFlow<Boolean> = _requireLogin.asStateFlow()

    /** 로그인 화면을 띄워야 하는가 (강제 ON + 승무원 미로그인 + 관리자도 아님) */
    val needCrewLogin: StateFlow<Boolean> =
        combine(_requireLogin, _crewEmpNo, _isAdmin) { require, empNo, admin ->
            require && empNo == null && !admin
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

    // ── 필터링된 피드 (핀 고정 우선 + 카테고리/검색) ─────────────
    val filteredPosts: StateFlow<List<Post>> =
        combine(_posts, _selectedCategory, _searchQuery) { posts, cat, query ->
            posts.asSequence()
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
        viewModelScope.launch {
            repo.postsFlow().collect { result ->
                _isLoading.value = false
                result.onSuccess { _posts.value = it }
                result.onFailure {
                    _message.value = UiMessage("게시물을 불러오지 못했습니다: ${it.localizedMessage}", true)
                }
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
        viewModelScope.launch { runCatching { repo.confirmRead(postId) } }
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
        _crewEmpNo.value = null
        _message.value = UiMessage("로그아웃했습니다")
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
                onSuccess()
            } catch (e: Exception) {
                _message.value = UiMessage("사번 또는 PIN을 확인해주세요", true)
            }
        }
    }

    fun crewSignOut() {
        crewRepo.signOut()
        _crewEmpNo.value = null
        _isAdmin.value = false
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
                    val limit = when {
                        isVideo -> 200L * 1024 * 1024
                        isImage -> 50L * 1024 * 1024
                        else -> 20L * 1024 * 1024
                    }
                    if (size > limit) {
                        val mb = when {
                            isVideo -> "200MB"
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
}
