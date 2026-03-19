// com/example/whichzup/chat/domain/model/Message.kt
package com.example.whichzup.chat.domain.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    val id: String = "",
    val senderId: String = "",
    val text: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null, // e.g., "image", "video", "audio", "document"
    val mediaFileName: String? = null,
    @ServerTimestamp val timestamp: Date? = null,
    val status: String = MessageStatus.SENDING.name,
    // Useful for group chats to track who specifically has read/delivered
    val readBy: List<String> = emptyList(),
    val deliveredTo: List<String> = emptyList(),
    @get:PropertyName("isPinned")
    val isPinned: Boolean = false
)