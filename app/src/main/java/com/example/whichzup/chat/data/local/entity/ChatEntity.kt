package com.example.whichzup.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.whichzup.chat.domain.model.Chat
import java.util.Date

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val isGroup: Boolean,
    val name: String?,
    val groupImageUrl: String?,
    val participantIds: List<String>,
    val adminId: String, // Added adminId
    val lastMessageText: String,
    val lastMessageSenderId: String,
    val lastMessageTimestamp: Date?
)

fun ChatEntity.toDomain(): Chat = Chat(
    id = id,
    isGroup = isGroup,
    name = name,
    groupImageUrl = groupImageUrl,
    participantIds = participantIds,
    adminId = adminId, // Mapped adminId
    lastMessageText = lastMessageText,
    lastMessageSenderId = lastMessageSenderId,
    lastMessageTimestamp = lastMessageTimestamp
)

fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id,
    isGroup = isGroup,
    name = name,
    groupImageUrl = groupImageUrl,
    participantIds = participantIds,
    adminId = adminId, // Mapped adminId
    lastMessageText = lastMessageText,
    lastMessageSenderId = lastMessageSenderId,
    lastMessageTimestamp = lastMessageTimestamp
)