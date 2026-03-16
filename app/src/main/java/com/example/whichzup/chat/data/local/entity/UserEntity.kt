package com.example.whichzup.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.whichzup.chat.domain.model.User
import com.example.whichzup.chat.domain.model.UserStatus

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val profilePictureUrl: String,
    val bio: String,
    val status: String,
    val onlineStatus: Boolean
)

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    profilePictureUrl = profilePictureUrl,
    bio = bio,
    status = try { UserStatus.valueOf(status) } catch (e: Exception) { UserStatus.OFFLINE },
    onlineStatus = onlineStatus
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    email = email,
    profilePictureUrl = profilePictureUrl,
    bio = bio,
    status = status.name,
    onlineStatus = onlineStatus
)