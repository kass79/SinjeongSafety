package com.sinjeong.safety.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore "posts" 컬렉션 실시간 연동 + 관리자 인증(Firebase Auth)
 */
class PostRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val postsRef get() = db.collection("posts")

    /** 실시간 게시물 스트림 (최신순) — 스냅샷 리스너 기반이라 모든 기기에서 즉시 반영됨 */
    fun postsFlow(): Flow<Result<List<Post>>> = callbackFlow {
        val registration = postsRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val posts = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toObject(Post::class.java) }.getOrNull()
                } ?: emptyList()
                trySend(Result.success(posts))
            }
        awaitClose { registration.remove() }
    }

    /** 파일 1개 업로드 → 다운로드 URL이 담긴 Attachment 반환 */
    suspend fun uploadAttachment(uri: Uri, name: String, mimeType: String, size: Long): Attachment {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        val safeName = name.replace(Regex("[/\\#?%*:|\"<>]"), "_")
        val ref = storage.reference.child("attachments/${System.currentTimeMillis()}_$safeName")
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        return Attachment(name = name, url = url, mimeType = mimeType, size = size)
    }

    suspend fun addPost(category: String, tag: String, title: String, content: String,
                        attachments: List<Attachment> = emptyList()) {
        val user = auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        val data = hashMapOf(
            "category" to category,
            "tag" to tag,
            "title" to title.trim(),
            "content" to content.trim(),
            "authorName" to (user.email?.substringBefore("@") ?: "관리자"),
            "authorUid" to user.uid,
            "attachments" to attachments.map {
                mapOf("name" to it.name, "url" to it.url, "mimeType" to it.mimeType, "size" to it.size)
            },
            "views" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        postsRef.add(data).await()
    }

    suspend fun updatePost(id: String, category: String, tag: String, title: String, content: String,
                           attachments: List<Attachment>) {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        postsRef.document(id).update(
            mapOf(
                "category" to category,
                "tag" to tag,
                "title" to title.trim(),
                "content" to content.trim(),
                "attachments" to attachments.map {
                    mapOf("name" to it.name, "url" to it.url, "mimeType" to it.mimeType, "size" to it.size)
                },
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** 조회수 +1 (로그인 없이도 가능 — 보안 규칙에서 views 필드 +1만 허용) */
    suspend fun incrementViews(id: String) {
        postsRef.document(id).update("views", FieldValue.increment(1)).await()
    }

    suspend fun deletePost(id: String) {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        postsRef.document(id).delete().await()
    }

    // ── 관리자 인증 ──────────────────────────────────────────────
    fun isLoggedIn(): Boolean = auth.currentUser != null
    fun adminEmail(): String? = auth.currentUser?.email
    fun currentUid(): String? = auth.currentUser?.uid

    /**
     * 아이디만 입력해도 되도록 "@" 없으면 사내 도메인을 붙여 이메일로 변환.
     * 예) "sinjeong-admin" → "sinjeong-admin@sinjeong.app"
     */
    suspend fun login(idOrEmail: String, password: String) {
        val email = if (idOrEmail.contains("@")) idOrEmail.trim()
        else "${idOrEmail.trim()}@sinjeong.app"
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun logout() = auth.signOut()

    companion object {
        fun now(): Timestamp = Timestamp.now()
    }
}
