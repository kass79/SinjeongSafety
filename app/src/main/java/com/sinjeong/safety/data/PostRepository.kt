package com.sinjeong.safety.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    suspend fun uploadAttachment(
        context: Context,
        uri: Uri,
        name: String,
        mimeType: String,
        size: Long
    ): Attachment {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        val safeName = name.replace(Regex("[/\\#?%*:|\"<>]"), "_")
        val ref = storage.reference.child("attachments/${System.currentTimeMillis()}_$safeName")

        // 사진은 올리기 전에 줄인다. 게시판에서 보는 데는 원본 화질이 필요 없고,
        // 요즘 폰 사진은 한 장에 3~5MB라 용량과 업로드 시간을 크게 잡아먹는다.
        val shrunk = if (mimeType.startsWith("image/")) shrinkImage(context, uri) else null

        return if (shrunk != null && shrunk.size < size) {
            val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
            ref.putBytes(shrunk, meta).await()
            val url = ref.downloadUrl.await().toString()
            Attachment(name = name, url = url, mimeType = "image/jpeg", size = shrunk.size.toLong())
        } else {
            // 압축에 실패했거나 오히려 커진 경우엔 원본을 그대로 올린다.
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            Attachment(name = name, url = url, mimeType = mimeType, size = size)
        }
    }

    /**
     * 사진을 긴 변 [maxDim] 픽셀로 줄이고 JPEG로 다시 인코딩한다.
     * 큰 사진을 통째로 메모리에 올리면 앱이 죽을 수 있어 inSampleSize로 미리 줄여 읽고,
     * 촬영 방향(EXIF)을 반영해 세로 사진이 눕지 않게 하며,
     * 투명 배경(PNG)이 검게 나오지 않도록 흰 바탕에 그린다.
     * 실패하면 null을 돌려주고 호출한 쪽이 원본을 올린다.
     */
    private suspend fun shrinkImage(
        context: Context,
        uri: Uri,
        maxDim: Int = 1600,
        quality: Int = 82
    ): ByteArray? = withContext(Dispatchers.IO) {
        var source: Bitmap? = null
        var rotated: Bitmap? = null
        var flattened: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val ow = bounds.outWidth
            val oh = bounds.outHeight
            if (ow <= 0 || oh <= 0) return@withContext null

            var sample = 1
            while (ow / sample > maxDim * 2 || oh / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            source = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@withContext null

            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val w = source.width
            val h = source.height
            val scale = maxDim.toFloat() / maxOf(w, h)
            val matrix = Matrix()
            if (scale < 1f) matrix.postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            rotated = Bitmap.createBitmap(source, 0, 0, w, h, matrix, true)

            flattened = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flattened)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(rotated, 0f, 0f, null)

            val out = ByteArrayOutputStream()
            flattened.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            flattened?.recycle()
            if (rotated !== source) rotated?.recycle()
            source?.recycle()
        }
    }

    suspend fun addPost(category: String, tag: String, title: String, content: String,
                        attachments: List<Attachment> = emptyList(),
                        links: List<LinkAttachment> = emptyList(),
                        docDate: Timestamp? = null) {
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
            // 새 글에도 링크를 저장한다. 예전엔 이 줄이 없어서 글을 올릴 때 붙인 링크가
            // 조용히 사라지고, 수정 화면에서 다시 넣어야만 남았다(updatePost에는 있었다).
            "links" to links.map { mapOf("url" to it.url, "title" to it.title) },
            "views" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        // 자료 날짜를 지정한 경우에만 저장한다. 없으면 올린 시각이 기준이 된다.
        if (docDate != null) data["docDate"] = docDate
        postsRef.add(data).await()
    }

    suspend fun updatePost(id: String, category: String, tag: String, title: String, content: String,
                           attachments: List<Attachment>,
                           links: List<LinkAttachment> = emptyList(),
                           docDate: Timestamp? = null) {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        // 수정 전 첨부 목록을 기억해 둔다(빠진 파일을 나중에 정리하기 위해).
        val beforeUrls = attachmentUrlsOf(id)
        postsRef.document(id).update(
            mapOf(
                "category" to category,
                "tag" to tag,
                "title" to title.trim(),
                "content" to content.trim(),
                "attachments" to attachments.map {
                    mapOf("name" to it.name, "url" to it.url, "mimeType" to it.mimeType, "size" to it.size)
                },
                "links" to links.map { mapOf("url" to it.url, "title" to it.title) },
                "updatedAt" to FieldValue.serverTimestamp()
            ) + (if (docDate != null) mapOf("docDate" to docDate) else emptyMap())
        ).await()
        // 관리자가 첨부를 빼고 저장한 경우, 더 이상 쓰이지 않는 파일을 지운다.
        val keep = attachments.map { it.url }.toSet()
        deleteFiles(beforeUrls.filter { it !in keep })
    }

    /** 조회수 +1 (로그인 없이도 가능 — 보안 규칙에서 views 필드 +1만 허용) */
    suspend fun incrementViews(id: String) {
        postsRef.document(id).update("views", FieldValue.increment(1)).await()
    }

    /** 상단 고정 토글 (관리자만) */
    suspend fun setPinned(id: String, pinned: Boolean) {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        postsRef.document(id).update("pinned", pinned).await()
    }

    /**
     * 확인(읽음) 처리. 인원 수를 +1 하고, 로그인한 승무원이면 누가 봤는지도 남긴다.
     * 사람 기록이 있어야 관리자가 "누가 아직 안 봤는지"를 볼 수 있다.
     * 문서 id를 사번으로 두어 같은 사람이 폰을 바꿔 눌러도 한 줄만 남는다.
     */
    suspend fun confirmRead(id: String, empNo: String? = null, name: String? = null) {
        postsRef.document(id).update("confirms", FieldValue.increment(1)).await()
        if (empNo.isNullOrBlank()) return
        runCatching {
            postsRef.document(id).collection("confirms").document(empNo).set(
                mapOf(
                    "empNo" to empNo,
                    "name" to (name?.trim() ?: ""),
                    "at" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    // ── 댓글 (posts/{id}/comments) ────────────────────────────
    // 질의응답의 답변(QuestionRepository.answersFlow)과 같은 구조다.
    // 하위 컬렉션이라 목록의 Post 로는 알 수 없고, 상세 화면에서만 구독한다.

    /** 그 글의 댓글 (오래된 순 — 대화 흐름대로 읽히도록) */
    fun commentsFlow(postId: String): Flow<Result<List<Comment>>> = callbackFlow {
        val registration = postsRef.document(postId).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { doc.toObject(Comment::class.java) }.getOrNull()
                } ?: emptyList()
                trySend(Result.success(list))
            }
        awaitClose { registration.remove() }
    }

    /** 댓글 작성. 목록에 보여줄 댓글 수도 함께 올린다. */
    suspend fun addComment(
        postId: String,
        content: String,
        authorName: String,
        authorEmpNo: String,
        isAdmin: Boolean
    ) {
        postsRef.document(postId).collection("comments").add(
            mapOf(
                "content" to content.trim(),
                "authorName" to authorName,
                "authorEmpNo" to authorEmpNo,
                "isAdmin" to isAdmin,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        // 카운터가 실패해도 댓글 자체는 남아야 하므로 따로 감싼다.
        runCatching {
            postsRef.document(postId).update("commentCount", FieldValue.increment(1)).await()
        }
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        postsRef.document(postId).collection("comments").document(commentId).delete().await()
        runCatching {
            postsRef.document(postId).update("commentCount", FieldValue.increment(-1)).await()
        }
    }

    /** 그 글을 확인한 사람들 (최근 순). 읽기 실패하면 빈 목록. */
    suspend fun loadConfirms(postId: String): List<CrewConfirm> = runCatching {
        postsRef.document(postId).collection("confirms").get().await().documents.map { d ->
            CrewConfirm(
                empNo = d.id,
                name = d.getString("name")?.trim().orEmpty(),
                at = d.getTimestamp("at")
            )
        }.sortedByDescending { it.at?.seconds ?: 0L }
    }.getOrDefault(emptyList())

    suspend fun deletePost(id: String) {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        // 글을 지우기 전에 첨부 주소를 확보한다(지운 뒤엔 알 수 없으므로).
        val urls = attachmentUrlsOf(id)
        postsRef.document(id).delete().await()
        deleteFiles(urls)
    }

    /** 해당 글에 첨부된 파일들의 다운로드 주소. 조회 실패 시 빈 목록. */
    private suspend fun attachmentUrlsOf(id: String): List<String> = try {
        val snap = postsRef.document(id).get().await()
        @Suppress("UNCHECKED_CAST")
        val list = snap.get("attachments") as? List<Map<String, Any?>> ?: emptyList()
        list.mapNotNull { it["url"] as? String }.filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    /** Storage에서 파일을 지운다. 개별 실패는 무시하고 나머지를 계속 지운다. */
    private suspend fun deleteFiles(urls: List<String>) {
        for (url in urls) {
            try {
                storage.getReferenceFromUrl(url).delete().await()
            } catch (e: Exception) {
            }
        }
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
