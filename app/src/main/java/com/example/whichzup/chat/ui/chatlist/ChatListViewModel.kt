// com/example/whichzup/chat/ui/chatlist/ChatListViewModel.kt
package com.example.whichzup.chat.ui.chatlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whichzup.chat.data.repository.ChatRepository
import com.example.whichzup.chat.data.repository.UserRepository
import com.example.whichzup.chat.domain.model.AuthState
import com.example.whichzup.chat.domain.model.Chat
import com.example.whichzup.chat.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiModel(
    val chat: Chat,
    val displayName: String,
    val displayImageUrl: String,
    val status: String
)

class ChatListViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)

    // Search States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Group Creation States
    private val _isGroupMode = MutableStateFlow(false)
    val isGroupMode = _isGroupMode.asStateFlow()

    private val _selectedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedUserIds = _selectedUserIds.asStateFlow()

    private val _showCreateGroupDialog = MutableStateFlow(false)
    val showCreateGroupDialog = _showCreateGroupDialog.asStateFlow()

    // NEW: Sync Status State for UI Feedback
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus = _syncStatus.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchedContacts: StateFlow<List<User>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else userRepository.searchUsers(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allChats: Flow<List<ChatUiModel>> = userRepository.authState
        .flatMapLatest { authState ->
            if (authState is AuthState.Authenticated) {
                _currentUserId.value = authState.user.id

                chatRepository.getUserChats(authState.user.id).map { chatList ->
                    chatList.map { chat ->
                        var displayName = chat.name ?: "Unknown"
                        var displayImageUrl = chat.groupImageUrl ?: ""
                        var statusIndicator = "OFFLINE"

                        if (chat.participantIds.size == 2 && !chat.isGroup) {
                            val otherUserId = chat.participantIds.firstOrNull { it != authState.user.id }
                            if (otherUserId != null) {
                                try {
                                    var userProfile = userRepository.getUserProfile(otherUserId).firstOrNull()
                                    if (userProfile == null) {
                                        userRepository.fetchProfileFromNetwork(otherUserId)
                                        userProfile = userRepository.getUserProfile(otherUserId).firstOrNull()
                                    }

                                    if (userProfile != null) {
                                        displayName = userProfile.name
                                        displayImageUrl = userProfile.profilePictureUrl
                                        statusIndicator = userProfile.status.name
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatApp", "Error fetching user data", e)
                                }
                            }
                        }
                        ChatUiModel(chat, displayName, displayImageUrl, statusIndicator)
                    }
                }
            } else {
                _currentUserId.value = null
                flowOf(emptyList())
            }
        }

    val chats: StateFlow<List<ChatUiModel>> = combine(allChats, _searchQuery) { chatList, query ->
        if (query.isBlank()) {
            chatList
        } else {
            chatList.filter {
                it.displayName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getCurrentUserId(): String? = _currentUserId.value

    fun logout() {
        viewModelScope.launch {
            userRepository.signOut()
        }
    }

    // UPDATED: Now provides feedback to the UI
    fun syncContacts() {
        viewModelScope.launch {
            _syncStatus.value = "Scanning contacts..."
            userRepository.syncDeviceContacts()
                .onSuccess {
                    _syncStatus.value = "Contacts imported successfully!"
                }
                .onFailure {
                    Log.e("ChatListViewModel", "Failed to sync contacts", it)
                    _syncStatus.value = "Failed to import contacts."
                }
        }
    }

    fun clearSyncStatus() {
        _syncStatus.value = null
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId).onFailure {
                Log.e("ChatListViewModel", "Failed to delete chat", it)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGroupMode() {
        _isGroupMode.value = !_isGroupMode.value
        if (!_isGroupMode.value) {
            _selectedUserIds.value = emptySet()
            _searchQuery.value = ""
        }
    }

    fun toggleUserSelection(userId: String) {
        val current = _selectedUserIds.value.toMutableSet()
        if (current.contains(userId)) current.remove(userId) else current.add(userId)
        _selectedUserIds.value = current
    }

    fun onNextGroupClicked() {
        if (_selectedUserIds.value.isNotEmpty()) {
            _showCreateGroupDialog.value = true
        }
    }

    fun onDismissGroupDialog() {
        _showCreateGroupDialog.value = false
    }

    fun createOneOnOneChat(targetUserId: String) {
        val currentUserId = _currentUserId.value ?: return
        if (currentUserId == targetUserId) return

        val newChat = Chat(
            id = UUID.randomUUID().toString(),
            isGroup = false,
            participantIds = listOf(currentUserId, targetUserId),
            lastMessageText = "Chat started",
            lastMessageSenderId = currentUserId,
            lastMessageTimestamp = null
        )

        viewModelScope.launch {
            chatRepository.createChat(newChat).onSuccess {
                updateSearchQuery("")
            }
        }
    }

    fun createGroupChat(groupName: String, groupImageUrl: String) {
        val currentUserId = _currentUserId.value ?: return
        if (groupName.isBlank() || _selectedUserIds.value.isEmpty()) return

        val participantIds = _selectedUserIds.value.toMutableList().apply { add(currentUserId) }

        val newChat = Chat(
            id = UUID.randomUUID().toString(),
            isGroup = true,
            name = groupName,
            groupImageUrl = groupImageUrl,
            participantIds = participantIds,
            adminId = currentUserId,
            lastMessageText = "Group created",
            lastMessageSenderId = currentUserId,
            lastMessageTimestamp = null
        )

        viewModelScope.launch {
            chatRepository.createChat(newChat).onSuccess {
                _showCreateGroupDialog.value = false
                toggleGroupMode()
            }
        }
    }
}