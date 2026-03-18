package com.example.whichzup.chat.ui.groupsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whichzup.chat.data.repository.ChatRepository
import com.example.whichzup.chat.data.repository.UserRepository
import com.example.whichzup.chat.domain.model.Chat
import com.example.whichzup.chat.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GroupSettingsViewModel(
    private val chatId: String,
    val currentUserId: String,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // Isolate the specific chat from the user's chat list
    val currentChat: StateFlow<Chat?> = chatRepository.getUserChats(currentUserId)
        .map { chats -> chats.find { it.id == chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Check admin status dynamically
    val isAdmin: StateFlow<Boolean> = currentChat
        .map { it?.adminId == currentUserId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Fetch rich User objects for all participant IDs
    @OptIn(ExperimentalCoroutinesApi::class)
    val participants: StateFlow<List<User>> = currentChat
        .flatMapLatest { chat ->
            if (chat == null || chat.participantIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                // Combine the flows of individual user profiles into a single list
                val userFlows = chat.participantIds.map { userId ->
                    userRepository.getUserProfile(userId)
                }
                combine(userFlows) { users ->
                    users.filterNotNull()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search logic for adding new members
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<User>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else userRepository.searchUsers(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateGroupName(newName: String) {
        val chat = currentChat.value ?: return
        if (newName.isBlank() || chat.adminId != currentUserId) return

        viewModelScope.launch {
            chatRepository.updateGroupDetails(chatId, newName, chat.groupImageUrl)
        }
    }

    fun addParticipant(userId: String) {
        val chat = currentChat.value ?: return
        if (chat.adminId != currentUserId || chat.participantIds.contains(userId)) return

        viewModelScope.launch {
            chatRepository.addParticipants(chatId, listOf(userId))
            updateSearchQuery("") // Clear search after adding
        }
    }

    fun removeParticipant(userId: String) {
        val chat = currentChat.value ?: return
        if (chat.adminId != currentUserId || userId == currentUserId) return // Admin can't remove themselves here

        viewModelScope.launch {
            chatRepository.removeParticipant(chatId, userId)
        }
    }
}