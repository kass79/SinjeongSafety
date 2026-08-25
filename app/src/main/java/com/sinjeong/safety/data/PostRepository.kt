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
import android.media.MediaMetadataRetriever
import android.graphics.pdf.PdfRenderer
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
            // 동영상이면 포스터(첫 화면)도 같이 올린다. 이 함수는 게시물·출무점호·질의응답이
            // 모두 거쳐가는 한 곳이라 여기서 만들면 어느 화면에서 올려도 포스터가 붙는다.
            val poster = if (mimeType.startsWith("video/")) uploadPoster(context, uri, safeName) else ""
            // PDF 면 쪽마다 그림으로도 올린다. 여기가 세 업로드 경로가 다 지나는 곳이라
            // 게시물이든 출무점호든 한 번에 붙는다(포스터와 같은 자리, 같은 이유).
            val isPdf = mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
            val (pageUrls, pageCount) =
                if (isPdf) uploadPdfPages(context, uri, safeName) else (emptyList<String>() to 0)
            Attachment(
                name = name, url = url, mimeType = mimeType, size = size, posterUrl = poster,
                pageUrls = pageUrls, pageCount = pageCount
            )
        }
    }

    /**
     * PDF 를 쪽마다 JPEG 로 렌더해 Storage 에 올리고 (주소 목록, 원본 전체 쪽 수) 를 돌려준다.
     *
     * 보는 쪽에서 그때그때 렌더하지 않고 올릴 때 한 번 굽는 이유: 승무원 기기에서 즉시 뜨고,
     * 터널에서도 Coil 캐시가 듣고, 이미 있는 이미지 표시 코드를 그대로 쓴다.
     *
     * 긴 변 1600px / JPEG 82 는 사진 축소([shrinkImage])와 같은 눈높이다. 실제 공문
     * (운전정보 2026-1, A4 가로)으로 확인했다 — 137dpi, 253KB, 본문 글씨가 1:1 로 또렷하다.
     * 이보다 낮추면(가로 1080 = 92dpi) 작은 글씨가 뭉개진다.
     *
     * 흰 바탕을 먼저 깔아야 한다. PDF 는 배경이 비어 있어서 그냥 렌더하면 글씨만 뜨고
     * 나머지가 새까맣게 나온다.
     *
     * 암호가 걸렸거나 손상됐거나 메모리가 모자라면 통째로 삼키고 빈 목록을 준다 —
     * 그림은 있으면 좋은 것이지 첨부 자체가 아니다. 업로드가 실패해선 안 된다.
     * 중간에 엎어지면 이미 올린 쪽은 지운다(안 지우면 아무도 안 쓰는 파일이 Storage 에 남는다).
     */
    private suspend fun uploadPdfPages(
        context: Context,
        uri: Uri,
        safeName: String
    ): Pair<List<String>, Int> = withContext(Dispatchers.IO) {
        val urls = ArrayList<String>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext emptyList<String>() to 0
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val total = renderer.pageCount
                    for (i in 0 until minOf(total, MAX_PDF_PAGES)) {
                        renderer.openPage(i).use { page ->
                            val scale = 1600f / maxOf(page.width, page.height)
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val out = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                            bmp.recycle()

                            val pageRef = storage.reference.child(
                                "attachments/${System.currentTimeMillis()}_p${i + 1}_$safeName.jpg"
                            )
                            val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
                            pageRef.putBytes(out.toByteArray(), meta).await()
                            urls.add(pageRef.downloadUrl.await().toString())
                        }
                    }
                    urls.toList() to total
                }
            }
        } catch (e: Exception) {
            deleteFiles(urls)
            emptyList<String>() to 0
        } catch (e: OutOfMemoryError) {
            deleteFiles(urls)
            emptyList<String>() to 0
        }
    }

    /**
     * 동영상의 한 장면을 JPEG 로 뽑아 Storage 에 올리고 그 주소를 돌려준다(실패하면 빈 문자열).
     *
     * 2초 지점을 쓰는 이유: 교육영상이 페이드인으로 시작해 0~1초는 거의 검은 화면이다
     * (실제 교육영상 4편으로 확인 — 0초는 새까맣고, 1초는 제목이 반쯤 뜬 상태, 2초에 제목 카드가 다 뜬다).
     * 2초보다 짧은 영상은 첫 프레임으로 물러선다.
     *
     * OPTION_CLOSEST 를 쓰는 게 핵심이다. 흔히 쓰는 OPTION_CLOSEST_SYNC 는 '가장 가까운 키프레임'을
     * 주는데, 이 교육영상들은 키프레임이 0초 다음이 8.3초라(ffprobe 로 확인) 2초를 달라고 해도
     * 0초 키프레임 — 바로 그 새까만 화면 — 이 돌아온다. CLOSEST 는 키프레임부터 풀어서
     * 진짜 2초 프레임을 준다. 2초어치 디코딩은 업로드 시간에 비하면 없는 셈이다.
     *
     * 코덱·권한 문제로 추출이 실패할 수 있는데, 그때 업로드 전체를 죽이면 안 된다
     * — 포스터는 있으면 좋은 것이지 영상 자체가 아니다. 그래서 전부 삼키고 빈 문자열을 준다.
     */
    private suspend fun uploadPoster(
        context: Context,
        uri: Uri,
        safeName: String
    ): String = withContext(Dispatchers.IO) {
        // minSdk 26 이라 MediaMetadataRetriever 는 use{} 를 못 쓴다(AutoCloseable 은 API 29부터).
        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            retriever.setDataSource(context, uri)
            frame = retriever.getFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime()
            val bmp = frame ?: return@withContext ""

            // 목록·상세에서 볼 뿐이라 가로 1080 이면 충분하다(사진 축소 관례와 같은 눈높이).
            val scale = 1080f / maxOf(bmp.width, bmp.height)
            scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                )
            } else bmp

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)

            val posterRef = storage.reference
                .child("attachments/${System.currentTimeMillis()}_poster_$safeName.jpg")
            val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
            posterRef.putBytes(out.toByteArray(), meta).await()
            posterRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            ""
        } catch (e: OutOfMemoryError) {
            ""
        } finally {
            if (scaled !== frame) scaled?.recycle()
            frame?.recycle()
            runCatching { retriever.release() }
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
                mapOf("name" to it.name, "url" to it.url, "mimeType" to it.mimeType,
                      "size" to it.size, "posterUrl" to it.posterUrl,
                      "pageUrls" to it.pageUrls, "pageCount" to it.pageCount)
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
                    mapOf("name" to it.name, "url" to it.url, "mimeType" to it.mimeType,
                          "size" to it.size, "posterUrl" to it.posterUrl,
                          "pageUrls" to it.pageUrls, "pageCount" to it.pageCount)
                },
                "links" to links.map { mapOf("url" to it.url, "title" to it.title) },
                "updatedAt" to FieldValue.serverTimestamp()
            ) + (if (docDate != null) mapOf("docDate" to docDate) else emptyMap())
        ).await()
        // 관리자가 첨부를 빼고 저장한 경우, 더 이상 쓰이지 않는 파일을 지운다.
        // 포스터도 keep 에 넣어야 한다 — 빼면 남겨 둔 영상의 포스터가 지워져 썸네일만 깨진다.
        // PDF 쪽 그림도 마찬가지다. 빼면 글을 고쳐 저장만 해도 본문의 공문이 통째로 사라진다.
        val keep = attachments.flatMap { listOf(it.url, it.posterUrl) + it.pageUrls }.toSet()
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
    /**
     * [quizCorrect]/[quizTotal] 은 퀴즈를 푼 경우에만 들어온다. null 이면 두 필드를
     * 아예 넣지 않아 예전과 똑같은 문서가 남는다 — 안 푼 사람과 0점을 구분하기 위해서다.
     *
     * firestore.rules 는 손댈 필요가 없다: confirms 하위 컬렉션 규칙은
     * "본인 사번 문서인가"만 보고 필드는 검사하지 않으므로 필드가 늘어도 그대로 통과한다.
     */
    suspend fun confirmRead(
        id: String,
        empNo: String? = null,
        name: String? = null,
        quizCorrect: Int? = null,
        quizTotal: Int? = null
    ) {
        postsRef.document(id).update("confirms", FieldValue.increment(1)).await()
        if (empNo.isNullOrBlank()) return
        val data = mutableMapOf<String, Any>(
            "empNo" to empNo,
            "name" to (name?.trim() ?: ""),
            "at" to FieldValue.serverTimestamp()
        )
        if (quizCorrect != null && quizTotal != null) {
            data["quizCorrect"] = quizCorrect
            data["quizTotal"] = quizTotal
        }
        runCatching {
            postsRef.document(id).collection("confirms").document(empNo).set(data).await()
        }
    }

    /**
     * 사고사례 퀴즈 저장(관리자). 빈 리스트를 주면 빈 배열로 덮어써 퀴즈를 없앤다.
     *
     * firestore.rules 는 손댈 필요가 없다: posts update 규칙의 validPost() 는
     * request.resource.data — 즉 병합이 끝난 뒤의 문서 — 를 검사하므로,
     * quiz 필드만 바꿔도 문서에 이미 들어 있는 title/content 가 그대로 검사를 통과한다.
     */
    suspend fun saveQuiz(postId: String, quiz: List<QuizQuestion>) {
        auth.currentUser ?: throw IllegalStateException("관리자 로그인이 필요합니다")
        postsRef.document(postId).update(
            "quiz",
            quiz.map {
                mapOf(
                    "q" to it.q,
                    "choices" to it.choices,
                    "answer" to it.answer,
                    "explain" to it.explain
                )
            }
        ).await()
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

    /**
     * 댓글 작성. 목록에 보여줄 댓글 수도 함께 올린다.
     * [parentId] 가 비어 있으면 원댓글, 값이 있으면 그 댓글의 답글이다(1단계까지만).
     * commentCount 는 원댓글·답글을 구분하지 않는 총 개수다.
     */
    suspend fun addComment(
        postId: String,
        content: String,
        authorName: String,
        authorEmpNo: String,
        isAdmin: Boolean,
        parentId: String = ""
    ) {
        postsRef.document(postId).collection("comments").add(
            mapOf(
                "content" to content.trim(),
                "authorName" to authorName,
                "authorEmpNo" to authorEmpNo,
                "isAdmin" to isAdmin,
                "parentId" to parentId,
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
                at = d.getTimestamp("at"),
                // 퀴즈를 안 푼 사람(과 옛 기록)에는 필드 자체가 없다 → null 그대로 둔다.
                quizCorrect = d.getLong("quizCorrect")?.toInt(),
                quizTotal = d.getLong("quizTotal")?.toInt()
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
        // 포스터·PDF 쪽 그림도 같이 걷는다. 안 걷으면 글을 지워도 그것들만 Storage 에 영영 남는다.
        list.flatMap {
            listOf(it["url"] as? String, it["posterUrl"] as? String) +
                ((it["pageUrls"] as? List<*>)?.map { p -> p as? String } ?: emptyList())
        }.filterNotNull().filter { it.isNotBlank() }
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

        /**
         * 본문에 펼칠 PDF 쪽 수 상한. 운전정보 공문은 보통 1~3쪽이라 10쪽이면 넉넉하고,
         * 두꺼운 자료(교재 수십 쪽)를 통째로 그림으로 구우면 올리는 데만 몇 분이 걸린다.
         * 넘치는 쪽은 원문 PDF 로 보게 하고, 잘렸다는 사실은 화면에 표시한다.
         */
        const val MAX_PDF_PAGES = 10
    }
}
