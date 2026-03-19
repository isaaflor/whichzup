// File path: app/src/main/java/com/example/whichzup/chat/ui/chatroom/ChatRoomScreen.kt
package com.example.whichzup.chat.ui.chatroom
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.whichzup.chat.domain.model.Message
import com.example.whichzup.chat.domain.model.MessageStatus
import com.example.whichzup.chat.domain.model.User
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chatName: String, // Keeping this to avoid breaking your current NavHost route, but we will override it visually
    viewModel: ChatRoomViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGroupSettings: (chatId: String) -> Unit
) {
    val groupedMessages by viewModel.groupedMessages.collectAsStateWithLifecycle()
    val currentChat by viewModel.currentChat.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()

    // New states for dynamic profiles
    val dynamicChatName by viewModel.dynamicChatName.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Visual Media Picker (Images & Videos)
    val pickMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val type = getMediaType(context, uri)
            val name = getFileName(context, uri)
            viewModel.setPendingAttachment(uri, type, name)
        }
    }

    // Document/File Picker
    val pickDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val type = getMediaType(context, uri)
            val name = getFileName(context, uri)
            viewModel.setPendingAttachment(uri, type, name)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocationAndSend(context, fusedLocationClient, viewModel)
        } else {
            Toast.makeText(context, "Location permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                val name = "camera_capture_${System.currentTimeMillis()}.jpg"
                viewModel.setPendingAttachment(uri, "image", name)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                val uri = createTempPictureUri(context)
                tempImageUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val audioRecorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp3")
            audioFile = file
            audioRecorder.startRecording(file)
            isRecording = true
        }
    }

    val totalMessages = groupedMessages.values.sumOf { it.size }
    LaunchedEffect(totalMessages) {
        if (totalMessages > 0 && !isSearching) {
            delay(100)
            listState.scrollToItem(0)
        }
    }

    val pinnedMessage = remember(groupedMessages) {
        groupedMessages.values.flatten().findLast { it.isPinned }
    }

    val flatList = remember(groupedMessages) {
        val list = mutableListOf<Any>()
        groupedMessages.entries.toList().reversed().forEach { (date, messages) ->
            list.addAll(messages.reversed())
            list.add(date)
        }
        list
    }

    val matchedIndices = remember(flatList, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            flatList.mapIndexedNotNull { index, item ->
                if (item is Message && item.text?.contains(searchQuery, ignoreCase = true) == true) index else null
            }
        }
    }

    var currentMatchIndex by remember { mutableStateOf(0) }
    LaunchedEffect(matchedIndices) { if (matchedIndices.isNotEmpty()) currentMatchIndex = 0 }

    fun scrollToCurrentMatch() {
        if (matchedIndices.isNotEmpty() && currentMatchIndex in matchedIndices.indices) {
            coroutineScope.launch { listState.animateScrollToItem(matchedIndices[currentMatchIndex]) }
        }
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search...", maxLines = 1) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                scrollToCurrentMatch()
                                keyboardController?.hide()
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            viewModel.onSearchQueryChanged("")
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
                    },
                    actions = {
                        if (matchedIndices.isNotEmpty()) {
                            Text("${currentMatchIndex + 1}/${matchedIndices.size}", style = MaterialTheme.typography.labelMedium)
                            IconButton(
                                onClick = { if (currentMatchIndex < matchedIndices.size - 1) { currentMatchIndex++; scrollToCurrentMatch() } },
                                enabled = currentMatchIndex < matchedIndices.size - 1
                            ) { Icon(Icons.Filled.KeyboardArrowUp, "Older") }
                            IconButton(
                                onClick = { if (currentMatchIndex > 0) { currentMatchIndex--; scrollToCurrentMatch() } },
                                enabled = currentMatchIndex > 0
                            ) { Icon(Icons.Filled.KeyboardArrowDown, "Newer") }
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) { Icon(Icons.Filled.Close, "Clear text") }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(dynamicChatName) }, // Updated to use dynamic name
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { isSearching = true }) { Icon(Icons.Filled.Search, "Search messages") }
                        if (currentChat?.isGroup == true) {
                            IconButton(onClick = { currentChat?.id?.let { onNavigateToGroupSettings(it) } }) { Icon(Icons.Filled.Info, "Group Settings") }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isSearching) {
                Column {
                    if (pendingAttachment != null) {
                        PendingAttachmentPreview(
                            attachment = pendingAttachment!!,
                            onRemove = { viewModel.clearPendingAttachment() }
                        )
                    }
                    ChatInputBar(
                        text = inputText,
                        onTextChanged = { inputText = it },
                        onSendClicked = {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        },
                        onLocationClicked = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        onCameraClicked = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onGalleryClicked = { pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        onFileClicked = { pickDocLauncher.launch("*/*") },
                        onMicClicked = {
                            if (!isRecording) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else {
                                try {
                                    audioRecorder.stopRecording()
                                    isRecording = false
                                    audioFile?.let { file ->
                                        viewModel.uploadAudioDirectly(Uri.fromFile(file))
                                    }
                                } catch (e: Exception) {
                                    isRecording = false
                                    Toast.makeText(context, "Error saving audio.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        isRecording = isRecording,
                        hasPendingAttachment = pendingAttachment != null
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pinnedMessage != null && !isSearching) {
                PinnedMessageBanner(message = pinnedMessage, onUnpin = { viewModel.togglePinMessage(pinnedMessage.id, false) })
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                reverseLayout = true
            ) {
                groupedMessages.entries.toList().reversed().forEach { (date, messages) ->
                    items(messages.reversed(), key = { it.id }) { message ->
                        val isMine = message.senderId == viewModel.currentUserId
                        val senderUser = participants[message.senderId]

                        MessageBubble(
                            message = message,
                            isMine = isMine,
                            sender = senderUser,
                            isGroup = currentChat?.isGroup == true,
                            searchQuery = searchQuery,
                            onTogglePin = { isPinned -> viewModel.togglePinMessage(message.id, isPinned) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    item { DateHeader(date) }
                }
            }
        }
    }
}

// ... PendingAttachmentPreview, getMessageAnnotatedString, PinnedMessageBanner remain exactly the same ...
@Composable
fun PendingAttachmentPreview(attachment: PendingAttachment, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        if (attachment.type == "image" || attachment.type == "video") {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                coil.compose.AsyncImage(
                    model = attachment.uri,
                    contentDescription = "Attachment Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (attachment.type == "video") {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = "Video",
                        modifier = Modifier.size(48.dp).align(Alignment.Center),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(Icons.Filled.Close, "Remove attachment", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (attachment.type == "audio") Icons.Filled.AudioFile else Icons.AutoMirrored.Filled.InsertDriveFile
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (attachment.type == "audio") "Audio File" else "Document",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove attachment") }
            }
        }
    }
}

@Composable
fun getMessageAnnotatedString(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(text)
        if (query.isNotBlank()) {
            var startIndex = 0
            while (startIndex < text.length) {
                val index = text.indexOf(query, startIndex, ignoreCase = true)
                if (index == -1) break
                addStyle(SpanStyle(background = Color.Yellow, color = Color.Black), index, index + query.length)
                startIndex = index + query.length
            }
        }
        val urlPattern = android.util.Patterns.WEB_URL.toRegex()
        val matches = urlPattern.findAll(text)
        for (match in matches) {
            var url = match.value
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            addStyle(SpanStyle(color = Color(0xFF90CAF9), textDecoration = TextDecoration.Underline), match.range.first, match.range.last + 1)
            addStringAnnotation("URL", url, match.range.first, match.range.last + 1)
        }
    }
}

@Composable
fun PinnedMessageBanner(message: Message, onUnpin: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PushPin, "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Mensagem Fixada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(message.text ?: "Mídia", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUnpin) { Icon(Icons.Filled.Close, "Desfixar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    sender: User?, // Added sender User object
    isGroup: Boolean, // Know if we are in a group to show names
    searchQuery: String = "",
    onTogglePin: (Boolean) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = message.timestamp?.let { timeFormat.format(it) } ?: "Sending..."
    var showMenu by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            // Profile Picture Logic
            if (!sender?.profilePictureUrl.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = sender?.profilePictureUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(sender?.name?.take(1)?.uppercase() ?: "U", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 0.dp, bottomEnd = if (isMine) 0.dp else 16.dp
                ),
                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 280.dp).combinedClickable(onClick = {}, onLongClick = { showMenu = true })
            ) {
                Column(modifier = Modifier.padding(12.dp)) {

                    // Show Sender Name in Group Chats
                    if (isGroup && !isMine) {
                        Text(
                            text = sender?.name ?: "Unknown",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (!message.mediaUrl.isNullOrEmpty()) {
                        when (message.mediaType) {
                            "audio" -> AudioPlayer(audioUrl = message.mediaUrl)
                            "video" -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl)).apply {
                                                    setDataAndType(Uri.parse(message.mediaUrl), "video/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No app found to play video", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                ) {
                                    coil.compose.AsyncImage(
                                        model = message.mediaUrl,
                                        contentDescription = "Video Thumbnail",
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Icon(
                                        Icons.Filled.PlayCircle,
                                        contentDescription = "Play Video",
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            "document" -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl)).apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = if (isMine) Color.White else MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(message.mediaFileName ?: "Document", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isMine) Color.White else Color.Black)
                                }
                            }
                            else -> {
                                coil.compose.AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Imagem enviada",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 250.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl)).apply {
                                                    setDataAndType(Uri.parse(message.mediaUrl), "image/*")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        if (!message.text.isNullOrEmpty()) Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!message.text.isNullOrEmpty()) {
                        val annotatedText = getMessageAnnotatedString(text = message.text, query = searchQuery)
                        androidx.compose.foundation.text.ClickableText(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyLarge.copy(color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant),
                            onClick = { offset ->
                                annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
                                    uriHandler.openUri(annotation.item)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isPinned) {
                            Icon(Icons.Filled.PushPin, "Pinned", modifier = Modifier.size(14.dp), tint = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(timeString, style = MaterialTheme.typography.labelSmall, color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val statusColor = if (message.status == MessageStatus.READ.name) Color(0xFF34B7F1) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            val StatusIcon = when (message.status) {
                                MessageStatus.SENDING.name -> Icons.Filled.Schedule
                                MessageStatus.SENT.name -> Icons.Filled.Done
                                MessageStatus.DELIVERED.name -> Icons.Filled.DoneAll
                                MessageStatus.READ.name -> Icons.Filled.DoneAll
                                else -> Icons.Filled.Schedule
                            }
                            Icon(StatusIcon, "Status: ${message.status}", modifier = Modifier.size(16.dp), tint = statusColor)
                        }
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (message.isPinned) "Desfixar" else "Fixar no topo") },
                    onClick = { onTogglePin(!message.isPinned); showMenu = false },
                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) }
                )
            }
        }
    }
}
@Composable
fun DateHeader(date: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(date, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onCameraClicked: () -> Unit = {},
    onLocationClicked: () -> Unit = {},
    onGalleryClicked: () -> Unit = {},
    onFileClicked: () -> Unit = {},
    onMicClicked: () -> Unit = {},
    isRecording: Boolean = false,
    hasPendingAttachment: Boolean = false
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp).windowInsetsPadding(WindowInsets.ime),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGalleryClicked) { Icon(Icons.Filled.Image, "Galeria", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onFileClicked) { Icon(Icons.Filled.AttachFile, "Anexar Arquivo", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onCameraClicked) { Icon(Icons.Filled.PhotoCamera, "Tirar foto", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onLocationClicked) { Icon(Icons.Filled.LocationOn, "Enviar Localização", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isRecording) "Gravando áudio..." else "Type a message...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = !isRecording
            )
            Spacer(modifier = Modifier.width(8.dp))

            val isTextBlank = text.isBlank() && !hasPendingAttachment

            IconButton(
                onClick = if (isTextBlank) onMicClicked else onSendClicked,
                modifier = Modifier.background(color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary, shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isTextBlank) Icons.Filled.Mic else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isTextBlank) "Gravar Áudio" else "Enviar",
                    tint = Color.White
                )
            }
        }
    }
}

// Utility to get safe MIME Type mapping
fun getMediaType(context: Context, uri: Uri): String {
    val mimeType = context.contentResolver.getType(uri) ?: ""
    return when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "audio"
        else -> "document"
    }
}

// Utility to extract filename from Uri
@SuppressLint("Range")
fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        }
    }
    return result ?: uri.path?.substringAfterLast('/') ?: "unknown_file"
}

