package com.sinjeong.safety.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.BriefingRepository
import com.sinjeong.safety.data.LinkAttachment
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 출무점호 올리기 (관리자 전용).
 *
 * 한글 문서에서 복사한 원문을 그대로 붙여넣게 하고, 나누는 일은 앱이 한다.
 * 관리자가 항목을 하나씩 입력하게 만들면 매일 아침 쓸 수가 없다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefingWriteScreen(vm: MainViewModel, onBack: () -> Unit) {

    var text by remember { mutableStateOf("") }
    // 파싱은 순수 함수라 저장 없이 바로 부를 수 있다 — 올리기 전에 눈으로 확인하는 용도.
    val preview = remember(text) { BriefingRepository.parse(text) }

    val isUploading by vm.isUploading.collectAsState()
    val briefings by vm.briefings.collectAsState()
    // 지난 점호 고르는 창. 매일 양식이 비슷해 새로 쓰는 것보다 고쳐 쓰는 게 빠르다.
    var showPast by remember { mutableStateOf(false) }
    var newFiles by remember { mutableStateOf(listOf<PendingFile>()) }
    var links by remember { mutableStateOf(listOf<LinkAttachment>()) }
    var linkInput by remember { mutableStateOf("") }

    // 게시물 작성 화면과 같은 피커·같은 첨부 UI를 쓴다(WriteScreen.kt).
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        newFiles = newFiles + uris.map { uri ->
            val (name, size) = vm.fileInfo(uri)
            PendingFile(uri, name, size, vm.mimeOf(uri))
        }
    }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        newFiles = newFiles + uris.map { uri ->
            val (name, size) = vm.fileInfo(uri)
            PendingFile(uri, name, size, vm.mimeOf(uri))
        }
    }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ── 상단 바 ──────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "뒤로",
                    tint = AppColors.TextPrimary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(onClick = onBack)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "출무점호 올리기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (isUploading) "올리는 중…" else "등록",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary,
                    modifier = Modifier
                        .clickable(enabled = !isUploading) {
                            if (text.isBlank()) vm.showMessage("붙여넣은 내용이 없습니다")
                            else vm.saveBriefing(text, newFiles.map { it.uri }, links) { onBack() }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "한글 문서에서 그대로 복사해 붙여넣으세요. 날짜와 항목을 알아서 나눕니다.",
                    fontSize = 12.5.sp,
                    color = AppColors.TextSecondary
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "지난 점호를 불러와 고쳐 올릴 수 있습니다",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    // 게시물 작성 화면의 "AI 3줄 요약"과 같은 파스텔 그린 알약(WriteScreen.kt)
                    Text(
                        "지난 점호 불러오기",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TagOpsFg,
                        modifier = Modifier
                            .background(AppColors.TagOpsBg, RoundedCornerShape(50))
                            .clickable {
                                if (briefings.isEmpty()) vm.showMessage("불러올 지난 점호가 없습니다")
                                else showPast = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("'26.08.20.[목] 평→평\n - 3호선 …", color = AppColors.TextHint) },
                    shape = RoundedCornerShape(14.dp),
                    colors = briefingFieldColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                )

                // ── 파일 · 동영상 첨부 ──────────────────────────────
                Spacer(Modifier.height(20.dp))
                SectionLabel("파일 · 동영상 첨부")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        border = BorderStroke(2.dp, Color(0xFFC6CEE8)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isUploading) { filePicker.launch("*/*") }
                    ) {
                        Column(
                            Modifier.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AttachFile, null, tint = AppColors.Primary,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("사진 · 문서", color = AppColors.Primary,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        border = BorderStroke(2.dp, Color(0xFFC6CEE8)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isUploading) { videoPicker.launch("video/*") }
                    ) {
                        Column(
                            Modifier.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Movie, null, tint = AppColors.Primary,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("동영상 (MP4)", color = AppColors.Primary,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    newFiles.forEach { pf ->
                        when {
                            pf.isImage -> ImageThumb(model = pf.uri) { newFiles = newFiles - pf }
                            pf.isVideo -> VideoChip(name = pf.name, size = pf.size) { newFiles = newFiles - pf }
                            else -> FileChip(name = pf.name, size = pf.size) { newFiles = newFiles - pf }
                        }
                    }
                }

                // ── 링크 첨부 ──────────────────────────────────────
                Spacer(Modifier.height(20.dp))
                SectionLabel("링크 첨부")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        singleLine = true,
                        placeholder = { Text("https:// 주소 붙여넣기", color = AppColors.TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Link, null, tint = AppColors.Primary) },
                        shape = RoundedCornerShape(14.dp),
                        colors = briefingFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val u = linkInput.trim()
                            if (u.startsWith("http")) {
                                links = links + LinkAttachment(url = u, title = "")
                                linkInput = ""
                            }
                        },
                        enabled = linkInput.trim().startsWith("http"),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                    ) { Text("추가", fontWeight = FontWeight.Bold) }
                }
                Text(
                    "유튜브 링크는 카드에 ▶ 표시로 나옵니다",
                    fontSize = 11.sp, color = AppColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                links.forEach { link ->
                    LinkRow(link = link) { links = links - link }
                    Spacer(Modifier.height(8.dp))
                }

                if (text.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "미리보기 — ${preview.id} · 지적 ${preview.items.size}건",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    // 홈 카드와 같은 모양. 미리보기는 접지 않고 전부 보여야 잘못 나뉜 걸 잡는다.
                    BriefingCard(
                        briefing = preview,
                        isAdmin = false,
                        onWrite = {},
                        onList = null,
                        collapseLimit = Int.MAX_VALUE
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showPast) {
        AlertDialog(
            onDismissRequest = { showPast = false },
            title = { Text("지난 점호 불러오기") },
            text = {
                Column {
                    // 확인 창을 하나 더 띄우는 대신 여기서 미리 경고한다(탭 수를 늘리지 않으려고).
                    if (text.isNotBlank()) {
                        Text(
                            "현재 입력한 내용은 사라집니다",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.NewBadge
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(briefings.take(10), key = { it.id }) { b ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // raw 원문을 넣으면 preview 는 remember(text) 라 알아서 다시 계산된다.
                                        text = b.raw
                                        showPast = false
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    b.dateText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "지적 ${b.items.size}건",
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPast = false }) { Text("취소") }
            }
        )
    }
}

// 질문 작성 화면의 것과 모양은 같지만, 그쪽은 file-private 라 여기서 쓸 수 없다.
@Composable
private fun briefingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Divider
)
