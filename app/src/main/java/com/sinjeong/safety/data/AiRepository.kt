package com.sinjeong.safety.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

// ── askGuide 주고받는 모양 (서버 계약) ──────────────────────────
/** AI 에게 넘기는 근거 한 덩어리. [id] 는 요청 안에서 유일해야 한다. */
data class GuideSource(
    val id: String,
    val kind: String,      // "reg" | "manual"
    val label: String,     // 화면에 보일 출처 이름 (규정집·조번 또는 섹션명)
    val title: String,
    val body: String
) {
    fun toMap(): Map<String, String> =
        mapOf("id" to id, "kind" to kind, "label" to label, "title" to title, "body" to body)
}

/** 인용문 안에서 색칠할 구간. kind = action | contact | caution */
data class GuideHighlight(val text: String, val kind: String)

data class GuideStep(
    val quote: String,
    val sourceId: String,
    val verified: Boolean,
    val highlights: List<GuideHighlight>
)

data class GuideAnswer(
    val found: Boolean,
    val conclusion: String,
    val steps: List<GuideStep>,
    val cautions: List<String>
)

/**
 * 서버(Cloud Functions)의 AI 기능 호출.
 * API 키는 서버 금고에만 있다 — 앱에는 아무 비밀도 없다.
 * 로그인하지 않으면 서버가 거부하므로 호출 전에 로그인 상태를 확인한다.
 */
class AiRepository {
    private val fn = FirebaseFunctions.getInstance("asia-northeast3")

    /**
     * 규정·비상대응 매뉴얼 통합 답변.
     * 서버가 준 값이 한 칸이라도 비어 있을 수 있으므로 전부 방어적으로 읽는다 —
     * 캐스팅 하나가 터지면 화면에는 "AI 실패" 만 남고 원인을 알 수 없게 된다.
     */
    suspend fun askGuide(question: String, mode: String, sources: List<GuideSource>): GuideAnswer {
        val data = hashMapOf(
            "question" to question,
            "mode" to mode,
            "sources" to sources.map { it.toMap() }
        )
        val result = fn.getHttpsCallable("askGuide").call(data).await()
        @Suppress("UNCHECKED_CAST")
        val root = result.data as? Map<String, Any?>
            ?: throw IllegalStateException("응답이 비었습니다")

        @Suppress("UNCHECKED_CAST")
        val rawSteps = root["steps"] as? List<Map<String, Any?>>
        val steps = rawSteps.orEmpty().mapNotNull { s ->
            val quote = s["quote"] as? String ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val rawHl = s["highlights"] as? List<Map<String, Any?>>
            GuideStep(
                quote = quote,
                sourceId = s["sourceId"] as? String ?: "",
                verified = s["verified"] as? Boolean ?: false,
                highlights = rawHl.orEmpty().mapNotNull { h ->
                    val t = h["text"] as? String
                    if (t.isNullOrBlank()) null
                    else GuideHighlight(t, h["kind"] as? String ?: "action")
                }
            )
        }
        return GuideAnswer(
            found = root["found"] as? Boolean ?: false,
            conclusion = root["conclusion"] as? String ?: "",
            steps = steps,
            cautions = (root["cautions"] as? List<*>)
                ?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
                .orEmpty()
        )
    }

    suspend fun summarizePost(title: String, content: String): String {
        val data = hashMapOf("title" to title, "content" to content)
        val result = fn.getHttpsCallable("summarizePost").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<String, Any?>)?.get("summary") as? String
            ?: throw IllegalStateException("응답이 비었습니다")
    }

    /**
     * 공문 사진(base64 JPEG) → 정리된 본문 텍스트.
     * 서버가 사진 1~3장, 한 장당 base64 5MB 까지만 받는다 — 부르는 쪽에서 줄여 보낸다.
     * 결과는 초안이다. 곧바로 저장하지 않고 화면이 관리자에게 먼저 보여 준다.
     */
    suspend fun extractImageText(images: List<String>): String {
        val data = hashMapOf("images" to images)
        val result = fn.getHttpsCallable("extractImageText").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<String, Any?>)?.get("text") as? String
            ?: throw IllegalStateException("응답이 비었습니다")
    }

    /**
     * 사고사례 퀴즈 초안(보통 2문제). 결과는 바로 저장하지 않고 관리자가 검토한다.
     * 정답 번호는 서버가 Double 로 줄 수도 있어 Number 로 받고,
     * 보기 개수를 벗어난 값이 와도 화면이 깨지지 않도록 범위 안으로 조인다.
     */
    suspend fun generateQuiz(title: String, content: String): List<QuizQuestion> {
        val data = hashMapOf("title" to title, "content" to content)
        val result = fn.getHttpsCallable("generateQuiz").call(data).await()
        @Suppress("UNCHECKED_CAST")
        val raw = (result.data as? Map<String, Any?>)?.get("questions") as? List<Map<String, Any?>>
        val quiz = raw.orEmpty().mapNotNull { item ->
            val text = item["q"] as? String
            val choices = (item["choices"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            if (text.isNullOrBlank() || choices.size < 2) return@mapNotNull null
            QuizQuestion(
                q = text,
                choices = choices,
                answer = ((item["answer"] as? Number)?.toInt() ?: 0).coerceIn(0, choices.size - 1),
                explain = item["explain"] as? String ?: ""
            )
        }
        if (quiz.isEmpty()) throw IllegalStateException("응답이 비었습니다")
        return quiz
    }
}
