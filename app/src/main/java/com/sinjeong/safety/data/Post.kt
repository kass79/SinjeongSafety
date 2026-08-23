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
    val commentCount: Long = 0,         // 댓글 수. 목록에서 보여주려고 세어 둔다(옛 글에는 없다)
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    /**
     * 자료 날짜. 관리자가 지정하는 "그 자료가 만들어진 날"이다.
     * 올린 시각(createdAt)과 구분해야, 과거 자료를 나중에 올려도
     * 피드 맨 위로 튀어나오지 않는다. 예전 글에는 이 값이 없다.
     */
    val docDate: Timestamp? = null
)

/** 정렬·표시에 쓰는 기준 날짜. 자료 날짜가 없으면 올린 시각으로 대체한다. */
val Post.effectiveDate: Timestamp?
    get() = docDate ?: createdAt

/**
 * 댓글 1건.  posts/{postId}/comments/{자동id}  문서.
 * 실명제라 질의응답의 Answer 와 같은 모양으로 둔다(표기 규칙을 한 곳에 맞춘다).
 */
data class Comment(
    @DocumentId val id: String = "",
    val content: String = "",
    val authorName: String = "",
    val authorEmpNo: String = "",
    val isAdmin: Boolean = false,
    val createdAt: Timestamp? = null,
    /**
     * 답글이면 그 부모 댓글의 id, 원댓글이면 빈 문자열.
     * 옛 댓글 문서에는 이 필드가 아예 없으므로 기본값(빈 문자열)이 곧 "원댓글"이 되어
     * 그대로 호환된다 — 마이그레이션 없이 섞여 있어도 된다.
     * 깊이는 1단계까지만 쓴다(답글의 답글 없음). 좁은 폰에서 스레드가 깊어지면
     * 읽기 어렵고, 현장 게시판에 그만큼의 깊이가 필요하지 않다.
     */
    val parentId: String = ""
)

/**
 * 확인 기록 1건.  posts/{postId}/confirms/{사번}  문서.
 * 옛 버전은 인원 수(Post.confirms)만 세었으므로 그 시절 기록에는 사람이 없다.
 */
data class CrewConfirm(
    val empNo: String = "",
    val name: String = "",
    val at: Timestamp? = null
)

/** 확인 현황 화면에 뿌릴 집계. */
data class ConfirmReport(
    val confirmed: List<CrewConfirm> = emptyList(),  // 확인한 사람 (최근 순)
    val pending: List<CrewConfirm> = emptyList(),    // 아직 안 본 사람 (사번 순)
    val anonymous: Long = 0                          // 이름 없이 수만 센 옛 기록
) {
    val total: Int get() = confirmed.size + pending.size
}

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
