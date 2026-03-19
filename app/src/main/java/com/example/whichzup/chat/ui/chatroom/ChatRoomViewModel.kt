// com/example/whichzup/chat/ui/chatroom/ChatRoomViewModel.kt
package com.example.whichzup.chat.ui.chatroom

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whichzup.chat.domain.model.Chat
import com.example.whichzup.chat.domain.model.Message
import com.example.whichzup.chat.domain.model.MessageStatus
import com.example.whichzup.chat.data.repository.ChatRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

data class PendingAttachment(
    val uri: Uri,
    val type: String, // "image", "video", "audio", "document"
    val fileName: String
)

class ChatRoomViewModel(
    private val chatId: String,
    val currentUserId: String,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    val currentChat: StateFlow<Chat?> = chatRepository
        .getUserChats(currentUserId)
        .map { chats -> chats.find { it.id == chatId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val groupedMessages: StateFlow<Map<String, List<Message>>> = chatRepository
        .getMessages(chatId)
        .onEach { messages ->
            val unreadIds = messages.filter {
                it.senderId != currentUserId && it.status != MessageStatus.READ.name
            }.map { it.id }

            if (unreadIds.isNotEmpty()) {
                viewModelScope.launch {
                    chatRepository.markMessagesAsRead(chatId, unreadIds)
                }
            }
        }
        .map { messages ->
            messages.groupBy { message ->
                message.timestamp?.let { dateFormatter.format(it) } ?: "Today"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun setPendingAttachment(uri: Uri, type: String, fileName: String) {
        _pendingAttachment.value = PendingAttachment(uri, type, fileName)
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun togglePinMessage(messageId: String, isPinned: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleMessagePin(chatId, messageId, isPinned)
        }
    }

    fun sendMessage(text: String) {
        val attachment = _pendingAttachment.value

        if (attachment != null) {
            clearPendingAttachment()
            uploadAttachmentAndSendMessage(attachment, text.takeIf { it.isNotBlank() })
        } else if (text.isNotBlank()) {
            viewModelScope.launch {
                val message = Message(
                    senderId = currentUserId,
                    text = text.trim()
                )
                chatRepository.sendMessage(chatId, message)
            }
        }
    }

    private fun uploadAttachmentAndSendMessage(attachment: PendingAttachment, caption: String?) {
        val storageRef = FirebaseStorage.getInstance().reference
        val uniqueId = UUID.randomUUID().toString()
        val extension = attachment.fileName.substringAfterLast('.', "")

        val folder = when(attachment.type) {
            "image" -> "images"
            "video" -> "videos"
            "audio" -> "audios"
            else -> "documents"
        }

        val fileName = if (extension.isNotBlank()) "$uniqueId.$extension" else uniqueId
        val fileRef = storageRef.child("$folder/$fileName")

        fileRef.putFile(attachment.uri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { publicUrl ->
                    viewModelScope.launch {
                        val message = Message(
                            senderId = currentUserId,
                            text = caption,
                            mediaUrl = publicUrl.toString(),
                            mediaType = attachment.type,
                            mediaFileName = attachment.fileName,
                            status = MessageStatus.SENT.name
                        )
                        chatRepository.sendMessage(chatId, message)
                    }
                }
            }
            .addOnFailureListener {
                // TODO: Handle upload failure (e.g., expose an error state to the UI)
            }
    }

    // Kept for direct audio recording where we skip the preview banner
    fun uploadAudioDirectly(localUri: Uri) {
        val attachment = PendingAttachment(localUri, "audio", "voice_message_${System.currentTimeMillis()}.mp3")
        uploadAttachmentAndSendMessage(attachment, null)
    }
}