// com/example/whichzup/chat/service/ChatFirebaseMessagingService.kt
package com.example.whichzup.chat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.whichzup.R

class ChatFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: Sync this new FCM token to Firestore
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val chatId = data["chatId"] ?: return
            val senderName = data["senderName"] ?: "Unknown"
            val messageText = data["text"] ?: "You received a new message."

            // Extract group info if provided by your backend payload
            val isGroup = data["isGroup"]?.toBoolean() ?: false
            val groupName = data["groupName"]

            // Dynamic Title Logic
            val title = if (isGroup && !groupName.isNullOrEmpty()) groupName else senderName

            // We need a display name to pass to the ChatRoom route
            val chatNameToPass = if (isGroup && !groupName.isNullOrEmpty()) groupName else senderName

            showNotification(title, messageText, chatId, chatNameToPass)
        }
    }

    private fun showNotification(title: String, message: String, chatId: String, chatName: String) {
        val channelId = "chat_messages_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Encode the chat name so it doesn't break the URI if it contains spaces
        val encodedChatName = Uri.encode(chatName)

        // Deep Link Intent using the scheme defined in AndroidManifest
        val deepLinkUri = Uri.parse("whichzup://chat/$chatId/$encodedChatName")
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Use chatId.hashCode() to group notifications by chat
        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Consider creating an ic_stat_chat in the future
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(chatId.hashCode(), notificationBuilder.build())
    }
}