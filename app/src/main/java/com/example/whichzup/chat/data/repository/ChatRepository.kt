// com/example/whichzup/chat/data/repository/ChatRepository.kt
package com.example.whichzup.chat.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.whichzup.chat.domain.model.Chat
import com.example.whichzup.chat.domain.model.Message
import com.example.whichzup.chat.data.local.dao.ChatDao
import com.example.whichzup.chat.data.local.dao.MessageDao
import com.example.whichzup.chat.data.local.entity.toDomain
import com.example.whichzup.chat.data.local.entity.toEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val firestore: FirebaseFirestore,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
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
                send(entities.map { it.toDomain() })
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

    // --- UPDATED: Delete Chat Locally AND on Firestore ---
    suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            // 1. Delete locally so the UI updates instantly
            chatDao.deleteChatLocally(chatId)

            // 2. Delete from network so it doesn't sync back
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

            val messageToSave = message.copy(id = newMessageRef.id)

            // 1. Optimistic Local Save (UI updates instantly)
            messageDao.insertMessage(messageToSave.toEntity(chatId))

            // 2. Network Sync
            firestore.runBatch { batch ->
                batch.set(newMessageRef, messageToSave)
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessageText" to (message.text ?: "Sent an attachment"),
                        "lastMessageSenderId" to message.senderId,
                        "lastMessageTimestamp" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = channelFlow {
        // 1. Observe Room (SSOT)
        launch {
            messageDao.getMessagesForChat(chatId).collect { entities ->
                send(entities.map { it.toDomain() })
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
            // Update local first
            messageDao.updateMessageStatus(messageId, newStatus)

            // Sync with network
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
                mapOf(
                    "name" to name,
                    "groupImageUrl" to imageUrl
                )
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
}