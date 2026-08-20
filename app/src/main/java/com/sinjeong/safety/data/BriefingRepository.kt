package com.sinjeong.safety.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 출무점호 (Firestore 컬렉션: "briefings")
 *
 * 문서 id 를 yyyyMMdd 로 못 박는다. 하루에 한 건이므로 같은 날 다시 올리면
 * 덮어쓰는 게 자연스럽고, 정렬도 id 순 하나로 끝난다(별도 날짜 필드 정렬 불필요).
 */
data class Briefing(
    @DocumentId val id: String = "",       // "20260820"
    val dateText: String = "",             // 원문 날짜 줄 그대로: "'26.08.20.[목] 평→평"
    val items: List<String> = emptyList(), // "3호선 구파발 하선 객실등 미점등"
    val footer: String = "",               // "※ 지적확인 및 기본업무 준수로 안전운행 이행 철저"
    val raw: String = "",                  // 붙여넣은 원문 통째 (파싱이 어긋났을 때 대비)
    val createdAt: Timestamp? = null
)

class BriefingRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val col = db.collection("briefings")

    /** 최근 것부터. 문서 id 가 곧 날짜라 id 내림차순이면 최신순이다. */
    fun briefingsFlow(limit: Long = 60): Flow<Result<List<Briefing>>> = callbackFlow {
        val reg = col.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Result.failure(err)); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull {
                    runCatching { it.toObject(Briefing::class.java) }.getOrNull()
                } ?: emptyList()
                trySend(Result.success(list))
            }
        awaitClose { reg.remove() }
    }

    /** 붙여넣은 원문을 나눠 briefings/{yyyyMMdd} 에 저장(덮어쓰기). */
    suspend fun save(raw: String) {
        // 화면에서 이미 막지만, 규칙과 별개로 여기서도 한 번 더 본다.
        if (auth.currentUser?.email != CrewRepository.ADMIN_EMAIL) {
            throw IllegalStateException("관리자만 등록할 수 있습니다")
        }
        val b = parse(raw)
        col.document(b.id).set(
            mapOf(
                "dateText" to b.dateText,
                "items" to b.items,
                "footer" to b.footer,
                "raw" to b.raw,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun delete(id: String) {
        col.document(id).delete().await()
    }

    companion object {
        /** "'26.08.20.[목]" 같은 줄에서 yy/MM/dd 를 집는다. 공백이 끼어도 걸리게. */
        private val DATE_RE = Regex("""(\d{2})\.\s*(\d{2})\.\s*(\d{2})\.""")

        /** 관리자가 어떤 기호로 붙여넣든 항목으로 인식하도록 넉넉히 잡는다. */
        private val BULLETS = charArrayOf('-', '–', '—', '‐', '·', '•')

        /**
         * 한글 문서에서 복사한 원문을 날짜·항목·맺음말로 나눈다.
         *
         * 순수 함수다 — 화면에서 미리보기로 그대로 불러 쓸 수 있어야 하고,
         * 저장 없이 결과를 확인할 수 있어야 하기 때문이다.
         */
        internal fun parse(raw: String): Briefing {
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val dateIdx = lines.indexOfFirst { DATE_RE.containsMatchIn(it) }
            val m = if (dateIdx >= 0) DATE_RE.find(lines[dateIdx]) else null

            val id = if (m != null) {
                "20" + m.groupValues[1] + m.groupValues[2] + m.groupValues[3]
            } else {
                SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            }

            // 날짜 줄이 없으면 오늘 것으로 본다. 이때 첫 줄이 지적 항목이면
            // 그걸 날짜로 삼아 삼켜버리므로, 원문과 같은 모양으로 오늘 날짜를 만들어 넣는다.
            val headLine = lines.firstOrNull()
            val useFirstAsDate = dateIdx < 0 && headLine != null &&
                headLine.first() !in BULLETS && !headLine.startsWith("※")
            val dateText = when {
                dateIdx >= 0 -> lines[dateIdx]
                useFirstAsDate -> headLine!!
                else -> SimpleDateFormat("''yy.MM.dd.", Locale.KOREA).format(Date())
            }
            val skipIdx = if (dateIdx >= 0) dateIdx else if (useFirstAsDate) 0 else -1

            val items = lines
                .filterIndexed { i, l -> i != skipIdx && !l.startsWith("※") }
                .map { it.trimStart(*BULLETS).trim() }
                .filter { it.isNotEmpty() }

            val footer = lines.filter { it.startsWith("※") }.joinToString("\n")

            return Briefing(id = id, dateText = dateText, items = items, footer = footer, raw = raw)
        }
    }
}
