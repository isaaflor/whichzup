package com.example.whichzup.chat.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.whichzup.chat.domain.model.UserStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var name by remember(userProfile) { mutableStateOf(userProfile?.name ?: "") }
    var bio by remember(userProfile) { mutableStateOf(userProfile?.bio ?: "") }
    var imageUrl by remember(userProfile) { mutableStateOf(userProfile?.profilePictureUrl ?: "") }
    var expandedStatus by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Edit Profile") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Profile Picture URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedStatus,
                onExpandedChange = { expandedStatus = !expandedStatus }
            ) {
                OutlinedTextField(
                    value = userProfile?.status?.name ?: "Select Status",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Current Status") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStatus) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedStatus,
                    onDismissRequest = { expandedStatus = false }
                ) {
                    UserStatus.values().forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.name) },
                            onClick = {
                                viewModel.updateStatus(status)
                                expandedStatus = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveProfileChanges(name, bio, imageUrl) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Profile")
                }
            }
        }
    }
}