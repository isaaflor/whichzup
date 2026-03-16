package com.example.whichzup.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.whichzup.chat.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUser(userId: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // Insert a list of users synced from search or contacts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    // Offline-first search querying both name and email
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%'")
    fun searchUsersLocal(query: String): Flow<List<UserEntity>>

    @Query("UPDATE users SET name = :name, profilePictureUrl = :profilePictureUrl, bio = :bio WHERE id = :userId")
    suspend fun updateProfileInfo(userId: String, name: String, profilePictureUrl: String, bio: String)

    @Query("UPDATE users SET status = :status, onlineStatus = :onlineStatus WHERE id = :userId")
    suspend fun updateStatus(userId: String, status: String, onlineStatus: Boolean)
}