// com/example/whichzup/chat/data/repository/ChatRepository.kt
package com.example.whichzup.chat.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.whichzup.chat.domain.model.Chat
import com.example.whichzup.chat.domain.model.Message
import com.example.whichzup.chat.domain.model.MessageStatus
import com.example.whichzup.chat.data.local.dao.ChatDao
import com.example.whichzup.chat.data.local.dao.MessageDao
import com.example.whichzup.chat.data.local.entity.toDomain
import com.example.whichzup.chat.data.local.entity.toEntity
import com.example.whichzup.chat.utils.CryptoManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val firestore: FirebaseFirestore,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val cryptoManager: CryptoManager = CryptoManager() // Injetado aqui
) {
    private val chatsCollection = firestore.collection("chats")

    suspend fun createChat(chat: Chat): Result<String> {
        return try {
            val docRef = if (chat.id.isEmpty()) chatsCollection.document() else chatsCollection.document(chat.id)
            val chatToSave = chat.copy(id = docRef.id)

            // Criptografa a lastMessageText se houver antes de salvar localmente
            val encryptedLastMsg = if (chatToSave.lastMessageText.isNotEmpty()) {
                cryptoManager.encrypt(chatToSave.lastMessageText)
            } else {
                chatToSave.lastMessageText
            }

            // Optimistic save to Room (Criptografado)
            chatDao.insertChat(chatToSave.copy(lastMessageText = encryptedLastMsg).toEntity())

            // Network save (Texto plano)
            docRef.set(chatToSave).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserChats(userId: String): Flow<List<Chat>> = channelFlow {
        // 1. Observe Room (SSOT) and emit to the UI
        launch {
            chatDao.getUserChats(userId).collect { entities ->
                val decryptedChats = entities.map {
                    // Descriptografa a last message para mostrar na tela de chats
                    it.toDomain().copy(lastMessageText = cryptoManager.decrypt(it.lastMessageText ?: ""))
                }
                send(decryptedChats)
            }
        }

        // 2. Listen to Firestore and update Room
        val listenerRegistration = chatsCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                snapshot?.let {
                    val chats = it.documents.mapNotNull { doc -> doc.toObject(Chat::class.java) }
                    launch {
                        // Criptografa as atualizações da nuvem ANTES de salvar no Room
                        val chatsToSave = chats.map { chat ->
                            val encryptedLastMsg = if (chat.lastMessageText.isNotEmpty()) {
                                cryptoManager.encrypt(chat.lastMessageText)
                            } else {
                                chat.lastMessageText
                            }
                            chat.copy(lastMessageText = encryptedLastMsg).toEntity()
                        }
                        chatDao.insertChats(chatsToSave)
                    }
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            chatDao.deleteChatLocally(chatId)
            chatsCollection.document(chatId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(chatId: String, message: Message): Result<Unit> {
        return try {
            val chatRef = chatsCollection.document(chatId)
            val messagesRef = chatRef.collection("messages")
            val newMessageRef = messagesRef.document()

            // 1. Criptografa APENAS para o Room (Local)
            val encryptedText = message.text?.let { cryptoManager.encrypt(it) }

            val localMessage = message.copy(
                id = newMessageRef.id,
                text = encryptedText, // Usa o texto criptografado no Room
                status = MessageStatus.SENDING.name
            )
            messageDao.insertMessage(localMessage.toEntity(chatId))

            // 2. Network Sync (Texto Limpo para o Firestore)
            val networkMessage = message.copy(
                id = newMessageRef.id,
                status = MessageStatus.SENT.name // Usa a mensagem original (texto limpo)
            )

            firestore.runBatch { batch ->
                batch.set(newMessageRef, networkMessage) // Salva limpo na coleção de mensagens
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessageText" to (message.text ?: "Sent an attachment"), // Salva limpo no lastMessage
                        "lastMessageSenderId" to message.senderId,
                        "lastMessageTimestamp" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            messageDao.updateMessageStatus(newMessageRef.id, MessageStatus.SENT.name)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = channelFlow {
        // 1. Observe Room (SSOT)
        launch {
            messageDao.getMessagesForChat(chatId).collect { entities ->
                val decryptedMessages = entities.map {
                    // Descriptografa para a UI mostrar corretamente dentro do chat
                    it.toDomain().copy(text = it.text?.let { txt -> cryptoManager.decrypt(txt) })
                }
                send(decryptedMessages)
            }
        }

        // 2. Listen to Firestore
        val listenerRegistration = chatsCollection.document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                snapshot?.let {
                    val messages = it.documents.mapNotNull { doc -> doc.toObject(Message::class.java) }
                    launch {
                        // Recebe limpo do Firestore, criptografa e salva no Room
                        val entitiesToSave = messages.map { msg ->
                            val encryptedTxt = msg.text?.let { cryptoManager.encrypt(it) }
                            msg.copy(text = encryptedTxt).toEntity(chatId)
                        }
                        messageDao.insertMessages(entitiesToSave)
                    }
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateMessageStatus(chatId: String, messageId: String, newStatus: String): Result<Unit> {
        return try {
            messageDao.updateMessageStatus(messageId, newStatus)
            chatsCollection.document(chatId)
                .collection("messages")
                .document(messageId)
                .update("status", newStatus)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroupDetails(chatId: String, name: String, imageUrl: String?): Result<Unit> {
        return try {
            chatsCollection.document(chatId).update(
                mapOf("name" to name, "groupImageUrl" to imageUrl)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addParticipants(chatId: String, newParticipantIds: List<String>): Result<Unit> {
        return try {
            chatsCollection.document(chatId).update(
                "participantIds", FieldValue.arrayUnion(*newParticipantIds.toTypedArray())
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markMessagesAsRead(chatId: String, unreadMessageIds: List<String>): Result<Unit> {
        if (unreadMessageIds.isEmpty()) return Result.success(Unit)
        return try {
            val messagesRef = chatsCollection.document(chatId).collection("messages")
            firestore.runBatch { batch ->
                unreadMessageIds.forEach { msgId ->
                    batch.update(messagesRef.document(msgId), "status", MessageStatus.READ.name)
                }
            }.await()
            unreadMessageIds.forEach { msgId ->
                messageDao.updateMessageStatus(msgId, MessageStatus.READ.name)
            }
            Result.success(Unit)
        } catch(e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeParticipant(chatId: String, participantIdToRemove: String): Result<Unit> {
        return try {
            chatsCollection.document(chatId).update(
                "participantIds", FieldValue.arrayRemove(participantIdToRemove)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleMessagePin(chatId: String, messageId: String, isPinned: Boolean): Result<Unit> {
        return try {
            messageDao.updateMessagePinnedStatus(messageId, isPinned)
            chatsCollection.document(chatId)
                .collection("messages")
                .document(messageId)
                .update("isPinned", isPinned)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}