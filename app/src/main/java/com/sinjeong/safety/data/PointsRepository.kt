package com.sinjeong.safety.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

/**
 * 승무원 참여 포인트 집계 (관리자 전용).
 *
 * 점수: 확인 1 / 퀴즈 정답 1 / 댓글 1 / 답변 2.
 * 관리자 본인이 쓴 댓글·답변(isAdmin)은 승무원 참여가 아니므로 뺀다.
 *
 * 주의: 여기 쓰는 세 쿼리는 전부 collectionGroup + 범위 조회다.
 * 일반 컬렉션과 달리 Firestore가 **컬렉션 그룹 전용 색인을 따로** 요구한다
 * (`firestore.indexes.json` 참고). 색인이 없으면 FAILED_PRECONDITION 이 나는데,
 * 예전에 이걸 조용히 삼켜서 "출무점호가 안 보임" 사고가 났다. 그래서 실패는 전부 로그로 남긴다.
 */
data class CrewPoints(
    val empNo: String = "",
    val name: String = "",
    /** 확인 완료 건수 (1건 1점) */
    val confirms: Int = 0,
    /** 퀴즈 정답 수 (1개 1점) */
    val quiz: Int = 0,
    /** 댓글 수 (1건 1점) */
    val comments: Int = 0,
    /** 답변 수 (1건 2점) */
    val answers: Int = 0
) {
    val total: Int get() = confirms + quiz + comments + answers * 2
}

class PointsRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * 그 달(현지 시간 기준)의 사번별 포인트. 합계 내림차순.
     * 한 쿼리라도 실패하면 그 항목만 0으로 남고 나머지는 그대로 집계된다(로그 확인할 것).
     */
    suspend fun monthlyPoints(month: YearMonth): List<CrewPoints> {
        val zone = ZoneId.systemDefault()
        val start = Timestamp(Date.from(month.atDay(1).atStartOfDay(zone).toInstant()))
        val end = Timestamp(Date.from(month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()))

        val confirms = mutableMapOf<String, Int>()
        val quiz = mutableMapOf<String, Int>()
        val comments = mutableMapOf<String, Int>()
        val answers = mutableMapOf<String, Int>()
        val names = mutableMapOf<String, String>()

        // ── 확인 완료 + 퀴즈 정답 ──────────────────────────
        for (doc in queryRange("confirms", "at", start, end)) {
            val empNo = doc.getString("empNo")?.trim().orEmpty()
            if (empNo.isBlank()) continue          // 이름/사번 없이 수만 세던 옛 기록
            confirms[empNo] = (confirms[empNo] ?: 0) + 1
            quiz[empNo] = (quiz[empNo] ?: 0) + (doc.getLong("quizCorrect")?.toInt() ?: 0)
            doc.getString("name")?.takeIf { it.isNotBlank() }?.let { names.putIfAbsent(empNo, it) }
        }

        // ── 댓글 ────────────────────────────────────────
        for (doc in queryRange("comments", "createdAt", start, end)) {
            if (doc.getBoolean("isAdmin") == true) continue
            val empNo = doc.getString("authorEmpNo")?.trim().orEmpty()
            if (empNo.isBlank()) continue
            comments[empNo] = (comments[empNo] ?: 0) + 1
            doc.getString("authorName")?.takeIf { it.isNotBlank() }
                ?.let { names.putIfAbsent(empNo, it) }
        }

        // ── 질의응답 답변 ────────────────────────────────
        for (doc in queryRange("answers", "createdAt", start, end)) {
            if (doc.getBoolean("isAdmin") == true) continue
            val empNo = doc.getString("authorEmpNo")?.trim().orEmpty()
            if (empNo.isBlank()) continue
            answers[empNo] = (answers[empNo] ?: 0) + 1
            doc.getString("authorName")?.takeIf { it.isNotBlank() }
                ?.let { names.putIfAbsent(empNo, it) }
        }

        // 이름표는 config/rosterNames 를 우선하고, 없으면 기록에 남은 이름으로 채운다.
        // 그 문서는 CrewRepository 가 명단 관리에 쓰면서 같이 들고 있으니 거기서 읽는다.
        val roster = CrewRepository().rosterNames()

        return (confirms.keys + comments.keys + answers.keys)
            .map { empNo ->
                CrewPoints(
                    empNo = empNo,
                    name = roster[empNo] ?: names[empNo] ?: "이름 미등록",
                    confirms = confirms[empNo] ?: 0,
                    quiz = quiz[empNo] ?: 0,
                    comments = comments[empNo] ?: 0,
                    answers = answers[empNo] ?: 0
                )
            }
            .sortedWith(compareByDescending<CrewPoints> { it.total }.thenBy { it.empNo })
    }

    /** 한 컬렉션 그룹의 그 달치 문서. 실패하면 빈 목록 + 로그(색인 누락이 여기서 잡힌다). */
    private suspend fun queryRange(
        group: String,
        field: String,
        start: Timestamp,
        end: Timestamp
    ) = try {
        db.collectionGroup(group)
            .whereGreaterThanOrEqualTo(field, start)
            .whereLessThan(field, end)
            .get().await().documents
    } catch (e: Exception) {
        Log.e("PointsRepository", "$group 조회 실패 — 컬렉션 그룹 색인($field)이 없을 수 있음", e)
        emptyList()
    }
}
