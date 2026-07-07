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
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

/**
 * 링크 첨부 (본문과 별개로 붙이는 URL. 유튜브면 썸네일 자동 표시)
 */
data class LinkAttachment(
    val url: String = "",
    val title: String = ""
) {
    /** 유튜브 영상 ID 추출 (없으면 null) */
    val youtubeId: String? get() {
        val patterns = listOf(
            Regex("""youtu\.be/([\w-]{11})"""),
            Regex("""[?&]v=([\w-]{11})"""),
            Regex("""youtube\.com/embed/([\w-]{11})""")
        )
        for (re in patterns) re.find(url)?.let { return it.groupValues[1] }
        return null
    }
    val isYoutube: Boolean get() = youtubeId != null
    val thumbnailUrl: String? get() = youtubeId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
    val displayHost: String get() = url
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .substringBefore("/")
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
    val links: List<LinkAttachment> = emptyList(),
    val views: Long = 0,
    val pinned: Boolean = false,        // 상단 고정
    val confirms: Long = 0,             // 확인(읽음) 인원 수
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

object Categories {
    const val HUMAN_ERROR = "인적오류 주의개소"
    const val EDU_VIDEO = "교육영상"
    const val REGULATION = "운전규정"
    const val NOTICE = "전달사항"
    val ALL = listOf(HUMAN_ERROR, EDU_VIDEO, REGULATION, NOTICE)

    /** 카드/칩에 쓸 짧은 이름 */
    fun short(category: String): String = when (category) {
        HUMAN_ERROR -> "인적오류"
        else -> category
    }
}

object Tags {
    const val ALL = "전체"
    const val SAFETY_EDU = "안전교육"
    const val OPERATION = "운행지시"
    const val GENERAL = "일반전달"
    val FILTERS = listOf(ALL, SAFETY_EDU, OPERATION, GENERAL)
    val SELECTABLE = listOf(SAFETY_EDU, OPERATION, GENERAL)
}
