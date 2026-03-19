// com/example/whichzup/chat/data/local/dao/UserDao.kt
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

    // Offline-first search: busca apenas no banco local e ignora o usuário atual
    @Query("SELECT * FROM users WHERE id != :currentUserId AND (name LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchUsersLocal(query: String, currentUserId: String): Flow<List<UserEntity>>

    // NOVO: Pega todos os contatos salvos localmente (para quando a busca estiver vazia)
    @Query("SELECT * FROM users WHERE id != :currentUserId ORDER BY name ASC")
    fun getAllContacts(currentUserId: String): Flow<List<UserEntity>>

    @Query("UPDATE users SET name = :name, profilePictureUrl = :profilePictureUrl, bio = :bio WHERE id = :userId")
    suspend fun updateProfileInfo(userId: String, name: String, profilePictureUrl: String, bio: String)

    @Query("UPDATE users SET status = :status, onlineStatus = :onlineStatus WHERE id = :userId")
    suspend fun updateStatus(userId: String, status: String, onlineStatus: Boolean)
}