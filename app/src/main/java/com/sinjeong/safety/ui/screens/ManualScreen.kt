package com.sinjeong.safety.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.ManualDoc
import com.sinjeong.safety.data.ManualFormat
import com.sinjeong.safety.data.ManualRepository
import com.sinjeong.safety.data.ManualSection
import com.sinjeong.safety.ui.theme.AppColors
import java.io.File

/**
 * 비상대응 현장조치 매뉴얼 뷰어.
 *
 * 표가 293개고 병합 셀이 잔뜩이라 섹션 본문은 Compose 로 그리지 않고 WebView 에 맡긴다
 * (자바스크립트 끄고 네트워크 차단 — 기기에 받아 둔 그림만 읽는다).
 * 목록·상태 화면만 Compose 다.
 */

// ── 목록 (그룹 → 섹션) ──────────────────────────────────────────
@Composable
fun ManualBrowseScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit
) {
    val context = LocalContext.current
    val empNo by vm.crewEmpNo.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val ready by vm.manualReady.collectAsState()
    val sync by ManualRepository.sync.collectAsState()
    val loggedIn = empNo != null || isAdmin

    var doc by remember { mutableStateOf<ManualDoc?>(null) }
    var group by remember { mutableStateOf<String?>(null) }

    // 화면에 들어올 때 한 번 더 확인한다 (앱 시작 때 통신이 안 됐을 수 있다)
    LaunchedEffect(loggedIn) { if (loggedIn) vm.syncManual() }
    LaunchedEffect(ready) { doc = if (ready) ManualRepository.load(context) else null }

    BackHandler(enabled = group != null) { group = null }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            ManualTopBar(
                title = group ?: "비상대응 현장조치 매뉴얼",
                onBack = { if (group != null) group = null else onBack() }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !loggedIn -> ManualNotice(
                    "🔒 로그인한 직원만 볼 수 있습니다",
                    "매뉴얼에는 직원 실명과 연락처가 들어 있어 사번으로 로그인한 기기에서만 열립니다."
                )
                !ready -> ManualDownloadPanel(sync = sync, onRetry = { vm.syncManual(force = true) })
                doc == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
                else -> {
                    val d = doc!!
                    val g = group
                    if (g == null) ManualGroupList(d, onPick = { group = it })
                    else ManualSectionList(d, g, onOpenSection)
                }
            }
        }
    }
}

@Composable
private fun ManualGroupList(doc: ManualDoc, onPick: (String) -> Unit) {
    // path[0] 이 그대로 큰 묶음 셋이다 (표지 / 표준운영절차 / 부록)
    val groups = remember(doc) {
        // 순서는 자료의 배열 순서 그대로다(id 는 열쇠일 뿐 — 정렬하지 않는다). 그릴 것이 없는 섹션은 뺀다.
        doc.visibleSections.mapNotNull { it.path.firstOrNull() }.distinct()
    }
    // 원본 문서의 오기 안내 — 그 오기가 실제로 들어 있는 판에서만 뜬다(수정본이 오면 저절로 사라진다)
    val typo = remember(doc) { ManualFormat.hasDrivingTypo(doc) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(doc.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    color = AppColors.TextPrimary, lineHeight = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text("${doc.edition}판 · ${doc.visibleSections.size}개 항목 · 기기에 저장되어 통신이 없어도 열립니다",
                    fontSize = 11.5.sp, color = AppColors.TextSecondary, lineHeight = 17.sp)
                if (typo) {
                    Spacer(Modifier.height(6.dp))
                    Text(ManualFormat.TYPO_NOTICE, fontSize = 11.sp, color = AppColors.TextHint,
                        lineHeight = 16.sp)
                }
            }
        }
        items(groups) { g ->
            val count = doc.visibleSections.count { it.path.firstOrNull() == g }
            Surface(
                shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
                    .clickable { onPick(g) }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            .background(AppColors.SituationBg),
                        contentAlignment = Alignment.Center
                    ) { Text(if (g == ManualFormat.GROUP_SOP) "🚨" else "📑", fontSize = 16.sp) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary)
                        Text("${count}개 항목", fontSize = 11.sp, color = AppColors.TextSecondary)
                    }
                    Text("›", fontSize = 16.sp, color = AppColors.TextHint)
                }
            }
        }
    }
}

