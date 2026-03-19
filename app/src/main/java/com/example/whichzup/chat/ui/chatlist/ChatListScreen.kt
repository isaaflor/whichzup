// com/example/whichzup/chat/ui/chatlist/ChatListScreen.kt
package com.example.whichzup.chat.ui.chatlist

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.whichzup.chat.domain.model.User
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (chatId: String) -> Unit,
    onProfileClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val searchedContacts by viewModel.searchedContacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val isGroupMode by viewModel.isGroupMode.collectAsStateWithLifecycle()
    val selectedUserIds by viewModel.selectedUserIds.collectAsStateWithLifecycle()
    val showGroupDialog by viewModel.showCreateGroupDialog.collectAsStateWithLifecycle()

    // NOVO: Observe o estado do modal de Adicionar Contato
    val showAddContactDialog by viewModel.showAddContactDialog.collectAsStateWithLifecycle()

    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    LaunchedEffect(syncStatus) {
        syncStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSyncStatus()
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.syncContacts()
        } else {
            Toast.makeText(context, "Permission denied. Cannot import contacts.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isGroupMode) {
                        Text("New Group (${selectedUserIds.size})")
                    } else {
                        Text("Messages")
                    }
                },
                navigationIcon = {
                    if (isGroupMode) {
                        IconButton(onClick = viewModel::toggleGroupMode) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!isGroupMode) {
                        // NOVO: Ícone para adicionar contato manualmente
                        IconButton(onClick = viewModel::onAddContactClicked) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Add Contact")
                        }
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.syncContacts()
                            } else {
                                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }) {
                            Icon(Icons.Filled.Contacts, contentDescription = "Import Contacts")
                        }
                        IconButton(onClick = viewModel::toggleGroupMode) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "New Group")
                        }
                        IconButton(onClick = onProfileClick) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                        }
                        IconButton(onClick = {
                            viewModel.logout()
                            onLogoutClick()
                        }) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = "Logout")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isGroupMode && selectedUserIds.isNotEmpty()) {
                FloatingActionButton(onClick = viewModel::onNextGroupClicked) {
                    Icon(imageVector = Icons.Filled.ArrowForward, contentDescription = "Next")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(if (isGroupMode) "Search contacts..." else "Search chats or new contacts...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                shape = CircleShape
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (searchedContacts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Contacts",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(searchedContacts, key = { "contact_${it.id}" }) { user ->
                        ContactItem(
                            user = user,
                            isSelected = selectedUserIds.contains(user.id),
                            isSelectionMode = isGroupMode,
                            onClick = {
                                if (isGroupMode) {
                                    viewModel.toggleUserSelection(user.id)
                                } else {
                                    viewModel.createOneOnOneChat(user.id)
                                }
                            }
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                if (!isGroupMode) {
                    item {
                        Text(
                            text = "Recent Chats",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(chats, key = { it.chat.id }) { uiModel ->
                        ChatItem(
                            uiModel = uiModel,
                            onClick = { onChatClick(uiModel.chat.id) },
                            onDeleteClick = { viewModel.deleteChat(uiModel.chat.id) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }

        // NOVO: Exibe o modal se o estado for true
        if (showAddContactDialog) {
            AddContactDialog(
                onDismiss = viewModel::onDismissAddContactDialog,
                onConfirm = viewModel::addContactByEmail
            )
        }

        if (showGroupDialog) {
            CreateGroupDialog(
                onDismiss = viewModel::onDismissGroupDialog,
                onConfirm = viewModel::createGroupChat
            )
        }
    }
}

// NOVO: Componente do Modal
@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("User Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email) },
                enabled = email.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, imageUrl: String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var groupImageUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = groupImageUrl,
                    onValueChange = { groupImageUrl = it },
                    label = { Text("Group Image URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(groupName, groupImageUrl) },
                enabled = groupName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ContactItem(
    user: User,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        Box(modifier = Modifier.size(48.dp)) {
            if (user.profilePictureUrl.isNotBlank()) {
                AsyncImage(
                    model = user.profilePictureUrl,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.name.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(text = user.name, fontWeight = FontWeight.Bold)
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChatItem(
    uiModel: ChatUiModel,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val statusColor = when (uiModel.status.uppercase()) {
        "ONLINE", "AVAILABLE" -> Color(0xFF4CAF50)
        "BUSY" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            if (uiModel.displayImageUrl.isNotBlank()) {
                AsyncImage(
                    model = uiModel.displayImageUrl,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = uiModel.displayName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(statusColor)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiModel.displayName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeString = uiModel.chat.lastMessageTimestamp?.let { timeFormat.format(it) } ?: ""
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = uiModel.chat.lastMessageText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDeleteClick, modifier = Modifier.padding(start = 8.dp)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete Chat",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}