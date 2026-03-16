package com.example.whichzup.chat.ui.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whichzup.chat.domain.model.Chat
import com.example.whichzup.chat.domain.model.Message
import com.example.whichzup.chat.data.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ChatRoomViewModel(
    private val chatId: String,
    val currentUserId: String,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // NEW: Expose the current chat so the UI knows if it's a group
    val currentChat: StateFlow<Chat?> = chatRepository
        .getUserChats(currentUserId)
        .map { chats -> chats.find { it.id == chatId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Fetches messages and groups them by date for the UI
    val groupedMessages: StateFlow<Map<String, List<Message>>> = chatRepository
        .getMessages(chatId)
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

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val message = Message(
                senderId = currentUserId,
                text = text.trim()
            )
            chatRepository.sendMessage(chatId, message)
        }
    }
}