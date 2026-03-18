package com.example.whichzup.chat.ui.chatroom

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.whichzup.chat.domain.model.Message
import com.example.whichzup.chat.domain.model.MessageStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import android.media.MediaRecorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chatName: String,
    viewModel: ChatRoomViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGroupSettings: (chatId: String) -> Unit
) {
    val groupedMessages by viewModel.groupedMessages.collectAsStateWithLifecycle()
    val currentChat by viewModel.currentChat.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val context = LocalContext.current

    // Inicializa o cliente de localização do Google Play Services
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }


    // Cria o lançador para pedir as permissões de localização em tempo de execução
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (isGranted) {
            // Se o utilizador permitiu, vamos buscar a localização!
            fetchLocationAndSend(context, fusedLocationClient, viewModel)
        } else {
            Toast.makeText(context, "Permissão de localização negada.", Toast.LENGTH_SHORT).show()
        }
    }

    // Estado para guardar a URI da foto que está sendo tirada
    var tempImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Lançador que abre a câmera e espera o resultado (sucesso ou não)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                viewModel.uploadMediaAndSendMessage(uri)
                Toast.makeText(context, "Imagem enviada!", Toast.LENGTH_SHORT).show()

            }
        } else {
            Toast.makeText(context, "Captura cancelada.", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                // 3.1 Cria o arquivo temporário e pega a URI
                val uri = createTempPictureUri(context)
                // 3.2 Salva no estado
                tempImageUri = uri
                // 3.3 Abre a câmera de fato
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                // Se der erro (ex: problema no FileProvider), não fecha o app
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permissão negada.", Toast.LENGTH_SHORT).show()
        }
    }

    val audioRecorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Inicia a gravação
            val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp3")
            audioFile = file
            audioRecorder.startRecording(file)
            isRecording = true
            Toast.makeText(context, "Gravando...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissão de áudio negada.", Toast.LENGTH_SHORT).show()
        }
    }



    val totalMessages = groupedMessages.values.sumOf { it.size }
    LaunchedEffect(totalMessages) {
        if (totalMessages > 0 && !isSearching) {
            kotlinx.coroutines.delay(100)
            listState.scrollToItem(0)
        }
    }

    val pinnedMessage = remember(groupedMessages) {
        groupedMessages.values.flatten().findLast { it.isPinned }
    }

    // --- LÓGICA DE NAVEGAÇÃO DA BUSCA ---

    // 1. Simula a ordem exata da LazyColumn (mensagens mais novas no índice 0)
    val flatList = remember(groupedMessages) {
        val list = mutableListOf<Any>()
        groupedMessages.entries.toList().reversed().forEach { (date, messages) ->
            list.addAll(messages.reversed())
            list.add(date) // O DateHeader também conta como um item na lista visual
        }
        list
    }

    // 2. Encontra todos os índices das mensagens que contêm o texto buscado
    val matchedIndices = remember(flatList, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            flatList.mapIndexedNotNull { index, item ->
                if (item is Message && item.text?.contains(searchQuery, ignoreCase = true) == true) {
                    index
                } else null
            }
        }
    }

    // 3. Controla em qual resultado estamos focados no momento
    var currentMatchIndex by remember { mutableStateOf(0) }

    // Reseta o foco para a primeira mensagem encontrada se o texto mudar
    LaunchedEffect(matchedIndices) {
        if (matchedIndices.isNotEmpty()) {
            currentMatchIndex = 0
        }
    }

    // Função auxiliar para rolar a tela
    fun scrollToCurrentMatch() {
        if (matchedIndices.isNotEmpty() && currentMatchIndex in matchedIndices.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(matchedIndices[currentMatchIndex])
            }
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
                            placeholder = { Text("Buscar...", maxLines = 1) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    scrollToCurrentMatch()
                                    keyboardController?.hide()
                                }
                            ),
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
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar busca")
                        }
                    },
                    actions = {
                        // Mostra as setas e o contador apenas se houver resultados
                        if (matchedIndices.isNotEmpty()) {
                            Text(
                                text = "${currentMatchIndex + 1}/${matchedIndices.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Seta para cima (Procura em mensagens mais antigas)
                            IconButton(
                                onClick = {
                                    if (currentMatchIndex < matchedIndices.size - 1) {
                                        currentMatchIndex++
                                        scrollToCurrentMatch()
                                    }
                                },
                                enabled = currentMatchIndex < matchedIndices.size - 1
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Mais antiga")
                            }

                            // Seta para baixo (Procura em mensagens mais recentes)
                            IconButton(
                                onClick = {
                                    if (currentMatchIndex > 0) {
                                        currentMatchIndex--
                                        scrollToCurrentMatch()
                                    }
                                },
                                enabled = currentMatchIndex > 0
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Mais recente")
                            }
                        }

                        // Botão de limpar texto
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Limpar texto")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(chatName) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search messages")
                        }
                        if (currentChat?.isGroup == true) {
                            IconButton(onClick = { currentChat?.id?.let { onNavigateToGroupSettings(it) } }) {
                                Icon(Icons.Filled.Info, contentDescription = "Group Settings")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isSearching) {
                ChatInputBar(
                    text = inputText,
                    onTextChanged = { inputText = it },
                    onSendClicked = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    onLocationClicked = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onCameraClicked = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onMicClicked = {
                        if (!isRecording) {
                            // Pede permissão e começa a gravar
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            // Para a gravação e envia
                            try {
                                audioRecorder.stopRecording()
                                isRecording = false
                                audioFile?.let { file ->
                                    // Enviamos a URI do arquivo para o ViewModel
                                    val uri = android.net.Uri.fromFile(file)
                                    viewModel.uploadMediaAndSendMessage(uri, isAudio = true) // Avisa que é áudio
                                }
                            } catch (e: Exception) {
                                isRecording = false
                                Toast.makeText(context, "Erro ao gravar áudio.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    isRecording = isRecording
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (pinnedMessage != null && !isSearching) {
                PinnedMessageBanner(
                    message = pinnedMessage,
                    onUnpin = { viewModel.togglePinMessage(pinnedMessage.id, false) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                reverseLayout = true
            ) {
                groupedMessages.entries.toList().reversed().forEach { (date, messages) ->
                    items(messages.reversed(), key = { it.id }) { message ->
                        val isMine = message.senderId == viewModel.currentUserId
                        MessageBubble(
                            message = message,
                            isMine = isMine,
                            searchQuery = searchQuery,
                            onTogglePin = { isPinned ->
                                viewModel.togglePinMessage(message.id, isPinned)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        DateHeader(date)
                    }
                }
            }
        }
    }
}

@Composable
fun getMessageAnnotatedString(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(text)

        // 1. Destaque da Busca (Amarelo)
        if (query.isNotBlank()) {
            var startIndex = 0
            while (startIndex < text.length) {
                val index = text.indexOf(query, startIndex, ignoreCase = true)
                if (index == -1) break
                addStyle(
                    style = SpanStyle(background = Color.Yellow, color = Color.Black),
                    start = index,
                    end = index + query.length
                )
                startIndex = index + query.length
            }
        }

        // 2. Identificador de Links
        val urlPattern = android.util.Patterns.WEB_URL.toRegex()
        val matches = urlPattern.findAll(text)
        for (match in matches) {
            var url = match.value
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }

            // Deixa o texto do link com cor diferente e sublinhado
            addStyle(
                style = SpanStyle(
                    color = Color(0xFF90CAF9), // Um azul claro amigável
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
            // Adiciona a "etiqueta" invisível que diz para o Compose que isso é um link
            addStringAnnotation(
                tag = "URL",
                annotation = url,
                start = match.range.first,
                end = match.range.last + 1
            )
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
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mensagem Fixada",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.text ?: "Mídia",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Desfixar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    searchQuery: String = "",
    onTogglePin: (Boolean) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = message.timestamp?.let { timeFormat.format(it) } ?: "Sending..."
    var showMenu by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "U",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 0.dp,
                    bottomEnd = if (isMine) 0.dp else 16.dp
                ),
                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 1. Renderiza a Imagem se existir mediaUrl
                    if (!message.mediaUrl.isNullOrEmpty()) {
                        val isAudio = message.mediaUrl.endsWith(".mp3") || message.mediaUrl.contains("audio_")

                        if (isAudio) {
                            // Se for áudio, mostra o Player que criamos acima
                            AudioPlayer(audioUrl = message.mediaUrl)
                        } else {
                            // Se não for áudio, assume que é imagem (como já estava)
                            coil.compose.AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Imagem enviada",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                                    .padding(bottom = if (message.text.isNullOrEmpty()) 0.dp else 8.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }

                    // 2. Renderiza o Texto (ou Link de GPS) se existir
                    if (!message.text.isNullOrEmpty()) {
                        val annotatedText = getMessageAnnotatedString(text = message.text, query = searchQuery)

                        androidx.compose.foundation.text.ClickableText(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = { offset ->
                                annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                    }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )

                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))

                            val statusColor = if (message.status == MessageStatus.READ.name) {
                                Color(0xFF34B7F1)
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            }

                            val StatusIcon = when (message.status) {
                                MessageStatus.SENDING.name -> Icons.Filled.Schedule
                                MessageStatus.SENT.name -> Icons.Filled.Done
                                MessageStatus.DELIVERED.name -> Icons.Filled.DoneAll
                                MessageStatus.READ.name -> Icons.Filled.DoneAll
                                else -> Icons.Filled.Schedule
                            }

                            Icon(
                                imageVector = StatusIcon,
                                contentDescription = "Status da mensagem: ${message.status}",
                                modifier = Modifier.size(16.dp),
                                tint = statusColor
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (message.isPinned) "Desfixar" else "Fixar no topo") },
                    onClick = {
                        onTogglePin(!message.isPinned)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.PushPin, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = date,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onCameraClicked: () -> Unit= {},
    onLocationClicked: () -> Unit = {},
    onMicClicked: () -> Unit = {},
    isRecording: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .windowInsetsPadding(WindowInsets.ime),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCameraClicked){
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.PhotoCamera,
                    contentDescription = "Tirar foto",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant

                )
            }

            IconButton(onClick = onLocationClicked) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.LocationOn,
                    contentDescription = "Enviar Localização",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                // Feedback visual: Muda o placeholder se estiver gravando
                placeholder = { Text(if (isRecording) "Gravando áudio..." else "Type a message...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = !isRecording // Desabilita digitação enquanto grava
            )
            Spacer(modifier = Modifier.width(8.dp))

            val isTextBlank = text.isBlank()

            IconButton(
                onClick = if(isTextBlank) onMicClicked else onSendClicked,
                modifier = Modifier
                    .background(
                        color = if(isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if(isTextBlank) androidx.compose.material.icons.Icons.Filled.Mic else androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send,
                    contentDescription = if(isTextBlank) "Gravar Áudio" else "Enviar",
                    tint = Color.White
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun fetchLocationAndSend(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: ChatRoomViewModel
) {
    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            // Monta um link do Google Maps com a latitude e longitude
            val mapsLink = "📍 Minha localização: https://maps.google.com/?q=${location.latitude},${location.longitude}"
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

    // Limpa o player quando o item sai da tela
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                try {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(context, android.net.Uri.parse(audioUrl))
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    isPlaying = true

                    // Quando acabar o áudio, volta o ícone para "Play"
                    mediaPlayer.setOnCompletionListener { isPlaying = false }
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao reproduzir áudio", Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Ouvir",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Visual simples de "onda de áudio" ou apenas um texto
        Text(
            text = "Mensagem de voz",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}