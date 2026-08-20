package com.sinjeong.safety.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.data.QuestionRepository
import com.sinjeong.safety.ui.theme.AppColors

/**
 * 질문 작성. 사진은 최대 3장까지만 — 답변하는 사람이 스크롤만 하다 지치지 않게.
 */
@Composable
fun QuestionWriteScreen(vm: MainViewModel, onBack: () -> Unit) {

    val isUploading by vm.isUploading.collectAsState()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var images by remember { mutableStateOf(listOf<Uri>()) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val room = QuestionRepository.MAX_IMAGES - images.size
        if (uris.size > room) {
            vm.showMessage("사진은 ${QuestionRepository.MAX_IMAGES}장까지만 올릴 수 있습니다")
        }
        if (room > 0) images = images + uris.take(room)
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
                        .clickable(enabled = !isUploading, onClick = onBack)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "질문하기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (isUploading) {
                    CircularProgressIndicator(
                        color = AppColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        "등록",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary,
                        modifier = Modifier
                            .clickable {
                                if (title.isBlank() || content.isBlank()) {
                                    vm.showMessage("제목과 내용을 입력해주세요")
                                } else {
                                    vm.addQuestion(title, content, images) { onBack() }
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    enabled = !isUploading,
                    placeholder = { Text("제목을 입력하세요", color = AppColors.TextHint) },
                    shape = RoundedCornerShape(14.dp),
                    colors = questionFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    enabled = !isUploading,
                    placeholder = { Text("궁금한 내용을 적어주세요", color = AppColors.TextHint) },
                    shape = RoundedCornerShape(14.dp),
                    colors = questionFieldColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                )

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
                        modifier = Modifier.clickable(enabled = !isUploading) { picker.launch("image/*") }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "사진 추가",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${images.size} / ${QuestionRepository.MAX_IMAGES}",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }

                if (images.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        images.forEach { uri ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2C2F3A))
                                        .clickable(enabled = !isUploading) { images = images - uri },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "빼기",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun questionFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Divider
)
