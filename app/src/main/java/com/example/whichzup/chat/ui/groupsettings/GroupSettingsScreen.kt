package com.example.whichzup.chat.ui.groupsettings

import androidx.compose.foundation.clickable // ???
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    viewModel: GroupSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val chat by viewModel.currentChat.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    if (chat == null) return // Loading state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- Group Info Section ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat?.name ?: "Unnamed Group",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isAdmin) {
                        IconButton(onClick = { showEditNameDialog = true }) {
                            Icon(Icons.Filled.Edit, "Edit Name")
                        }
                    }
                }
            }

            // --- Members Section ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Members (${participants.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isAdmin) {
                    IconButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Filled.PersonAdd, "Add Member")
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(participants, key = { it.id }) { user ->
                    ListItem(
                        headlineContent = { Text(user.name) },
                        supportingContent = { Text(user.email) },
                        trailingContent = {
                            if (isAdmin && user.id != viewModel.currentUserId) {
                                IconButton(onClick = { viewModel.removeParticipant(user.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else if (user.id == chat?.adminId) {
                                Text("Admin", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // --- Dialogs ---
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(chat?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Group Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateGroupName(newName)
                    showEditNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddMemberDialog = false
                viewModel.updateSearchQuery("")
            },
            title = { Text("Add Member") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = { Text("Search by name...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(searchResults) { user ->
                            if (!chat!!.participantIds.contains(user.id)) {
                                ListItem(
                                    headlineContent = { Text(user.name) },
                                    modifier = Modifier.clickable {
                                        viewModel.addParticipant(user.id)
                                        showAddMemberDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMemberDialog = false }) { Text("Close") }
            }
        )
    }
}