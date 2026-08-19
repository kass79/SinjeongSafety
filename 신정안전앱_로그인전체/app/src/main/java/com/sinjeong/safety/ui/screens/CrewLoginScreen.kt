package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.R
import com.sinjeong.safety.ui.theme.AppColors

/** 화면 단계 */
private enum class CrewStep { LOGIN, REGISTER_ID, REGISTER_PIN, HELP }

/**
 * 승무원 로그인 화면.
 * 등록은 최초 1회만 하고, 이후에는 로그인 상태가 유지되어 이 화면이 나타나지 않는다.
 */
@Composable
fun CrewLoginScreen(
    vm: MainViewModel,
    onSuccess: () -> Unit,
    onAdminClick: () -> Unit
) {
    var step by remember { mutableStateOf(CrewStep.LOGIN) }
    var empNo by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val message by vm.message.collectAsState()
    LaunchedEffect(message) { if (message?.isError == true) loading = false }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Image(
                painter = painterResource(R.drawable.mascot_hello),
                contentDescription = null,
                modifier = Modifier.size(100.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "슬기로운 승무생활",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.Primary
            )
            Text(
                "신정승무사업소 승무원 전용",
                fontSize = 13.sp,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(28.dp))

            when (step) {

                // ── 로그인 ──────────────────────────────────────
                CrewStep.LOGIN -> {
                    OutlinedTextField(
                        value = empNo,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) empNo = it },
                        singleLine = true,
                        label = { Text("사번 8자리") },
                        leadingIcon = { Icon(Icons.Default.Badge, null, tint = AppColors.Primary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                        singleLine = true,
                        label = { Text("PIN 6자리") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = AppColors.Primary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(22.dp))
                    PrimaryButton(
                        text = "로그인",
                        loading = loading,
                        enabled = empNo.length == 8 && pin.length == 6 && !loading
                    ) {
                        loading = true
                        vm.crewSignIn(empNo, pin) { loading = false; onSuccess() }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("처음이신가요? 등록하기") {
                        pin = ""; step = CrewStep.REGISTER_ID
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { step = CrewStep.HELP }) {
                            Text("PIN을 잊으셨나요?", fontSize = 12.5.sp, color = AppColors.TextHint)
                        }
                        TextButton(onClick = onAdminClick) {
                            Text("관리자", fontSize = 12.5.sp, color = AppColors.TextHint)
                        }
                    }
                }

                // ── 등록 1단계: 사번 확인 ───────────────────────
                CrewStep.REGISTER_ID -> {
                    Text(
                        "사번 확인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "신정승무사업소 명단과 대조합니다",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = empNo,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) empNo = it },
                        singleLine = true,
                        label = { Text("사번 8자리") },
                        leadingIcon = { Icon(Icons.Default.Badge, null, tint = AppColors.Primary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    InfoCard(
                        "명단에 있는 사번만 등록할 수 있어요. " +
                            "전입·신규 발령으로 명단에 없으면 관리자에게 알려주세요."
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        text = "다음",
                        loading = loading,
                        enabled = empNo.length == 8 && !loading
                    ) {
                        loading = true
                        vm.crewCheckEmpNo(empNo) { savedName ->
                            loading = false
                            if (savedName != null) name = savedName
                            step = CrewStep.REGISTER_PIN
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("뒤로") { step = CrewStep.LOGIN }
                }

                // ── 등록 2단계: 이름 + PIN ──────────────────────
                CrewStep.REGISTER_PIN -> {
                    Text(
                        "이름과 PIN 설정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "앞으로 이 PIN으로 로그인합니다",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 10) name = it },
                        singleLine = true,
                        label = { Text("이름") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = AppColors.Primary) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                        singleLine = true,
                        label = { Text("PIN 6자리") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = AppColors.Primary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin2 = it },
                        singleLine = true,
                        label = { Text("PIN 확인") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = AppColors.Primary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pin2.isNotEmpty() && pin != pin2) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "PIN이 서로 다릅니다",
                            fontSize = 12.5.sp,
                            color = AppColors.NewBadge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    InfoCard(
                        "생일이나 사번 뒷자리처럼 남이 아는 숫자는 피해주세요. " +
                            "PIN은 암호화되어 저장되며 관리자도 볼 수 없습니다."
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        text = "등록 완료",
                        loading = loading,
                        enabled = name.isNotBlank() && pin.length == 6 && pin == pin2 && !loading
                    ) {
                        loading = true
                        vm.crewRegister(empNo, name, pin) { loading = false; onSuccess() }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("뒤로") { step = CrewStep.REGISTER_ID }
                }

                // ── PIN 분실 안내 ───────────────────────────────
                CrewStep.HELP -> {
                    Text(
                        "PIN을 잊으셨나요?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    InfoCard(
                        "① 관리자에게 사번을 알려주세요.\n\n" +
                            "② 초기화되면 앱에서 [처음이신가요? 등록하기]로 다시 등록하시면 됩니다. " +
                            "이름과 기록은 그대로 남아 있어요."
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlineButton("돌아가기") { step = CrewStep.LOGIN }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Surface,
            contentColor = AppColors.Primary
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FC)),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            color = Color(0xFF2E4479),
            lineHeight = 19.sp,
            modifier = Modifier.padding(13.dp)
        )
    }
}
