package com.sinjeong.safety.ui.screens

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sinjeong.safety.data.ConfirmReport
import com.sinjeong.safety.data.CrewConfirm
import com.sinjeong.safety.data.CrewPoints
import com.sinjeong.safety.ui.theme.AppColors
import java.io.File
import java.text.Collator
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.util.Date
import java.util.Locale

/**
 * 확인 현황·포인트 현황을 앱 밖으로 빼내는 곳.
 * 화면(Composable)이 아니라 그냥 함수다 — 버튼 클릭에서 바로 부른다.
 *
 * 새 라이브러리는 쓰지 않는다. CSV·텍스트는 문자열이면 충분하고,
 * 인쇄(PDF)는 안드로이드 기본 인쇄가 해 준다.
 *
 * 서식을 만드는 부분은 전부 순수 함수(build...)로 떼어 놨다. 안드로이드가 없어도 결과를
 * 눈으로 확인할 수 있어야 해서다 — 정렬은 실제 문자열을 보기 전에는 맞았는지 알 수 없다.
 */

private val csvTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
private val shortTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.KOREA)
private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

/** 가나다순. 한글 음절은 코드값 순서가 곧 가나다순이지만 한자·영문이 섞이면 깨져서 Collator를 쓴다. */
private val koreanOrder: Collator = Collator.getInstance(Locale.KOREA)

/** 이름이 안 남은 옛 기록도 한 줄은 차지해야 한다. */
private val CrewConfirm.displayName: String get() = name.ifBlank { "이름 미등록" }

// Collator 는 Comparator<Any> 라서 compareBy(koreanOrder) { ... } 로는 타입 추론이 안 된다.
// 비교 함수를 직접 쓰는 편이 짧다.
private fun List<CrewConfirm>.byName(): List<CrewConfirm> =
    sortedWith(Comparator { a, b ->
        val n = koreanOrder.compare(a.displayName, b.displayName)
        if (n != 0) n else a.empNo.compareTo(b.empNo)
    })

// ─────────────────────────────────────────────────────────────
//  고정폭 정렬
// ─────────────────────────────────────────────────────────────

/**
 * 고정폭 글꼴에서 한글·한자는 두 칸을 먹는다.
 * String.length 로 자리를 세면 이름이 두 자냐 세 자냐에 따라 열이 통째로 밀린다 —
 * `padEnd` 를 그냥 쓰면 반드시 깨진다. 그래서 글자마다 폭을 따로 센다.
 */
private fun Char.cellWidth(): Int = when (code) {
    in 0x1100..0x115F,   // 한글 자모
    in 0x2E80..0x303E,   // 한중일 부수·괄호
    in 0x3041..0x33FF,   // 가나 · 한글 호환자모 · 한중일 기호
    in 0x3400..0x4DBF,   // 한자 확장 A
    in 0x4E00..0x9FFF,   // 한자
    in 0xA960..0xA97F,   // 한글 자모 확장 A
    in 0xAC00..0xD7A3,   // 한글 음절 ← 직원 이름이 전부 여기 있다
    in 0xF900..0xFAFF,   // 한자 호환
    in 0xFE30..0xFE4F,   // 세로쓰기 기호
    in 0xFF00..0xFF60,   // 전각 영숫자
    in 0xFFE0..0xFFE6 -> 2
    else -> 1
}

/**
 * 표시 폭 기준으로 오른쪽을 공백으로 채운다.
 * 폭을 넘기면 잘라낸다 — 한 사람 이름이 길다고 그 줄만 열이 밀리면 표가 아니다.
 */
private fun String.padCell(width: Int): String {
    val sb = StringBuilder()
    var w = 0
    for (c in this) {
        val cw = c.cellWidth()
        if (w + cw > width) break
        sb.append(c)
        w += cw
    }
    repeat(width - w) { sb.append(' ') }
    return sb.toString()
}

/** 숫자 열은 오른쪽 맞춤. 숫자는 전부 반각이라 폭 계산이 필요 없다. */
private fun Int.padNum(width: Int): String = toString().padStart(width)

/**
 * 이름 열 폭. "이름 미등록"(공백 포함 11칸)이 통째로 들어가야 한다 —
 * 10으로 뒀더니 "이름 미등"으로 잘렸다.
 */
