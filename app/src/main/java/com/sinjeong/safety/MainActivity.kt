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
import com.sinjeong.safety.ui.screens.CrewLoginScreen
import com.sinjeong.safety.ui.screens.DetailScreen
import com.sinjeong.safety.ui.screens.HomeScreen
import com.sinjeong.safety.ui.screens.LoginScreen
import com.sinjeong.safety.ui.screens.RegulationScreen
import com.sinjeong.safety.ui.screens.WriteScreen
import com.sinjeong.safety.ui.theme.SinjeongSafetyTheme

object Routes {
    const val HOME = "home"
    const val LOGIN = "login"
    const val CREW_LOGIN = "crew_login"      // 승무원 로그인 (게이트)
    const val WRITE = "write"                 // 새 글
    const val EDIT = "write/{postId}"         // 수정
    const val DETAIL = "detail/{postId}"
    const val REGULATION = "regulation"
    fun detail(id: String) = "detail/$id"
    fun edit(id: String) = "write/$id"
}

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 새 게시물 푸시 토픽 구독 + 안드로이드 13+ 알림 권한 요청
        FirebaseMessaging.getInstance().subscribeToTopic("new_posts")
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
                                    onRegulationClick = { nav.navigate(Routes.REGULATION) }
                                )
                            }

                            composable(Routes.REGULATION) {
                                RegulationScreen(onBack = { nav.popBackStack() })
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
                                    onEdit = { id -> nav.navigate(Routes.edit(id)) }
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
