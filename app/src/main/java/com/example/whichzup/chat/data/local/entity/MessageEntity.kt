package com.example.whichzup.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.whichzup.chat.domain.model.Message
import java.util.Date

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String, // Explicit association needed for relational DB
    val senderId: String,
    val text: String?,
    val mediaUrl: String?,
    val timestamp: Date?,
    val status: String,
    val readBy: List<String>,
    val deliveredTo: List<String>,
    val isPinned: Boolean
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    senderId = senderId,
    text = text,
    mediaUrl = mediaUrl,
    timestamp = timestamp,
    status = status,
    readBy = readBy,
    deliveredTo = deliveredTo,
    isPinned = isPinned
)

fun Message.toEntity(chatId: String): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    senderId = senderId,
    text = text,
    mediaUrl = mediaUrl,
    timestamp = timestamp,
    status = status,
    readBy = readBy,
    deliveredTo = deliveredTo,
    isPinned = isPinned
)