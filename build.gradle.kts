plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    // 앱이 죽었을 때 원인을 자동으로 보고받기 위한 플러그인
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}
