package com.sinjeong.safety.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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
/**
 * 점호 항목 한 줄. 관리자가 붙여넣는 원문은 평평한 목록이 아니라
 * "발생개요 ▸ 원인 ▸ 재발방지" 식으로 들여쓴 보고서라, 단계를 잃으면 읽을 수가 없다.
 */
data class BriefingItem(
    val text: String = "",
    val level: Int = 0        // 0 = 최상위, 1·2 = 들여쓴 하위 항목
)

data class Briefing(
    val id: String = "",                   // "20260820"
    val dateText: String = "",             // 원문 날짜 줄 그대로: "'26.08.20.[목] 평→평"
    val items: List<BriefingItem> = emptyList(),
    val footer: String = "",               // "※ 지적확인 및 기본업무 준수로 안전운행 이행 철저"
    val raw: String = "",                  // 붙여넣은 원문 통째 (파싱이 어긋났을 때 대비)
    val attachments: List<Attachment> = emptyList(),
    val links: List<LinkAttachment> = emptyList(),
    val createdAt: Timestamp? = null
)

class BriefingRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val col = db.collection("briefings")

    /** 최근 것부터. 문서 id 가 곧 날짜라 id 내림차순이면 최신순이다. */
    fun briefingsFlow(limit: Long = 60): Flow<Result<List<Briefing>>> = callbackFlow {
        // 문서 id(__name__) 내림차순 정렬은 Firestore 가 별도 색인을 요구한다(FAILED_PRECONDITION).
        // 색인을 새로 배포하느니, 게시물과 똑같이 createdAt 으로 받아 오고(단일 필드라 색인이 자동)
        // 순서만 앱에서 문서 id 기준으로 다시 잡는다. 하루 한 건이라 정렬 비용은 없다시피 하고,
        // 등록 시각과 점호 날짜가 다를 수 있어(지난 자료를 나중에 올림) id 기준이 맞다.
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Result.failure(err)); return@addSnapshotListener
                }
                val list = (snap?.documents?.mapNotNull {
                    runCatching { toBriefing(it) }.getOrNull()
                } ?: emptyList()).sortedByDescending { it.id }
                trySend(Result.success(list))
            }
        awaitClose { reg.remove() }
    }

    /** 붙여넣은 원문을 나눠 briefings/{yyyyMMdd} 에 저장(덮어쓰기). */
    suspend fun save(raw: String, attachments: List<Attachment>, links: List<LinkAttachment>) {
        // 화면에서 이미 막지만, 규칙과 별개로 여기서도 한 번 더 본다.
        if (auth.currentUser?.email != CrewRepository.ADMIN_EMAIL) {
            throw IllegalStateException("관리자만 등록할 수 있습니다")
        }
        val b = parse(raw)
        col.document(b.id).set(
            mapOf(
                "dateText" to b.dateText,
                // 계층을 살리려면 문자열이 아니라 map 으로 넣어야 한다.
                "items" to b.items.map { mapOf("text" to it.text, "level" to it.level) },
                "footer" to b.footer,
                "raw" to b.raw,
                "attachments" to attachments.map {
                    mapOf("name" to it.name, "url" to it.url, "mimeType" to it.mimeType, "size" to it.size)
                },
                "links" to links.map { mapOf("url" to it.url, "title" to it.title) },
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

        /**
         * 관리자가 어떤 기호로 붙여넣든 항목으로 인식하도록 넉넉히 잡는다.
         * `*` 가 특히 중요하다 — 변환 도구를 거친 원문이 마크다운 불릿으로 온다.
         */
        private val BULLETS = charArrayOf('-', '–', '—', '‐', '·', '•', '*', '▪', '◦')

        /** LaTeX 조각. 관리자가 붙여넣는 텍스트는 변환 도구를 한 번 거쳐 온다. */
        private val LATEX = listOf(
            "\\rightarrow" to "→",
            "\\leftarrow" to "←",
            "\\times" to "×",
            "\\cdot" to "·",
            "\\to" to "→"
        )

        /** 공백 정리 + LaTeX 잔재 제거. 항목·날짜·맺음말 모두 이걸 거친다. */
        private fun clean(s: String): String {
            var t = s
            for ((from, to) in LATEX) t = t.replace(from, to)
            // 기호를 바꾸고 나면 감싸던 `$` 만 남는다. 수식이 아니므로 떼어낸다.
            t = t.replace("$", "")
            return t.replace(Regex("\\s+"), " ").trim()
        }

        /** 줄 앞 공백 폭. 탭은 스페이스 4칸으로 본다. */
        private fun indentOf(line: String): Int =
            line.takeWhile { it == ' ' || it == '\t' }
                .fold(0) { acc, c -> acc + if (c == '\t') 4 else 1 }

        /**
         * 한글 문서에서 복사한 원문을 날짜·항목·맺음말로 나눈다.
         *
         * 순수 함수다 — 화면에서 미리보기로 그대로 불러 쓸 수 있어야 하고,
         * 저장 없이 결과를 확인할 수 있어야 하기 때문이다.
         */
        internal fun parse(raw: String): Briefing {
            // trimmed = 불릿이 붙은 채로의 줄(날짜 판별 방어에 필요), text = 다 걷어낸 알맹이
            val indents = ArrayList<Int>()
            val trimmed = ArrayList<String>()
            val texts = ArrayList<String>()
            for (line in raw.lines()) {
                if (line.isBlank()) continue
                indents.add(indentOf(line))
                val t = line.trim()
                trimmed.add(t)
                texts.add(clean(t.trimStart(*BULLETS).trim()))
            }

            val dateIdx = texts.indexOfFirst { DATE_RE.containsMatchIn(it) }
            val m = if (dateIdx >= 0) DATE_RE.find(texts[dateIdx]) else null

            val id = if (m != null) {
                "20" + m.groupValues[1] + m.groupValues[2] + m.groupValues[3]
            } else {
                SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            }

            // 날짜 줄이 없으면 오늘 것으로 본다. 이때 첫 줄이 지적 항목이면
            // 그걸 날짜로 삼아 삼켜버리므로, 원문과 같은 모양으로 오늘 날짜를 만들어 넣는다.
            val headLine = trimmed.firstOrNull()
            val useFirstAsDate = dateIdx < 0 && headLine != null &&
                headLine.first() !in BULLETS && !headLine.startsWith("※")
            val dateText = when {
                dateIdx >= 0 -> texts[dateIdx]
                useFirstAsDate -> texts.first()
                else -> SimpleDateFormat("''yy.MM.dd.", Locale.KOREA).format(Date())
            }
            val skipIdx = if (dateIdx >= 0) dateIdx else if (useFirstAsDate) 0 else -1

            // 맺음말(※)은 불릿을 떼어낸 뒤에 봐야 한다. "* ※ …" 로 오는 원문이 있다.
            val bodyIdx = texts.indices.filter {
                it != skipIdx && texts[it].isNotEmpty() && !texts[it].startsWith("※")
            }
            // 들여쓰기 폭은 문서마다 다르다(2칸·3칸·4칸). 값 자체가 아니라
            // 등장한 값들을 작은 것부터 세운 "순위"로 단계를 매겨야 어느 문서든 맞는다.
            val depths = bodyIdx.map { indents[it] }.distinct().sorted()
            val items = bodyIdx.map {
                BriefingItem(texts[it], depths.indexOf(indents[it]).coerceAtMost(2))
            }

            val footer = texts.filter { it.startsWith("※") }.joinToString("\n")

            return Briefing(id = id, dateText = dateText, items = items, footer = footer, raw = raw)
        }

        /**
         * 문서 → Briefing.
         *
         * items 는 계층이 없던 시절 문자열 배열로 저장했다. toObject 로 한 번에 담으면
         * 그 문서들이 통째로 버려지므로(이미 올라간 오늘 자료가 그 모양이다) 손으로 읽는다.
         */
        @Suppress("UNCHECKED_CAST")
        private fun toBriefing(d: DocumentSnapshot): Briefing {
            val items = (d.get("items") as? List<Any?>)?.mapNotNull { v ->
                when (v) {
                    is String -> BriefingItem(v, 0)
                    is Map<*, *> -> {
                        val mm = v as Map<String, Any?>
                        BriefingItem(mm["text"] as? String ?: "", (mm["level"] as? Number)?.toInt() ?: 0)
                    }
                    else -> null
                }
            } ?: emptyList()

            val atts = (d.get("attachments") as? List<Any?>)
                ?.filterIsInstance<Map<*, *>>()
                ?.map { it as Map<String, Any?> }
                ?.map {
                    Attachment(
                        name = it["name"] as? String ?: "",
                        url = it["url"] as? String ?: "",
                        mimeType = it["mimeType"] as? String ?: "",
                        size = (it["size"] as? Number)?.toLong() ?: 0L
                    )
                } ?: emptyList()

            val links = (d.get("links") as? List<Any?>)
                ?.filterIsInstance<Map<*, *>>()
                ?.map { it as Map<String, Any?> }
                ?.map {
                    LinkAttachment(
                        url = it["url"] as? String ?: "",
                        title = it["title"] as? String ?: ""
                    )
                } ?: emptyList()

            return Briefing(
                id = d.id,
                dateText = d.getString("dateText") ?: "",
                items = items,
                footer = d.getString("footer") ?: "",
                raw = d.getString("raw") ?: "",
                attachments = atts,
                links = links,
                createdAt = d.getTimestamp("createdAt")
            )
        }
    }
}
