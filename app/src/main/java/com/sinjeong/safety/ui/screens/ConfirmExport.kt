package com.sinjeong.safety.ui.screens

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sinjeong.safety.data.ConfirmReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 확인 현황 명단을 앱 밖으로 빼내는 두 가지 방법.
 * 화면(Composable)이 아니라 그냥 함수다 — 버튼 클릭에서 바로 부른다.
 * 새 라이브러리는 쓰지 않는다. CSV는 문자열이면 충분하고, PDF는 안드로이드 기본 인쇄가 해 준다.
 */

private val csvTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

/** CSV 한 칸. 값에 쉼표·따옴표·줄바꿈이 들어가도 안 깨지게 큰따옴표로 감싸고 내부 따옴표는 두 개로. */
private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

/** 파일명에 못 쓰는 문자를 걷어낸다. 제목이 길면 20자에서 자른다. */
private fun safeFileName(title: String): String {
    val cleaned = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "").trim()
    return if (cleaned.isBlank()) "명단" else cleaned.take(20)
}

/**
 * 확인 현황을 CSV로 만들어 다른 앱(카톡·메일)으로 넘긴다.
 * 맨 앞의 BOM 한 글자가 핵심 — 이게 없으면 엑셀이 UTF-8인 줄 모르고 한글을 깨뜨린다.
 */
fun shareConfirmCsv(context: Context, title: String, report: ConfirmReport) {
    val result = runCatching {
        val sb = StringBuilder()
        sb.append(0xFEFF.toChar())  // 엑셀용 UTF-8 표식(BOM). 눈에 안 보이는 글자라 코드값으로 적는다.
        sb.append("사번,이름,확인여부,확인시각\r\n")
        report.confirmed.forEach { crew ->
            sb.append(csvCell(crew.empNo)).append(',')
                .append(csvCell(crew.name)).append(',')
                .append(csvCell("확인")).append(',')
                .append(csvCell(crew.at?.let { csvTimeFormat.format(it.toDate()) } ?: ""))
                .append("\r\n")
        }
        report.pending.forEach { crew ->
            sb.append(csvCell(crew.empNo)).append(',')
                .append(csvCell(crew.name)).append(',')
                .append(csvCell("미확인")).append(',')
                .append(csvCell(""))
                .append("\r\n")
        }

        // file_paths.xml 의 <cache-path name="exports" path="exports/"/> 와 같은 폴더여야 한다.
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, "확인현황_${safeFileName(title)}_${fileDateFormat.format(Date())}.csv")
        file.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "확인 현황 - $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // context가 Activity가 아닐 수 있어서 새 태스크로 띄운다.
        val chooser = Intent.createChooser(send, "확인 현황 보내기")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
    if (result.isFailure) {
        Toast.makeText(context, "내보내기에 실패했습니다", Toast.LENGTH_SHORT).show()
    }
}

/** HTML에 값을 그대로 넣으면 이름에 &나 <가 있을 때 표가 망가진다. */
private fun htmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

/**
 * 인쇄를 걸어 둔 WebView를 붙들어 두는 자리.
 * 지역 변수로만 두면 print()가 끝나기 전에 GC가 WebView를 치워서 인쇄가 조용히 실패한다.
 * 인쇄 어댑터를 넘긴 뒤에 놓아준다(그때부터는 프레임워크가 들고 있다).
 */
private var printHolder: WebView? = null

/**
 * 안드로이드 기본 인쇄로 명단을 뽑는다. 대화상자에서 "PDF로 저장"을 고르면 PDF가 된다.
 * PdfDocument에 직접 그리지 않는 이유 — 한글 폰트와 쪽 나눔을 WebView가 알아서 해 준다.
 */
fun printConfirmRoster(context: Context, title: String, report: ConfirmReport) {
    val result = runCatching {
        val done = report.confirmed.size
        val total = report.total
        val percent = if (total > 0) done * 100 / total else 0

        val sb = StringBuilder()
        sb.append("<html><head><meta charset=\"utf-8\">")
        sb.append("<style>")
        sb.append("body{font-family:sans-serif;font-size:11pt;padding:16px;}")
        sb.append("h1{font-size:15pt;margin:0 0 6px 0;}")
        sb.append("p{margin:2px 0;}")
        sb.append(".hint{font-size:9pt;color:#666;}")
        sb.append("h2{font-size:12pt;margin:18px 0 6px 0;}")
        sb.append("table{width:100%;border-collapse:collapse;}")
        sb.append("th,td{border:1px solid #bbb;padding:4px 6px;text-align:left;}")
        sb.append("th{background:#eee;}")
        sb.append("</style></head><body>")

        sb.append("<h1>").append(htmlEscape(title)).append("</h1>")
        sb.append("<p>확인 ").append(done).append("명 / 전체 ").append(total)
            .append("명 (").append(percent).append("%)</p>")
        if (report.anonymous > 0) {
            sb.append("<p class=\"hint\">이름이 안 남은 옛 기록 ").append(report.anonymous).append("명</p>")
        }

        sb.append("<h2>확인한 사람 (").append(done).append("명)</h2>")
        sb.append("<table><tr><th>사번</th><th>이름</th><th>확인시각</th></tr>")
        report.confirmed.forEach { crew ->
            sb.append("<tr><td>").append(htmlEscape(crew.empNo))
                .append("</td><td>").append(htmlEscape(crew.name))
                .append("</td><td>").append(crew.at?.let { csvTimeFormat.format(it.toDate()) } ?: "")
                .append("</td></tr>")
        }
        sb.append("</table>")

        sb.append("<h2>아직 안 본 사람 (").append(report.pending.size).append("명)</h2>")
        sb.append("<table><tr><th>사번</th><th>이름</th></tr>")
        report.pending.forEach { crew ->
            sb.append("<tr><td>").append(htmlEscape(crew.empNo))
                .append("</td><td>").append(htmlEscape(crew.name))
                .append("</td></tr>")
        }
        sb.append("</table>")

        sb.append("</body></html>")

        val jobName = "확인현황_${safeFileName(title)}"
        val web = WebView(context)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                runCatching {
                    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    manager.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
                }
                printHolder = null  // 어댑터를 넘겼으니 이제 놓아줘도 된다
            }
        }
        printHolder = web
        web.loadDataWithBaseURL(null, sb.toString(), "text/html", "UTF-8", null)
    }
    if (result.isFailure) {
        printHolder = null
        Toast.makeText(context, "내보내기에 실패했습니다", Toast.LENGTH_SHORT).show()
    }
}
