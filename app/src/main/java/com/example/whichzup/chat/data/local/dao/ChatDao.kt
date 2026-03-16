package com.example.whichzup.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.whichzup.chat.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE participantIds LIKE '%' || :userId || '%' ORDER BY lastMessageTimestamp DESC")
    fun getUserChats(userId: String): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    // NEW: Delete chat locally
    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatLocally(chatId: String)
}