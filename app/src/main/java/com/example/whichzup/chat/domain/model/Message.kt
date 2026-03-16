/* Begin, prompt: Generate the data models... Messages with text, media URLs, timestamp, and status (sent, delivered, read). */
package com.example.whichzup.chat.domain.model
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    val id: String = "",
    val senderId: String = "",
    val text: String? = null,
    val mediaUrl: String? = null,
    @ServerTimestamp val timestamp: Date? = null,
    val status: String = MessageStatus.SENT.name,
    // Useful for group chats to track who specifically has read/delivered
    val readBy: List<String> = emptyList(),
    val deliveredTo: List<String> = emptyList()
)
/* End */