@Composable
private fun ManualSectionList(doc: ManualDoc, group: String, onOpen: (String) -> Unit) {
    val list = remember(doc, group) { doc.visibleSections.filter { it.path.firstOrNull() == group } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        items(list) { s -> ManualSectionRow(s, onClick = { onOpen(s.id) }) }
    }
}

@Composable
private fun ManualSectionRow(section: ManualSection, onClick: () -> Unit) {
    // 부록은 "부 록 > 5. 비상대응 안내방송" 처럼 하위 경로가 있어 작게 얹어 준다
    val sub = section.path.drop(1).joinToString(" › ")
    val tag = ManualFormat.situationTag(section)
    Surface(
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.5.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            if (tag.isNotEmpty() || sub.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tag.isNotEmpty()) {
                        Surface(color = AppColors.ManualBadgeBg, shape = RoundedCornerShape(6.dp)) {
                            Text(tag, color = AppColors.ManualBadgeFg, fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (sub.isNotEmpty()) {
                        Text(sub, fontSize = 10.5.sp, color = AppColors.TextHint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            Text(ManualFormat.clean(section.title), fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, lineHeight = 19.sp)
        }
    }
}

// ── 섹션 보기 (WebView) ─────────────────────────────────────────
@Composable
fun ManualSectionScreen(sectionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var doc by remember { mutableStateOf<ManualDoc?>(null) }
    // "아직 읽는 중"과 "읽어 봤는데 없다"를 가른다. 안 가르면 매뉴얼이 없는 기기에서
    // (오늘의 비상조치·AI 답변의 바로가기로 들어왔을 때) 빙글빙글만 영원히 돈다.
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(sectionId) {
        doc = ManualRepository.load(context)
        loaded = true
    }

    val section = doc?.sections?.firstOrNull { it.id == sectionId }
    val dark = AppColors.isDark

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            ManualTopBar(
                title = section?.let { ManualFormat.clean(it.title) } ?: "매뉴얼",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
                doc == null -> ManualNotice(
                    "매뉴얼이 기기에 없습니다",
                    "규정 화면의 '비상대응 현장조치 매뉴얼'에서 먼저 받아 주세요. 로그인한 직원만 받을 수 있습니다."
                )
                section == null -> ManualNotice(
                    "항목을 찾지 못했습니다",
                    "매뉴얼이 새 판으로 바뀌었을 수 있습니다. 목록에서 다시 골라 주세요."
                )
                // 목록에서는 이미 빼지만, 바로가기(오늘의 비상조치·AI 답변)로 들어올 수도 있다 — 빈 화면은 열지 않는다
                !ManualFormat.hasContent(section) -> ManualNotice(
                    "이 항목에는 내용이 없습니다",
                    "제목만 있는 항목입니다. 목록에서 앞뒤 항목을 확인해 주세요."
                )
                else -> ManualWebView(
                    html = remember(section, dark, doc) {
                        // 오기 안내는 본문 맨 아래에 함께 싣는다(본문과 같이 스크롤되고, 같은 이스케이프를 탄다)
                        val notice = if (doc?.let { ManualFormat.hasDrivingTypo(it) } == true)
                            ManualFormat.TYPO_NOTICE else ""
                        ManualFormat.html(section, dark, doc?.edition.orEmpty(), notice = notice)
                    },
                    baseDir = File(context.filesDir, "manual")
                )
            }
        }
    }
}

/**
 * 기기에 받아 둔 섹션 HTML 을 그린다.
 *
 * 안전장치 — 이 WebView 는 바깥 세상과 끊어 둔다:
 *  - 자바스크립트 끔, 네트워크 로드 차단(`blockNetworkLoads`)
 *  - 파일 접근은 그림 표시에 필요한 만큼만 (file:// 문서끼리의 교차 접근은 끈다)
 *  - 링크를 눌러도 이동하지 않는다
 * 핀치 줌은 켠다(그림을 확대해 보려고 별도 뷰어를 두지 않는다).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ManualWebView(html: String, baseDir: File) {
    val bg = AppColors.Background.toArgb()
    // 이 WebView 가 읽어도 되는 곳은 그림 폴더 하나뿐이다. /data/user/0 과 /data/data 는
    // 같은 곳을 가리키는 다른 이름이라 양쪽 다 canonical 로 풀어서 비교한다.
    val imageRoot = remember(baseDir) {
        runCatching { File(baseDir, "images").canonicalPath + File.separator }.getOrDefault("")
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.blockNetworkLoads = true
                settings.allowContentAccess = false
                settings.allowFileAccess = true               // 그림(file://)만 쓴다
                // 기본값이 false 지만 명시한다 — file:// 문서가 다른 파일·다른 출처를 읽는 길은
                // 이 앱에서 절대 열리면 안 되고, 기본값에 기대면 나중에 누가 켜도 눈에 안 띈다.
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean = true   // 어떤 링크로도 나가지 않는다

                    // allowFileAccess 는 "앱이 읽을 수 있는 모든 파일"을 연다(로그인 토큰이 든
                    // shared_prefs 포함). 본문은 전부 이스케이프하지만 한 겹 더 — 그림 폴더 밖의
                    // 요청은 빈 응답으로 막는다. 본문 자체(loadDataWithBaseURL)는 그대로 둔다.
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        if (request == null || request.isForMainFrame) return null
                        val url = request.url
                        val inside = url.scheme == "file" && imageRoot.isNotEmpty() &&
                            runCatching {
                                File(url.path.orEmpty()).canonicalPath.startsWith(imageRoot)
                            }.getOrDefault(false)
                        return if (inside) null else android.webkit.WebResourceResponse(
                            "text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
            }
        },
        // 화면을 나가면 놓아 준다 (안 하면 섹션을 열 때마다 WebView 가 하나씩 쌓인다)
        onRelease = { it.destroy() },
        update = { web ->
            // update 는 다시 그릴 때마다 불린다. 그때마다 loadDataWithBaseURL 을 하면
            // 읽던 자리가 맨 위로 튕긴다 — 내용이 바뀐 경우에만 다시 싣는다.
            if (web.tag != html) {
                web.tag = html
                web.setBackgroundColor(bg)
                web.loadDataWithBaseURL(
                    "file://" + baseDir.absolutePath + "/",
                    html, "text/html", "utf-8", null
                )
            }
        }
    )
}

// ── 공통 조각 ───────────────────────────────────────────────────
@Composable
private fun ManualTopBar(title: String, onBack: () -> Unit) {
    Surface(color = AppColors.Surface, shadowElevation = 1.dp) {
        Row(
            // 상태표시줄 여백은 MainActivity 의 Scaffold 가 이미 넣어 준다
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = AppColors.TextPrimary)
            }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        }
    }
}

@Composable
private fun ManualNotice(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 12.5.sp, color = AppColors.TextSecondary, lineHeight = 20.sp)
    }
}

/** 아직 안 받았을 때 (진행률 · 다시 받기) */
@Composable
fun ManualDownloadPanel(sync: ManualRepository.SyncState, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🚨 비상대응 현장조치 매뉴얼", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            color = AppColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        when {
            sync.running -> {
                Text("받는 중… ${sync.done}/${sync.total}", fontSize = 12.5.sp,
                    color = AppColors.TextSecondary)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { sync.percent / 100f },
                    color = AppColors.Primary,
                    trackColor = AppColors.Divider,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            sync.denied -> {
                Text(ManualRepository.DENIED_MESSAGE, fontSize = 12.5.sp,
                    color = AppColors.TextSecondary, lineHeight = 20.sp)
            }
            else -> {
                Text(
                    if (sync.failed) "받지 못했습니다. 통신이 되는 곳에서 다시 눌러 주세요."
                    else "한 번 받아 두면 터널에서도 그림까지 그대로 열립니다. (약 5MB)",
                    fontSize = 12.5.sp, color = AppColors.TextSecondary, lineHeight = 20.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(if (sync.failed) "다시 받기" else "매뉴얼 받기",
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
