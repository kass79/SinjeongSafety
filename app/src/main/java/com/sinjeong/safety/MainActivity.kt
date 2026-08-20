package com.sinjeong.safety

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sinjeong.safety.ui.screens.ConfirmStatusScreen
import com.sinjeong.safety.ui.screens.CrewLoginScreen
import com.sinjeong.safety.ui.screens.SettingsScreen
import com.sinjeong.safety.ui.screens.DetailScreen
import com.sinjeong.safety.ui.screens.HomeScreen
import com.sinjeong.safety.ui.screens.LoginScreen
import com.sinjeong.safety.ui.screens.QuestionDetailScreen
import com.sinjeong.safety.ui.screens.QuestionListScreen
import com.sinjeong.safety.ui.screens.QuestionWriteScreen
import com.sinjeong.safety.ui.screens.RegulationAskScreen
import com.sinjeong.safety.ui.screens.RegulationScreen
import com.sinjeong.safety.ui.screens.WriteScreen
import com.sinjeong.safety.ui.theme.SinjeongSafetyTheme

object Routes {
    const val HOME = "home"
    const val LOGIN = "login"
    const val CREW_LOGIN = "crew_login"      // 승무원 로그인 (게이트)
    const val SETTINGS = "settings"          // 설정
    const val WRITE = "write"                 // 새 글
    const val EDIT = "write/{postId}"         // 수정
    const val DETAIL = "detail/{postId}"
    const val REGULATION = "regulation"
    const val REG_ASK = "reg_ask"             // 규정에 물어보기 (기기 안 검색)
    const val CONFIRMS = "confirms/{postId}"  // 확인 현황 (관리자)
    const val QUESTIONS = "questions"                // 질의응답 목록
    const val QUESTION_WRITE = "question_write"      // 질문 작성
    const val QUESTION_DETAIL = "question/{questionId}"
    fun question(id: String) = "question/$id"
    fun detail(id: String) = "detail/$id"
    fun edit(id: String) = "write/$id"
    fun confirms(id: String) = "confirms/$id"
}

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 새 게시물 푸시 토픽 구독 + 안드로이드 13+ 알림 권한 요청
        // 알림을 끈 적이 없다면 새 글 토픽을 구독한다 (설정 화면에서 끄면 해제된다)
        if (getSharedPreferences("safety_prefs", MODE_PRIVATE)
                .getBoolean("notify_new_posts", true)
        ) {
            FirebaseMessaging.getInstance().subscribeToTopic("new_posts")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            SinjeongSafetyTheme {
                val vm: MainViewModel = viewModel()
                val nav = rememberNavController()
                val snackbarHost = remember { SnackbarHostState() }
                val message by vm.message.collectAsState()
                // 로그인 강제 스위치가 켜져 있고 아직 로그인 전이면 로그인 화면부터 보여준다.
                val needLogin by vm.needCrewLogin.collectAsState()

                LaunchedEffect(message) {
                    message?.let {
                        snackbarHost.showSnackbar(it.text)
                        vm.consumeMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHost) }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                      if (needLogin) {
                        CrewLoginScreen(
                            vm = vm,
                            onSuccess = { },
                            onAdminClick = { nav.navigate(Routes.LOGIN) }
                        )
                      } else {
                        NavHost(navController = nav, startDestination = Routes.HOME) {

                            composable(Routes.HOME) {
                                HomeScreen(
                                    vm = vm,
                                    onPostClick = { post -> nav.navigate(Routes.detail(post.id)) },
                                    onLoginClick = { nav.navigate(Routes.LOGIN) },
                                    onWriteClick = { nav.navigate(Routes.WRITE) },
                                    onRegulationClick = { nav.navigate(Routes.REGULATION) },
                                    onAskClick = { nav.navigate(Routes.REG_ASK) },
                                    onQuestionsClick = { nav.navigate(Routes.QUESTIONS) },
                                    onSettingsClick = { nav.navigate(Routes.SETTINGS) }
                                )
                            }

                            composable(Routes.SETTINGS) {
                                SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
                            }

                            composable(Routes.REGULATION) {
                                RegulationScreen(
                                    onBack = { nav.popBackStack() },
                                    onAsk = { nav.navigate(Routes.REG_ASK) }
                                )
                            }

                            composable(Routes.REG_ASK) {
                                RegulationAskScreen(onBack = { nav.popBackStack() })
                            }

                            composable(Routes.LOGIN) {
                                LoginScreen(
                                    vm = vm,
                                    onBack = { nav.popBackStack() },
                                    onSuccess = { nav.popBackStack() }
                                )
                            }

                            composable(Routes.WRITE) {
                                WriteScreen(
                                    vm = vm,
                                    editingPostId = null,
                                    onBack = { nav.popBackStack() }
                                )
                            }

                            composable(Routes.EDIT) { backStackEntry ->
                                val postId = backStackEntry.arguments?.getString("postId")
                                WriteScreen(
                                    vm = vm,
                                    editingPostId = postId,
                                    onBack = { nav.popBackStack() }
                                )
                            }

                            composable(Routes.DETAIL) { backStackEntry ->
                                val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
                                DetailScreen(
                                    vm = vm,
                                    postId = postId,
                                    onBack = { nav.popBackStack() },
                                    onEdit = { id -> nav.navigate(Routes.edit(id)) },
                                    onConfirmStatus = { id -> nav.navigate(Routes.confirms(id)) }
                                )
                            }

                            composable(Routes.CONFIRMS) { backStackEntry ->
                                val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
                                ConfirmStatusScreen(vm = vm, postId = postId, onBack = { nav.popBackStack() })
                            }

                            // 승무원 로그인은 게이트로만 쓰였는데, 질의응답에서
                            // "로그인하면 …" 안내를 누르면 여기로 보내야 해서 목적지로도 등록한다.
                            composable(Routes.CREW_LOGIN) {
                                CrewLoginScreen(
                                    vm = vm,
                                    onSuccess = { nav.popBackStack() },
                                    onAdminClick = { nav.navigate(Routes.LOGIN) }
                                )
                            }

                            composable(Routes.QUESTIONS) {
                                QuestionListScreen(
                                    vm = vm,
                                    onBack = { nav.popBackStack() },
                                    onQuestionClick = { id -> nav.navigate(Routes.question(id)) },
                                    onWriteClick = { nav.navigate(Routes.QUESTION_WRITE) },
                                    onLoginClick = { nav.navigate(Routes.CREW_LOGIN) }
                                )
                            }

                            composable(Routes.QUESTION_WRITE) {
                                QuestionWriteScreen(vm = vm, onBack = { nav.popBackStack() })
                            }

                            composable(Routes.QUESTION_DETAIL) { backStackEntry ->
                                val qid = backStackEntry.arguments?.getString("questionId") ?: return@composable
                                QuestionDetailScreen(
                                    vm = vm,
                                    questionId = qid,
                                    onBack = { nav.popBackStack() },
                                    onLoginClick = { nav.navigate(Routes.CREW_LOGIN) }
                                )
                            }
                        }
                      }
                    }
                }
            }
        }
    }
}
