package com.sinjeong.safety.data

import android.content.Context
import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 질의응답 (Firestore 컬렉션: "questions", 답변은 하위 컬렉션 "answers")
 *
 * 안전정보 게시판(posts)과 완전히 분리한다. 공식 안전정보와 승무원 질문이
 * 같은 목록에 섞이면 급할 때 무엇을 봐야 할지 헷갈리기 때문이다.
 */
data class Question(
    @DocumentId val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorName: String = "",
    val authorEmpNo: String = "",
    val images: List<Attachment> = emptyList(),   // 사진만, 최대 3장
    val links: List<LinkAttachment> = emptyList(),
    val answerCount: Long = 0,
    val views: Long = 0,
    val createdAt: Timestamp? = null
)

data class Answer(
    @DocumentId val id: String = "",
    val content: String = "",
    val authorName: String = "",
    val authorEmpNo: String = "",
    val isAdmin: Boolean = false,     // 관리자 답변이면 파란 체크 뱃지
    val createdAt: Timestamp? = null,
    /**
     * 답글이면 그 부모 답변의 id, 원답변이면 빈 문자열. 게시물 댓글(Comment.parentId)과 같은 규칙이다.
     * 옛 답변 문서에는 이 필드가 아예 없으므로 기본값(빈 문자열)이 곧 "원답변"이 되어
     * 그대로 호환된다 — 마이그레이션 없이 섞여 있어도 된다.
     * 깊이는 1단계까지만 쓴다(답글의 답글 없음). 게시물 댓글과 다르게 동작하면
     * 같은 앱 안에서 두 게시판이 따로 노는 셈이라 더 헷갈린다.
     */
    val parentId: String = ""
)

class QuestionRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val col = db.collection("questions")

    companion object {
        const val MAX_IMAGES = 3
    }

    /** 질문 목록 (최신순) */
    fun questionsFlow(): Flow<Result<List<Question>>> = callbackFlow {
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Result.failure(err)); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull {
                    runCatching { it.toObject(Question::class.java) }.getOrNull()
                } ?: emptyList()
                trySend(Result.success(list))
            }
        awaitClose { reg.remove() }
    }

    /** 특정 질문의 답변 목록 (오래된 순 — 대화 흐름대로 읽히도록) */
    fun answersFlow(questionId: String): Flow<Result<List<Answer>>> = callbackFlow {
        val reg = col.document(questionId).collection("answers")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Result.failure(err)); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull {
                    runCatching { it.toObject(Answer::class.java) }.getOrNull()
                } ?: emptyList()
                trySend(Result.success(list))
            }
        awaitClose { reg.remove() }
    }

    suspend fun getQuestion(id: String): Question? =
        runCatching { col.document(id).get().await().toObject(Question::class.java) }.getOrNull()

    /** 질문 작성 */
    suspend fun addQuestion(
        title: String,
        content: String,
        authorName: String,
        authorEmpNo: String,
        images: List<Attachment>,
        links: List<LinkAttachment>
    ) {
        col.add(
            mapOf(
                "title" to title.trim(),
                "content" to content.trim(),
                "authorName" to authorName,
                "authorEmpNo" to authorEmpNo,
                "images" to images.take(MAX_IMAGES).map {
                    mapOf("name" to it.name, "url" to it.url,
                          "mimeType" to it.mimeType, "size" to it.size)
                },
                "links" to links.map { mapOf("url" to it.url, "title" to it.title) },
                "answerCount" to 0,
                "views" to 0,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * 답변 작성. 질문의 답변 수도 함께 올린다.
     * [parentId] 가 비어 있으면 원답변, 값이 있으면 그 답변의 답글이다(1단계까지만).
     * answerCount 는 게시물의 commentCount 와 마찬가지로 원답변·답글을 구분하지 않는 총 개수다
     * — 화면이 보여 주는 "답변 N"(목록 전체 개수)과 어긋나지 않게 하려면 같이 세야 한다.
     */
    suspend fun addAnswer(
        questionId: String,
        content: String,
        authorName: String,
        authorEmpNo: String,
        isAdmin: Boolean,
        parentId: String = ""
    ) {
        col.document(questionId).collection("answers").add(
            mapOf(
                "content" to content.trim(),
                "authorName" to authorName,
                "authorEmpNo" to authorEmpNo,
                "isAdmin" to isAdmin,
                "parentId" to parentId,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        runCatching {
            col.document(questionId).update("answerCount", FieldValue.increment(1)).await()
        }
    }

    /** 삭제는 관리자만. 규칙에서도 막혀 있다. */
    suspend fun deleteQuestion(id: String) {
        // 답변부터 지우고 질문을 지운다 (하위 문서는 자동으로 지워지지 않는다)
        runCatching {
            val answers = col.document(id).collection("answers").get().await()
            for (doc in answers.documents) doc.reference.delete().await()
        }
        col.document(id).delete().await()
    }

    /**
     * 답변 1건 삭제. 그 답변에 달린 답글은 함께 지우지 않는다 —
     * 게시물 댓글과 같은 처리다. 남은 답글은 부모를 잃어 어디에도 못 붙으므로
     * 화면(QuestionDetailScreen)이 맨 아래에 원답변처럼 그려서 사라지지 않게 한다.
     * 같이 지우면 남의 글까지 관리자가 소리 없이 날리는 셈이라 그렇게 하지 않는다.
     */
    suspend fun deleteAnswer(questionId: String, answerId: String) {
        col.document(questionId).collection("answers").document(answerId).delete().await()
        runCatching {
            col.document(questionId).update("answerCount", FieldValue.increment(-1)).await()
        }
    }

    suspend fun incrementViews(id: String) {
        runCatching { col.document(id).update("views", FieldValue.increment(1)).await() }
    }

    fun isAdminAccount(): Boolean = auth.currentUser?.email == CrewRepository.ADMIN_EMAIL
}
