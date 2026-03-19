// com/example/whichzup/MainActivity.kt
package com.example.whichzup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.whichzup.chat.data.local.AppDatabase
import com.example.whichzup.chat.data.repository.UserRepository
import com.example.whichzup.chat.data.repository.ChatRepository
import com.example.whichzup.chat.ui.login.LoginScreen
import com.example.whichzup.chat.ui.login.LoginViewModel
import com.example.whichzup.chat.ui.chatlist.ChatListScreen
import com.example.whichzup.chat.ui.chatlist.ChatListViewModel
import com.example.whichzup.chat.ui.chatroom.ChatRoomScreen
import com.example.whichzup.chat.ui.chatroom.ChatRoomViewModel
import com.example.whichzup.chat.ui.groupsettings.GroupSettingsScreen
import com.example.whichzup.chat.ui.groupsettings.GroupSettingsViewModel
import com.example.whichzup.chat.presentation.profile.ProfileScreen
import com.example.whichzup.chat.presentation.profile.ProfileViewModel
import com.example.whichzup.ui.theme.WhichZupTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()

        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "whichzup_database"
        )
            .fallbackToDestructiveMigration()
            .build()

        val userRepository = UserRepository(
            auth = auth,
            firestore = firestore,
            userDao = database.userDao(),
            context = applicationContext
        )

        val chatRepository = ChatRepository(
            firestore = firestore,
            chatDao = database.chatDao(),
            messageDao = database.messageDao()
        )

        setContent {
            WhichZupTheme {
                val context = LocalContext.current

                // NEW: Handle Android 13+ Notification Permission
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Handle permission granted/denied if necessary
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "login") {

                        composable("login") {
                            val loginViewModel: LoginViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return LoginViewModel(userRepository) as T
                                    }
                                }
                            )

                            LoginScreen(
                                viewModel = loginViewModel,
                                onNavigateToChatList = {
                                    navController.navigate("chatList") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("chatList") {
                            val chatListViewModel: ChatListViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return ChatListViewModel(userRepository, chatRepository) as T
                                    }
                                }
                            )

                            ChatListScreen(
                                viewModel = chatListViewModel,
                                onChatClick = { chatId ->
                                    navController.navigate("chatRoom/$chatId/Chat")
                                },
                                onProfileClick = {
                                    navController.navigate("profile")
                                },
                                onLogoutClick = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // UPDATED: Added deepLinks parameter for notification routing
                        composable(
                            route = "chatRoom/{chatId}/{chatName}",
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "whichzup://chat/{chatId}/{chatName}" }
                            )
                        ) { backStackEntry ->
                            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                            val chatName = backStackEntry.arguments?.getString("chatName") ?: "Chat"

                            val chatRoomViewModel: ChatRoomViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        val currentUserId = auth.currentUser?.uid ?: ""
                                        return ChatRoomViewModel(chatId, currentUserId, chatRepository) as T
                                    }
                                }
                            )

                            ChatRoomScreen(
                                chatName = chatName,
                                viewModel = chatRoomViewModel,
                                onNavigateBack = {
                                    // If opened from a notification, back stack might be empty
                                    if (navController.previousBackStackEntry == null) {
                                        navController.navigate("chatList") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                },
                                onNavigateToGroupSettings = { targetChatId ->
                                    navController.navigate("groupSettings/$targetChatId")
                                }
                            )
                        }

                        composable("groupSettings/{chatId}") { backStackEntry ->
                            // ... Group Settings implementation ...
                            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""

                            val groupSettingsViewModel: GroupSettingsViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        val currentUserId = auth.currentUser?.uid ?: ""
                                        return GroupSettingsViewModel(
                                            chatId = chatId,
                                            currentUserId = currentUserId,
                                            chatRepository = chatRepository,
                                            userRepository = userRepository
                                        ) as T
                                    }
                                }
                            )

                            GroupSettingsScreen(
                                viewModel = groupSettingsViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("profile") {
                            // ... Profile implementation ...
                            val profileViewModel: ProfileViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        return ProfileViewModel(userRepository, auth) as T
                                    }
                                }
                            )

                            ProfileScreen(viewModel = profileViewModel)
                        }
                    }
                }
            }
        }
    }
}