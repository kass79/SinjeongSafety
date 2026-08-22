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
}
