package com.example.whichzup.chat.domain.model

import com.google.firebase.firestore.PropertyName // Add this import
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Chat(
    val id: String = "",
    @get:PropertyName("isGroup") // Forces Firestore to save/read as "isGroup"
    val isGroup: Boolean = false,
    val name: String? = null,
    val groupImageUrl: String? = null,
    val participantIds: List<String> = emptyList(),
    val adminId: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageTimestamp: Date? = null
)