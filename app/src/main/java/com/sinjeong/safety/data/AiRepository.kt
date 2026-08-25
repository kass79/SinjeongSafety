package com.sinjeong.safety.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * 서버(Cloud Functions)의 AI 기능 호출.
 * API 키는 서버 금고에만 있다 — 앱에는 아무 비밀도 없다.
 * 로그인하지 않으면 서버가 거부하므로 호출 전에 로그인 상태를 확인한다.
 */
class AiRepository {
    private val fn = FirebaseFunctions.getInstance("asia-northeast3")

    suspend fun askRegulation(question: String, articles: List<Map<String, String>>): String {
        val data = hashMapOf("question" to question, "articles" to articles)
        val result = fn.getHttpsCallable("askRegulation").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<String, Any?>)?.get("answer") as? String
            ?: throw IllegalStateException("응답이 비었습니다")
    }

    suspend fun summarizePost(title: String, content: String): String {
        val data = hashMapOf("title" to title, "content" to content)
        val result = fn.getHttpsCallable("summarizePost").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return (result.data as? Map<String, Any?>)?.get("summary") as? String
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
