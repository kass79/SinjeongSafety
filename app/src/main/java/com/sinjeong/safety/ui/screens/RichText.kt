package com.sinjeong.safety.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.ui.theme.AppColors

// ── 본문 강조 표기 ────────────────────────────────────────────────
//
// Firestore 는 본문을 평문 문자열로 들고 있다. 그래서 강조는 글자 안에 심는다.
//   **중요한 대목**  → 형광펜(노란 배경)
//   __기준값 25km/h__ → 밑줄
//   https://...       → 파란 링크 (기존 동작 그대로)
//
// 설계상 지킨 것 셋:
//  ① 표기가 없으면 지금까지 올라간 글이 그대로 평문으로 나온다(옛 게시물 호환).
//  ② 짝이 안 맞는 마커는 '그냥 글자'다. 지우지 않는다 — 어떤 입력이든 글자가 사라지지 않는다.
//  ③ 마커는 한 줄 안에서만 짝을 찾는다. 여러 줄을 넘나들게 하면 본문 첫 줄의 `**` 하나가
//     글 전체를 형광펜으로 칠해 버린다(마크다운에서 흔한 사고). 피해를 한 줄로 가둔다.

/** 강조 종류. URL 은 기존 링크 동작을 그대로 유지하려고 같은 파서에서 함께 잡는다. */
enum class RichKind { HIGHLIGHT, UNDERLINE, URL }

/** 마커를 걷어낸 [RichText.text] 기준의 구간. */
data class RichSpan(val start: Int, val end: Int, val kind: RichKind)

/** 파싱 결과. [text] 에는 마커(`**`, `__`)가 빠져 있고 [spans] 는 그 좌표계를 쓴다. */
data class RichText(val text: String, val spans: List<RichSpan>)

// URL 이 먼저다. 주소 안의 `__` 는 마커가 아니라 주소의 일부다(정규식 교대는 왼쪽 우선).
// `(.+?)` 는 기본적으로 개행을 먹지 않으므로 짝 찾기가 한 줄로 제한된다 — 위 ③.
private val TOKEN = Regex("""https?://[^\s]+|\*\*(.+?)\*\*|__(.+?)__""")

/** 강조 안의 강조는 한 겹까지만(`**__둘 다__**`). 그 이상은 마커째 글자로 보여준다. */
private const val MAX_DEPTH = 2

/**
 * 순수 함수. 화면·색과 무관하므로 이 함수만 따로 검증하면 된다.
 *
 * 어떤 입력에도 글자를 잃지 않는다: 마커로 인정된 짝만 결과에서 빠지고,
 * 짝을 못 찾은 `**` / `__` 는 원문 그대로 붙는다.
 */
fun parseRichText(src: String): RichText {
    if (src.isEmpty()) return RichText(src, emptyList())
    val sb = StringBuilder(src.length)
    val spans = ArrayList<RichSpan>()
    walk(src, sb, spans, 0)
    return RichText(sb.toString(), spans)
}

private fun walk(src: String, sb: StringBuilder, spans: MutableList<RichSpan>, depth: Int) {
    var last = 0
    for (m in TOKEN.findAll(src)) {
        sb.append(src, last, m.range.first)          // 마커 사이의 평범한 글자
        val start = sb.length
        val hi = m.groups[1]
        val ul = m.groups[2]
        when {
            // URL — 통째로 원문 그대로. 안쪽을 다시 훑지 않는다.
            hi == null && ul == null -> {
                sb.append(m.value)
                spans += RichSpan(start, sb.length, RichKind.URL)
            }
            // 너무 겹쳤다. 마커를 글자로 보여주는 쪽을 택한다(사라지는 것보다 낫다).
            depth >= MAX_DEPTH -> sb.append(m.value)
            // 안쪽을 먼저 훑는다 — 형광펜 안의 URL·밑줄이 공짜로 살아난다.
            hi != null -> {
                walk(hi.value, sb, spans, depth + 1)
                spans += RichSpan(start, sb.length, RichKind.HIGHLIGHT)
            }
            else -> {
                walk(ul!!.value, sb, spans, depth + 1)
                spans += RichSpan(start, sb.length, RichKind.UNDERLINE)
            }
        }
        last = m.range.last + 1
    }
    sb.append(src, last, src.length)                 // 마지막 마커 뒤 나머지
}

/**
 * 파싱 결과에 색을 입힌다.
 *
 * @param links URL 을 파란 링크로 칠하고 탭 주석을 달지 여부.
 *   피드 카드는 false — 링크를 눌리게 하면 '카드 탭해서 상세 열기'와 싸운다.
 */
@Composable
fun rememberRichText(src: String, links: Boolean = true): AnnotatedString {
    val hiBg = AppColors.HighlightBg
    val hiFg = AppColors.HighlightFg
    val linkColor = AppColors.Primary
    return remember(src, links, hiBg, hiFg, linkColor) {
        val parsed = parseRichText(src)
        buildAnnotatedString {
            append(parsed.text)
            for (s in parsed.spans) when (s.kind) {
                // 배경만 칠하면 다크에서 글자가 묻힌다. 글자색까지 같이 정한다.
                RichKind.HIGHLIGHT -> addStyle(
                    SpanStyle(background = hiBg, color = hiFg, fontWeight = FontWeight.SemiBold),
                    s.start, s.end
                )
                // 밑줄만이면 링크(파랑+굵게+밑줄)와 헷갈린다. 색은 본문 그대로 두고 굵기만 올린다.
                RichKind.UNDERLINE -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold),
                    s.start, s.end
                )
                RichKind.URL -> if (links) {
                    addStyle(
                        SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline),
                        s.start, s.end
                    )
                    addStringAnnotation("URL", parsed.text.substring(s.start, s.end), s.start, s.end)
                }
            }
        }
    }
}

/** 본문 렌더러 — URL 자동 링크(탭하면 열림) + 형광펜 + 밑줄. */
@Composable
fun LinkifiedText(
    text: String,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 28.sp,
    color: Color = AppColors.TextPrimary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val annotated = rememberRichText(text)

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(fontSize = fontSize, lineHeight = lineHeight, color = color),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item)))
                }
            }
        }
    )
}
