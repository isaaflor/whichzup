package com.example.whichzup.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.whichzup.chat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :newStatus WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, newStatus: String)

    @Query("UPDATE messages SET isPinned = :isPinned WHERE id = :messageId")
    suspend fun updateMessagePinnedStatus(messageId:String, isPinned: Boolean)
}