// Your existing utility functions below
@SuppressLint("MissingPermission")
fun fetchLocationAndSend(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: ChatRoomViewModel
) {
    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            val mapsLink = "📍 Minha localização: https://maps.google.com/?q=$${location.latitude},${location.longitude}"
            viewModel.sendMessage(mapsLink)
        } else {
            Toast.makeText(context, "Não foi possível obter a localização. Tente abrir o Google Maps primeiro.", Toast.LENGTH_LONG).show()
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Erro ao buscar localização: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

fun createTempPictureUri(context: android.content.Context): android.net.Uri {
    val directory = java.io.File(context.cacheDir, "images").apply { mkdirs() }
    val tempFile = java.io.File.createTempFile("JPEG_", ".jpg", directory)
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}

class AudioRecorder(private val context: android.content.Context) {
    private var recorder: MediaRecorder? = null

    fun startRecording(outputFile: File) {
        recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording() {
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}

@Composable
fun AudioPlayer(audioUrl: String) {
    val context = LocalContext.current
    val mediaPlayer = remember { android.media.MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            try {
                if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    if (!isPrepared) {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(audioUrl)
                        mediaPlayer.prepareAsync()
                        mediaPlayer.setOnPreparedListener {
                            isPrepared = true
                            it.start()
                            isPlaying = true
                        }
                    } else {
                        mediaPlayer.start()
                        isPlaying = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao tocar áudio", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
        }

        Text(
            text = "Mensagem de voz",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        mediaPlayer.setOnCompletionListener {
            isPlaying = false
        }
    }
}