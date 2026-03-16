package com.example.whichzup.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.whichzup.chat.data.local.dao.ChatDao
import com.example.whichzup.chat.data.local.dao.MessageDao
import com.example.whichzup.chat.data.local.dao.UserDao
import com.example.whichzup.chat.data.local.entity.ChatEntity
import com.example.whichzup.chat.data.local.entity.MessageEntity
import com.example.whichzup.chat.data.local.entity.UserEntity

@Database(
    entities = [ChatEntity::class, MessageEntity::class, UserEntity::class],
    version = 2, // Incremented version for the new table
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
}