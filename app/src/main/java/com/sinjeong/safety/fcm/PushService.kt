package com.sinjeong.safety.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sinjeong.safety.MainActivity
import com.sinjeong.safety.R

/**
 * 새 게시물 푸시 수신
 * - 서버(Cloud Functions)가 "new_posts" 토픽으로 발송 → 여기서 알림 표시
 * - 앱이 포그라운드일 때도 알림이 뜨도록 직접 빌드
 */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"] ?: "📢 새 안전정보"
        val body = message.notification?.body
            ?: message.data["body"] ?: "새 게시물이 등록되었습니다"
        val postId = message.data["postId"]

        showNotification(title, body, postId)
    }

    override fun onNewToken(token: String) {
        // 토픽 구독 방식이므로 개별 토큰 저장은 불필요
    }

    private fun showNotification(title: String, body: String, postId: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID, "새 게시물 알림", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "새 안전정보가 등록되면 알려드려요" }
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            postId?.let { putExtra("postId", it) }
        }
        val pending = PendingIntent.getActivity(
            this, postId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "new_posts"
    }
}