private const val NAME_COL = 12

// ─────────────────────────────────────────────────────────────
//  공통 내보내기 (파일 저장 + 공유 / 인쇄)
// ─────────────────────────────────────────────────────────────

/** 파일명에 못 쓰는 문자를 걷어낸다. 제목이 길면 20자에서 자른다. */
private fun safeFileName(title: String): String {
    val cleaned = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "").trim()
    return if (cleaned.isBlank()) "명단" else cleaned.take(20)
}

/**
 * 캐시에 파일로 쓰고 다른 앱(카톡·메일)으로 넘긴다.
 * 폴더 이름은 res/xml/file_paths.xml 의 <cache-path name="exports" path="exports/"/> 와 같아야 한다.
 */
private fun shareTextFile(
    context: Context,
    fileName: String,
    mime: String,
    subject: String,
    content: String
) {
    val result = runCatching {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // context가 Activity가 아닐 수 있어서 새 태스크로 띄운다.
        context.startActivity(
            Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    if (result.isFailure) {
        Toast.makeText(context, "내보내기에 실패했습니다", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 인쇄를 걸어 둔 WebView를 붙들어 두는 자리.
 * 지역 변수로만 두면 print()가 끝나기 전에 GC가 WebView를 치워서 인쇄가 조용히 실패한다.
 * 인쇄 어댑터를 넘긴 뒤에 놓아준다(그때부터는 프레임워크가 들고 있다).
 */
private var printHolder: WebView? = null

/**
 * 안드로이드 기본 인쇄. 대화상자에서 "PDF로 저장"을 고르면 PDF가 된다.
 * PdfDocument에 직접 그리지 않는 이유 — 한글 폰트와 쪽 나눔을 WebView가 알아서 해 준다.
 */
private fun printHtml(context: Context, jobName: String, html: String) {
    val result = runCatching {
        val web = WebView(context)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                runCatching {
                    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    manager.print(
                        jobName,
                        view.createPrintDocumentAdapter(jobName),
                        PrintAttributes.Builder().build()
                    )
                }
                printHolder = null  // 어댑터를 넘겼으니 이제 놓아줘도 된다
            }
        }
        printHolder = web
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    if (result.isFailure) {
        printHolder = null
        Toast.makeText(context, "내보내기에 실패했습니다", Toast.LENGTH_SHORT).show()
    }
}

/** CSV 한 칸. 값에 쉼표·따옴표·줄바꿈이 들어가도 안 깨지게 큰따옴표로 감싸고 내부 따옴표는 두 개로. */
private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

/** HTML에 값을 그대로 넣으면 이름에 &나 <가 있을 때 표가 망가진다. */
private fun htmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

// ─────────────────────────────────────────────────────────────
//  형식 고르기 대화상자
// ─────────────────────────────────────────────────────────────

/** 내보내기 대화상자의 한 줄. */
data class ExportOption(val label: String, val hint: String, val run: () -> Unit)

/**
 * "내보내기"를 누르면 뜨는 형식 선택.
 * 상단바에 아이콘을 여러 개 늘어놓지 않으려고 한 곳에 모았다 — 관리자 화면이라 자주 쓸 버튼이 아니다.
 */
@Composable
fun ExportPickerDialog(options: List<ExportOption>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내보내기", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { option ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                option.run()
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            option.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(option.hint, fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

// ─────────────────────────────────────────────────────────────
//  1. 확인 현황 — CSV (엑셀용)
// ─────────────────────────────────────────────────────────────

/**
 * 맨 앞의 BOM 한 글자가 핵심 — 이게 없으면 엑셀이 UTF-8인 줄 모르고 한글을 깨뜨린다.
 * 퀴즈 열은 푼 사람만 채운다 — 안 푼 사람은 빈 칸이라야 0점과 구분된다.
 */
fun buildConfirmCsv(report: ConfirmReport): String {
    val sb = StringBuilder()
    sb.append(0xFEFF.toChar())  // 엑셀용 UTF-8 표식(BOM). 눈에 안 보이는 글자라 코드값으로 적는다.
    sb.append("사번,이름,확인여부,확인시각,퀴즈정답,퀴즈문항수\r\n")
    report.confirmed.forEach { crew ->
        sb.append(csvCell(crew.empNo)).append(',')
            .append(csvCell(crew.name)).append(',')
            .append(csvCell("확인")).append(',')
            .append(csvCell(crew.at?.let { csvTimeFormat.format(it.toDate()) } ?: "")).append(',')
            .append(csvCell(crew.quizTotal?.let { (crew.quizCorrect ?: 0).toString() } ?: ""))
            .append(',')
            .append(csvCell(crew.quizTotal?.toString() ?: ""))
            .append("\r\n")
    }
    report.pending.forEach { crew ->
        sb.append(csvCell(crew.empNo)).append(',')
            .append(csvCell(crew.name)).append(',')
            .append(csvCell("미확인")).append(',')
            .append(csvCell("")).append(',')
            .append(csvCell("")).append(',')
            .append(csvCell(""))
            .append("\r\n")
    }
    return sb.toString()
}

fun shareConfirmCsv(context: Context, title: String, report: ConfirmReport) = shareTextFile(
    context = context,
    fileName = "확인현황_${safeFileName(title)}_${fileDateFormat.format(Date())}.csv",
    mime = "text/csv",
    subject = "확인 현황 - $title",
    content = buildConfirmCsv(report)
)

// ─────────────────────────────────────────────────────────────
//  2. 확인 현황 — 텍스트 (읽으라고 만든 것. 카톡에 붙여넣어도 된다)
// ─────────────────────────────────────────────────────────────

/**
 * 사람이 그냥 읽는 명단. CSV는 엑셀에 넣어야 표가 되지만 이건 열어 보면 바로 표다.
 * 이름은 가나다순 — 확인 순서보다 "그 사람 확인했나?" 를 찾기 쉬운 게 낫다.
 */
fun buildConfirmText(title: String, report: ConfirmReport, now: Date = Date()): String {
    val done = report.confirmed.size
    val total = report.total
    val percent = if (total > 0) done * 100 / total else 0

    val sb = StringBuilder()
    sb.append("[확인 현황] ").append(title.ifBlank { "(제목 없음)" }).append('\n')
    sb.append(stampFormat.format(now)).append(" 기준 · 확인 ").append(done)
        .append("명 / 전체 ").append(total).append("명 (").append(percent).append("%)\n")

    val takers = report.confirmed.filter { (it.quizTotal ?: 0) > 0 }
    if (takers.isNotEmpty()) {
        val asked = takers.sumOf { it.quizTotal ?: 0 }
        val got = takers.sumOf { it.quizCorrect ?: 0 }
        sb.append("퀴즈 평균 정답률 ").append(got * 100 / asked)
            .append("% (").append(takers.size).append("명 응시)\n")
    }
    if (report.anonymous > 0) {
        sb.append("이름이 안 남은 옛 기록 ").append(report.anonymous).append("명\n")
    }
    sb.append("-".repeat(48)).append('\n')

    sb.append("\n■ 확인 완료 (").append(done).append("명)\n")
    if (report.confirmed.isEmpty()) {
        sb.append("  (없음)\n")
    } else {
        report.confirmed.byName().forEach { crew ->
            val quiz = crew.quizTotal?.let { "퀴즈 ${crew.quizCorrect ?: 0}/$it" } ?: ""
            val time = crew.at?.let { shortTimeFormat.format(it.toDate()) } ?: ""
            // 퀴즈 없는 글이면 줄 끝이 공백으로 끝나 버린다 — 잘라 낸다.
            sb.append(("  " + crew.empNo.padCell(9) + crew.displayName.padCell(NAME_COL) +
                time.padCell(13) + quiz).trimEnd()).append('\n')
        }
    }

    sb.append("\n■ 미확인 (").append(report.pending.size).append("명)\n")
    if (report.pending.isEmpty()) {
        sb.append("  (없음 — 전원 확인)\n")
    } else {
        report.pending.byName().forEach { crew ->
            sb.append("  ").append(crew.empNo.padCell(9)).append(crew.displayName).append('\n')
        }
    }
    return sb.toString()
}

fun shareConfirmText(context: Context, title: String, report: ConfirmReport) = shareTextFile(
    context = context,
    fileName = "확인현황_${safeFileName(title)}_${fileDateFormat.format(Date())}.txt",
    mime = "text/plain",
    subject = "확인 현황 - $title",
    content = buildConfirmText(title, report)
)

// ─────────────────────────────────────────────────────────────
//  3. 확인 현황 — 서명부 인쇄
// ─────────────────────────────────────────────────────────────

/**
 * 열 쌍 개수. 종이 원본(전직원서명부)은 성명|날인 6쌍이지만 여기는 4쌍이다.
 * 원본의 날인 칸은 도장 하나만 받으면 되는데 이 칸에는 "✓ 08-25 14:03" 이 들어간다 —
 * A4(210mm)에서 6쌍이면 한 쌍이 31mm라 확인 칸이 20mm도 안 남아 날짜가 줄바꿈된다.
 * 4쌍이면 한 쌍 46mm(성명 22mm + 확인 24mm)라 8.5pt에서 한 줄에 들어간다.
 */
private const val ROSTER_PAIRS = 4

/**
 * 종이 서명부와 같은 모양의 표. 날인 칸 자리에 확인 표시와 시각이 들어간다.
 * 이름은 가나다순으로 **세로로** 채운다 — 원본 종이가 그렇게 생겼고,
 * 관리자가 눈으로 훑는 순서도 그쪽이다.
 */
fun buildRosterHtml(title: String, report: ConfirmReport, now: Date = Date()): String {
    val done = report.confirmed.size
    val total = report.total
    val percent = if (total > 0) done * 100 / total else 0

    val confirmedAt = report.confirmed.associateBy { it.empNo }
    val all = (report.confirmed + report.pending).byName()
    val rows = if (all.isEmpty()) 0 else (all.size + ROSTER_PAIRS - 1) / ROSTER_PAIRS

    val sb = StringBuilder()
    sb.append("<html><head><meta charset=\"utf-8\">")
    sb.append("<style>")
    sb.append("@page{margin:12mm;}")
    sb.append("body{font-family:sans-serif;font-size:9pt;margin:0;}")
    sb.append("h1{font-size:15pt;margin:0 0 4px 0;text-align:center;}")
    sb.append(".sub{margin:2px 0;text-align:center;font-size:10pt;}")
    sb.append(".hint{font-size:8pt;color:#666;text-align:center;margin:2px 0;}")
    sb.append("table{width:100%;border-collapse:collapse;table-layout:fixed;margin-top:10px;}")
    sb.append("th,td{border:1px solid #888;padding:3px 4px;overflow:hidden;}")
    sb.append("th{background:#eee;font-size:9pt;text-align:center;}")
    // 성명 12% + 확인 13% = 한 쌍 25%, 네 쌍이 딱 100%.
    sb.append("col.nm{width:12%;}col.ck{width:13%;}")
    sb.append("td.nm{text-align:center;line-height:1.15;}")
    sb.append("td.nm .no{display:block;font-size:6pt;color:#888;}")
    sb.append("td.ck{font-size:8.5pt;line-height:1.15;white-space:nowrap;}")
    sb.append("td.ck .qz{display:block;font-size:7pt;color:#555;}")
    // 표가 여러 쪽으로 넘어가도 머리글이 따라가고, 한 줄이 두 쪽에 걸치지 않게 한다.
    sb.append("thead{display:table-header-group;}tr{page-break-inside:avoid;}")
    sb.append(".pending{margin-top:10px;font-size:9pt;line-height:1.6;}")
    sb.append("h2{font-size:11pt;margin:14px 0 4px 0;}")
    sb.append("</style></head><body>")

    sb.append("<h1>신정승무사업소 확인 서명부</h1>")
    sb.append("<p class=\"sub\">").append(htmlEscape(title.ifBlank { "(제목 없음)" })).append("</p>")
    sb.append("<p class=\"sub\">").append(stampFormat.format(now))
        .append(" 기준 · 확인 ").append(done).append("명 / 전체 ").append(total)
        .append("명 (").append(percent).append("%)</p>")
    if (report.anonymous > 0) {
        sb.append("<p class=\"hint\">이름이 안 남은 옛 기록 ").append(report.anonymous).append("명</p>")
    }

    sb.append("<table>")
    repeat(ROSTER_PAIRS) { sb.append("<col class=\"nm\"><col class=\"ck\">") }
    sb.append("<thead><tr>")
    repeat(ROSTER_PAIRS) { sb.append("<th>성명</th><th>확인</th>") }
    sb.append("</tr></thead><tbody>")
    for (row in 0 until rows) {
        sb.append("<tr>")
        for (col in 0 until ROSTER_PAIRS) {
            // 세로로 채운다: 한 열을 끝까지 내려간 뒤 다음 열로 넘어간다(종이 원본과 같은 순서).
            val crew = all.getOrNull(col * rows + row)
            if (crew == null) {
                sb.append("<td class=\"nm\">&nbsp;</td><td class=\"ck\">&nbsp;</td>")
                continue
            }
            // 사번은 작게 덧붙인다 — 282명이면 동명이인이 나온다.
            sb.append("<td class=\"nm\">").append(htmlEscape(crew.displayName))
                .append("<span class=\"no\">").append(htmlEscape(crew.empNo)).append("</span></td>")
            val hit = confirmedAt[crew.empNo]
            sb.append("<td class=\"ck\">")
            if (hit == null) {
                sb.append("&nbsp;")
            } else {
                sb.append("&#10003; ").append(hit.at?.let { shortTimeFormat.format(it.toDate()) } ?: "")
                hit.quizTotal?.let {
                    sb.append("<span class=\"qz\">퀴즈 ").append(hit.quizCorrect ?: 0)
                        .append('/').append(it).append("</span>")
                }
            }
            sb.append("</td>")
        }
        sb.append("</tr>")
    }
    sb.append("</tbody></table>")

    // 독촉할 때 쓰라고 미확인자만 한 덩어리로 모아 준다.
    sb.append("<h2>미확인 (").append(report.pending.size).append("명)</h2>")
    sb.append("<p class=\"pending\">")
    if (report.pending.isEmpty()) {
        sb.append("전원 확인했습니다.")
    } else {
        sb.append(report.pending.byName().joinToString(" · ") {
            htmlEscape("${it.displayName}(${it.empNo})")
        })
    }
    sb.append("</p>")

    sb.append("</body></html>")
    return sb.toString()
}

fun printConfirmRoster(context: Context, title: String, report: ConfirmReport) =
    printHtml(context, "확인서명부_${safeFileName(title)}", buildRosterHtml(title, report))

// ─────────────────────────────────────────────────────────────
//  4. 직원 포인트 현황
// ─────────────────────────────────────────────────────────────

private fun monthLabel(month: YearMonth) = "${month.year}년 ${month.monthValue}월"

fun buildPointsText(month: YearMonth, rows: List<CrewPoints>, now: Date = Date()): String {
    val sb = StringBuilder()
    sb.append("[직원 포인트 현황] ").append(monthLabel(month)).append('\n')
    sb.append(stampFormat.format(now)).append(" 기준 · ").append(rows.size).append("명\n")
    sb.append("확인 1점 · 퀴즈 정답 1점 · 댓글 1점 · 답변 2점\n")
    sb.append("-".repeat(58)).append('\n')
    // 숫자 열은 오른쪽 맞춤이라 머리글도 오른쪽에 붙인다.
    // "확인"은 4칸이니 앞에 2칸을 두면 숫자 열(6칸)의 오른쪽 끝과 맞는다.
    sb.append("순위".padCell(5))
        .append("사번".padCell(10))
        .append("이름".padCell(NAME_COL))
        .append("  확인  퀴즈  댓글  답변   합계\n")
    sb.append("-".repeat(58)).append('\n')
    rows.forEachIndexed { index, row ->
        sb.append((index + 1).padNum(3)).append("  ")
            .append(row.empNo.padCell(10))
            .append(row.name.padCell(NAME_COL))
            .append(row.confirms.padNum(6))
            .append(row.quiz.padNum(6))
            .append(row.comments.padNum(6))
            .append(row.answers.padNum(6))
            .append(row.total.padNum(7))
            .append('\n')
    }
    if (rows.isEmpty()) sb.append("  (이번 달 활동 기록 없음)\n")
    return sb.toString()
}

fun buildPointsCsv(rows: List<CrewPoints>): String {
    val sb = StringBuilder()
    sb.append(0xFEFF.toChar())  // 엑셀용 UTF-8 표식(BOM)
    sb.append("순위,사번,이름,확인,퀴즈,댓글,답변,합계\r\n")
    rows.forEachIndexed { index, row ->
        sb.append(csvCell("${index + 1}")).append(',')
            .append(csvCell(row.empNo)).append(',')
            .append(csvCell(row.name)).append(',')
            .append(csvCell("${row.confirms}")).append(',')
            .append(csvCell("${row.quiz}")).append(',')
            .append(csvCell("${row.comments}")).append(',')
            .append(csvCell("${row.answers}")).append(',')
            .append(csvCell("${row.total}"))
            .append("\r\n")
    }
    return sb.toString()
}

fun buildPointsHtml(month: YearMonth, rows: List<CrewPoints>, now: Date = Date()): String {
    val sb = StringBuilder()
    sb.append("<html><head><meta charset=\"utf-8\">")
    sb.append("<style>")
    sb.append("@page{margin:12mm;}")
    sb.append("body{font-family:sans-serif;font-size:10pt;margin:0;}")
    sb.append("h1{font-size:15pt;margin:0 0 4px 0;text-align:center;}")
    sb.append(".sub{margin:2px 0;text-align:center;font-size:9pt;color:#555;}")
    sb.append("table{width:100%;border-collapse:collapse;margin-top:10px;}")
    sb.append("th,td{border:1px solid #888;padding:4px 6px;text-align:center;}")
    sb.append("th{background:#eee;}")
    sb.append("td.tot{font-weight:bold;}")
    sb.append("thead{display:table-header-group;}tr{page-break-inside:avoid;}")
    sb.append("</style></head><body>")
    sb.append("<h1>직원 포인트 현황 — ").append(monthLabel(month)).append("</h1>")
    sb.append("<p class=\"sub\">").append(stampFormat.format(now)).append(" 기준 · ")
        .append(rows.size).append("명</p>")
    sb.append("<p class=\"sub\">확인 1점 · 퀴즈 정답 1점 · 댓글 1점 · 답변 2점</p>")
    sb.append("<table><thead><tr><th>순위</th><th>사번</th><th>이름</th>")
        .append("<th>확인</th><th>퀴즈</th><th>댓글</th><th>답변</th><th>합계</th></tr></thead><tbody>")
    rows.forEachIndexed { index, row ->
        sb.append("<tr><td>").append(index + 1)
            .append("</td><td>").append(htmlEscape(row.empNo))
            .append("</td><td>").append(htmlEscape(row.name))
            .append("</td><td>").append(row.confirms)
            .append("</td><td>").append(row.quiz)
            .append("</td><td>").append(row.comments)
            .append("</td><td>").append(row.answers)
            .append("</td><td class=\"tot\">").append(row.total)
            .append("</td></tr>")
    }
    sb.append("</tbody></table></body></html>")
    return sb.toString()
}

private fun pointsFileStem(month: YearMonth) =
    "직원포인트_%04d%02d".format(month.year, month.monthValue)

fun sharePointsText(context: Context, month: YearMonth, rows: List<CrewPoints>) = shareTextFile(
    context = context,
    fileName = "${pointsFileStem(month)}.txt",
    mime = "text/plain",
    subject = "직원 포인트 현황 - ${monthLabel(month)}",
    content = buildPointsText(month, rows)
)

fun sharePointsCsv(context: Context, month: YearMonth, rows: List<CrewPoints>) = shareTextFile(
    context = context,
    fileName = "${pointsFileStem(month)}.csv",
    mime = "text/csv",
    subject = "직원 포인트 현황 - ${monthLabel(month)}",
    content = buildPointsCsv(rows)
)

fun printPoints(context: Context, month: YearMonth, rows: List<CrewPoints>) =
    printHtml(context, pointsFileStem(month), buildPointsHtml(month, rows))
