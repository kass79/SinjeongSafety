package com.sinjeong.safety.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * 게시물 데이터 모델 (Firestore 컬렉션: "posts")
 */
/**
 * 첨부파일 (Firebase Storage에 업로드된 파일 메타데이터)
 */
data class Attachment(
    val name: String = "",
    val url: String = "",
    val mimeType: String = "",
    val size: Long = 0L
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

data class Post(
    @DocumentId val id: String = "",
    val category: String = "",      // Categories 중 하나
    val tag: String = "",           // Tags 중 하나 (전체 제외)
    val title: String = "",
    val content: String = "",
    val authorName: String = "관리자",
    val authorUid: String = "",
    val attachments: List<Attachment> = emptyList(),
    val views: Long = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

object Categories {
    const val HUMAN_ERROR = "인적오류 주의개소"
    const val EDU_VIDEO = "교육영상"
    const val REGULATION = "운전규정"
    const val NOTICE = "전달사항"
    val ALL = listOf(HUMAN_ERROR, EDU_VIDEO, REGULATION, NOTICE)
}

object Tags {
    const val ALL = "전체"
    const val SAFETY_EDU = "안전교육"
    const val OPERATION = "운행지시"
    const val GENERAL = "일반전달"
    val FILTERS = listOf(ALL, SAFETY_EDU, OPERATION, GENERAL)
    val SELECTABLE = listOf(SAFETY_EDU, OPERATION, GENERAL)
}
