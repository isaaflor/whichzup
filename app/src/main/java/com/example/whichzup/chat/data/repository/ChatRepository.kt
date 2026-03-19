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
    private val cryptoManager: CryptoManager = CryptoManager() // <-- INJETADO AQUI
) {
    private val chatsCollection = firestore.collection("chats")

    suspend fun createChat(chat: Chat): Result<String> {
        return try {
            val docRef = if (chat.id.isEmpty()) chatsCollection.document() else chatsCollection.document(chat.id)
            val chatToSave = chat.copy(id = docRef.id)

            // Optimistic save to Room
            chatDao.insertChat(chatToSave.toEntity())

            // Network save
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
                    // <-- DESCRIPTOGRAFANDO A LAST MESSAGE
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
                        chatDao.insertChats(chats.map { chat -> chat.toEntity() })
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

            // <-- CRIPTOGRAFANDO O TEXTO AQUI ANTES DE SALVAR
            val encryptedText = message.text?.let { cryptoManager.encrypt(it) }

            val localMessage = message.copy(
                id = newMessageRef.id,
                text = encryptedText, // Usa o texto criptografado
                status = MessageStatus.SENDING.name
            )
            messageDao.insertMessage(localMessage.toEntity(chatId))

            // 1. Optimistic Local Save (UI updates instantly)
            val networkMessage = localMessage.copy(status = MessageStatus.SENT.name)

            // 2. Network Sync
            firestore.runBatch { batch ->
                batch.set(newMessageRef, networkMessage)
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessageText" to (encryptedText ?: "Sent an attachment"),
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
                    // <-- DESCRIPTOGRAFANDO PARA A UI MOSTRAR CORRETAMENTE
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
                        messageDao.insertMessages(messages.map { msg -> msg.toEntity(chatId) })
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