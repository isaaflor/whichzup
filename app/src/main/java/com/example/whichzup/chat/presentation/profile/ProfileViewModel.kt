package com.example.whichzup.chat.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whichzup.chat.data.repository.UserRepository
import com.example.whichzup.chat.domain.model.User
import com.example.whichzup.chat.domain.model.UserStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val currentUserId = auth.currentUser?.uid ?: ""

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        if (currentUserId.isNotEmpty()) {
            observeLocalProfile()
            syncProfileFromNetwork()
        }
    }

    private fun observeLocalProfile() {
        viewModelScope.launch {
            userRepository.getUserProfile(currentUserId).collectLatest { user ->
                _userProfile.value = user
            }
        }
    }

    private fun syncProfileFromNetwork() {
        viewModelScope.launch {
            userRepository.fetchProfileFromNetwork(currentUserId)
        }
    }

    fun saveProfileChanges(name: String, bio: String, profilePictureUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            userRepository.updateProfileInfo(currentUserId, name, bio, profilePictureUrl)
            _isLoading.value = false
        }
    }

    fun updateStatus(status: UserStatus) {
        viewModelScope.launch {
            userRepository.updateUserStatus(currentUserId, status)
        }
    }
}