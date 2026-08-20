package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.R
import com.sinjeong.safety.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel, onBack: () -> Unit, onSuccess: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // 승무원 로그인 화면과 같은 처리 — 키보드가 떴거나 세로가 짧으면
    // 상단 마스코트·여백을 걷어내 아이디·비밀번호 칸을 위로 끌어올린다
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val shortScreen = LocalConfiguration.current.screenHeightDp < 640
    val compactTop = imeVisible || shortScreen

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("관리자 로그인", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // 키보드가 입력칸을 가리지 않도록 스크롤 앞에서 인셋을 준다
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (compactTop) 12.dp else 32.dp))
            if (!compactTop) Image(
                painter = painterResource(R.drawable.mascot_hello),
                contentDescription = null,
                modifier = Modifier.size(110.dp)
            )
            Spacer(Modifier.height(if (compactTop) 6.dp else 12.dp))
            Text("관리자 인증", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.Primary)
            Text(
                "글 작성·수정·삭제는 관리자만 가능합니다",
                fontSize = 13.sp,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(if (compactTop) 14.dp else 28.dp))

            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                singleLine = true,
                label = { Text("아이디") },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = AppColors.Primary) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text("비밀번호") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = AppColors.Primary) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = AppColors.TextSecondary
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    loading = true
                    vm.login(id, password) { onSuccess() }
                },
                enabled = id.isNotBlank() && password.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (loading) CircularProgressIndicator(
                    color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)
                )
                else Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // 로그인 실패 시 loading 해제 (message 콜백과 별개 안전장치)
            val message by vm.message.collectAsState()
            LaunchedEffect(message) { if (message?.isError == true) loading = false }
        }
    }
}
