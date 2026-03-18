package com.example.whichzup.chat.ui.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whichzup.chat.domain.model.Chat
import com.example.whichzup.chat.domain.model.Message
import com.example.whichzup.chat.domain.model.MessageStatus
import com.example.whichzup.chat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class ChatRoomViewModel(
    private val chatId: String,
    val currentUserId: String,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

        .onEach { messages ->
            val unreadIds = messages.filter{
                it.senderId!=currentUserId && it.status != MessageStatus.READ.name
            }.map{it.id}

            if (unreadIds.isNotEmpty()){
                viewModelScope.launch{
                    chatRepository.markMessagesAsRead(chatId,unreadIds)
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

    fun togglePinMessage(messageId: String, isPinned: Boolean){
        viewModelScope.launch{
            chatRepository.toggleMessagePin(chatId,messageId, isPinned)
        }
    }

    fun onSearchQueryChanged(query: String){
        _searchQuery.value = query
    }

    fun sendImageMessage(uri: String) {
        viewModelScope.launch {
            val message = Message(
                senderId = currentUserId,
                text = null,
                mediaUrl = uri,
                status = MessageStatus.SENT.name
            )
            chatRepository.sendMessage(chatId, message)
        }
    }

    fun uploadMediaAndSendMessage(localUri: android.net.Uri, isAudio: Boolean = false) {
        val storageRef = FirebaseStorage.getInstance().reference
        // Cria um nome único para o arquivo para não sobrescrever outros
        val fileName = if (isAudio) "audios/${UUID.randomUUID()}.mp3" else "images/${UUID.randomUUID()}.jpg"
        val fileRef = storageRef.child(fileName)

        // Faz o upload do arquivo físico
        fileRef.putFile(localUri)
            .addOnSuccessListener {
                // Após o sucesso, pedimos a URL pública (Download URL)
                fileRef.downloadUrl.addOnSuccessListener { publicUrl ->
                    // Agora enviamos a mensagem com a URL que TODO MUNDO consegue ver
                    sendMediaMessage(publicUrl.toString())
                }
            }
            .addOnFailureListener {
                // Trate o erro aqui (ex: log ou mudar um estado de erro na UI)
            }
    }

    private fun sendMediaMessage(publicUrl: String) {
        viewModelScope.launch {
            val message = Message(
                senderId = currentUserId,
                text = null,
                mediaUrl = publicUrl,
                status = MessageStatus.SENT.name
            )
            chatRepository.sendMessage(chatId, message)
        }
    }
}