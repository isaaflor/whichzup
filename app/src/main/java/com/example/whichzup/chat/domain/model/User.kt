// File path: app/src/main/java/com/example/whichzup/chat/domain/model/User.kt
package com.example.whichzup.chat.domain.model

enum class UserStatus {
    ONLINE, OFFLINE, BUSY, AVAILABLE
}

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val profilePictureUrl: String = "",
    val bio: String = "",
    val status: UserStatus = UserStatus.OFFLINE,
    // Kept for backward compatibility with your current Firestore structure
    val onlineStatus: Boolean = false
)