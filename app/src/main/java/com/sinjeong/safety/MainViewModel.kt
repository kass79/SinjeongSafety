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
import com.sinjeong.safety.data.PostRepository
import com.sinjeong.safety.data.Tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class UiMessage(val text: String, val isError: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PostRepository()
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
    private val _isAdmin = MutableStateFlow(repo.isLoggedIn())
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()
    val adminEmail: String? get() = repo.adminEmail()

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
                .sortedByDescending { it.pinned }   // 고정 글을 항상 위로 (createdAt 정렬은 이미 되어 있음)
                .toList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
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
                _isAdmin.value = true
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
                    val limit = if (isVideo) 200L * 1024 * 1024 else 20L * 1024 * 1024
                    if (size > limit) {
                        val mb = if (isVideo) "200MB" else "20MB"
                        throw IllegalArgumentException("'$name' — ${if (isVideo) "동영상은" else "파일당"} 최대 $mb 까지 첨부할 수 있어요")
                    }
                    repo.uploadAttachment(uri, name, mime, size)
                }
                val attachments = keptAttachments + uploaded

                // 2) Firestore 저장
                if (editingId == null) {
                    repo.addPost(category, "", title, content, attachments, links)
                    _message.value = UiMessage("게시물이 등록되었습니다")
                } else {
                    repo.updatePost(editingId, category, "", title, content, attachments, links)
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
