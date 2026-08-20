package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.BriefingRepository
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 출무점호 올리기 (관리자 전용).
 *
 * 한글 문서에서 복사한 원문을 그대로 붙여넣게 하고, 나누는 일은 앱이 한다.
 * 관리자가 항목을 하나씩 입력하게 만들면 매일 아침 쓸 수가 없다.
 */
@Composable
fun BriefingWriteScreen(vm: MainViewModel, onBack: () -> Unit) {

    var text by remember { mutableStateOf("") }
    // 파싱은 순수 함수라 저장 없이 바로 부를 수 있다 — 올리기 전에 눈으로 확인하는 용도.
    val preview = remember(text) { BriefingRepository.parse(text) }

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
                    "등록",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary,
                    modifier = Modifier
                        .clickable {
                            if (text.isBlank()) vm.showMessage("붙여넣은 내용이 없습니다")
                            else vm.saveBriefing(text) { onBack() }
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

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("'26.08.20.[목] 평→평\n - 3호선 …", color = AppColors.TextHint) },
                    shape = RoundedCornerShape(14.dp),
                    colors = briefingFieldColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                )

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
}

// 질문 작성 화면의 것과 모양은 같지만, 그쪽은 file-private 라 여기서 쓸 수 없다.
@Composable
private fun briefingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Divider
)
