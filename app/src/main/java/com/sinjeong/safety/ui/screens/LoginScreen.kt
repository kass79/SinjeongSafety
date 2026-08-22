package com.sinjeong.safety.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.safety.MainViewModel
import com.sinjeong.safety.R
import com.sinjeong.safety.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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

    // imePadding + verticalScroll 만으로는 부족했다 — 스크롤이 줄어들 뿐 자동으로 따라가지는
    // 않아서, 비밀번호 칸에 포커스가 가도 화면 밖에 남아 있었다. 포커스를 받은 칸이 스스로
    // 화면 안으로 들어오도록 요청한다.
    val scope = rememberCoroutineScope()
    val idRequester = remember { BringIntoViewRequester() }
    val pwRequester = remember { BringIntoViewRequester() }

    val doLogin = {
        loading = true
        vm.login(id, password) { onSuccess() }
    }

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
            // 좁을 때는 위쪽 장식을 전부 걷어낸다. 상단바에 이미 "관리자 로그인"이 있어
            // 제목까지 숨겨도 여기가 어딘지 헷갈리지 않는다 — 입력칸을 최대한 위로 올린다.
            Spacer(Modifier.height(if (compactTop) 0.dp else 32.dp))
            if (!compactTop) Image(
                painter = painterResource(R.drawable.mascot_hello),
                contentDescription = null,
                modifier = Modifier.size(110.dp)
            )
            Spacer(Modifier.height(if (compactTop) 0.dp else 12.dp))
            if (!compactTop) Text(
                "관리자 인증", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.Primary
            )
            Text(
                "글 작성·수정·삭제는 관리자만 가능합니다",
                fontSize = 13.sp,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(if (compactTop) 10.dp else 28.dp))

            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                singleLine = true,
                label = { Text("아이디") },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = AppColors.Primary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(idRequester)
                    .onFocusEvent { if (it.isFocused) scope.launch { idRequester.bringIntoView() } }
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                // 키보드 완료를 누르면 바로 로그인 — 가려진 버튼을 찾아 누르지 않아도 되게
                keyboardActions = KeyboardActions(onDone = {
                    if (id.isNotBlank() && password.isNotBlank() && !loading) doLogin()
                }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(pwRequester)
                    .onFocusEvent { if (it.isFocused) scope.launch { pwRequester.bringIntoView() } }
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { doLogin() },
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
