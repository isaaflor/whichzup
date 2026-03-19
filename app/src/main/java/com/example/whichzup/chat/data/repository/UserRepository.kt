// com/example/whichzup/chat/data/repository/UserRepository.kt
package com.example.whichzup.chat.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.example.whichzup.chat.domain.model.AuthState
import com.example.whichzup.chat.domain.model.User
import com.example.whichzup.chat.domain.model.UserStatus
import com.example.whichzup.chat.data.local.dao.UserDao
import com.example.whichzup.chat.data.local.entity.toDomain
import com.example.whichzup.chat.data.local.entity.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class UserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val context: Context
) {
    private val usersCollection = firestore.collection("users")

    val authState: Flow<AuthState> = callbackFlow {
        trySend(AuthState.Loading)

        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                usersCollection.document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        val user = document.toObject(User::class.java)
                        if (user != null) {
                            trySend(AuthState.Authenticated(user))
                        } else {
                            val newUser = User(
                                id = currentUser.uid,
                                email = currentUser.email ?: "",
                                name = currentUser.displayName ?: ""
                            )
                            trySend(AuthState.Authenticated(newUser))
                        }
                    }
                    .addOnFailureListener { e ->
                        trySend(AuthState.Error(e.message ?: "Failed to fetch user data"))
                    }
            } else {
                trySend(AuthState.Unauthenticated)
            }
        }

        auth.addAuthStateListener(authStateListener)

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    suspend fun signUpWithEmailPassword(email: String, password: String, name: String): Result<Unit> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                val user = User(
                    id = firebaseUser.uid,
                    name = name,
                    email = email,
                    status = UserStatus.ONLINE,
                    onlineStatus = true
                )
                saveUserToBothDatabases(user)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to create user"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            updateUserStatus(auth.currentUser?.uid, UserStatus.ONLINE)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user

            if (firebaseUser != null) {
                val doc = usersCollection.document(firebaseUser.uid).get().await()
                if (!doc.exists()) {
                    val user = User(
                        id = firebaseUser.uid,
                        name = firebaseUser.displayName ?: "Unknown",
                        email = firebaseUser.email ?: "",
                        profilePictureUrl = firebaseUser.photoUrl?.toString() ?: "",
                        status = UserStatus.ONLINE,
                        onlineStatus = true
                    )
                    saveUserToBothDatabases(user)
                } else {
                    updateUserStatus(firebaseUser.uid, UserStatus.ONLINE)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Google Sign-In failed"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        updateUserStatus(auth.currentUser?.uid, UserStatus.OFFLINE)
        auth.signOut()
    }

    private suspend fun saveUserToBothDatabases(user: User) {
        usersCollection.document(user.id).set(user).await()
        userDao.insertUser(user.toEntity())
    }

    fun getUserProfile(userId: String): Flow<User?> {
        return userDao.getUser(userId).map { it?.toDomain() }
    }

    suspend fun fetchProfileFromNetwork(userId: String): Result<Unit> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            val user = snapshot.toObject(User::class.java)
            if (user != null) {
                userDao.insertUser(user.toEntity())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun updateProfileInfo(userId: String, name: String, bio: String, profilePictureUrl: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "name" to name,
                "bio" to bio,
                "profilePictureUrl" to profilePictureUrl
            )
            usersCollection.document(userId).update(updates).await()
            userDao.updateProfileInfo(userId, name, profilePictureUrl, bio)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun updateUserStatus(userId: String?, status: UserStatus): Result<Unit> {
        if (userId == null) return Result.failure(Exception("User ID is null"))
        return try {
            val isOnline = status == UserStatus.ONLINE
            val updates = mapOf(
                "status" to status.name,
                "onlineStatus" to isOnline
            )
            usersCollection.document(userId).update(updates).await()
            userDao.updateStatus(userId, status.name, isOnline)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    fun getAllContacts(): Flow<List<User>> {
        val currentUserId = auth.currentUser?.uid ?: ""
        return userDao.getAllContacts(currentUserId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun searchUsers(query: String): Flow<List<User>> {
        val currentUserId = auth.currentUser?.uid ?: ""
        return userDao.searchUsersLocal(query, currentUserId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun syncDeviceContacts(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUserId = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
            val emails = mutableSetOf<String>()
            val projection = arrayOf(ContactsContract.CommonDataKinds.Email.DATA)
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)
                while (it.moveToNext()) {
                    val email = it.getString(emailIndex)
                    if (email.isNotBlank()) emails.add(email)
                }
            }

            if (emails.isEmpty()) {
                Result.success(Unit)
            } else {
                val matchedUsers = mutableListOf<User>()
                val chunks = emails.chunked(10) // Firestore aceita no max 10 no whereIn

                for (chunk in chunks) {
                    val snapshot = usersCollection.whereIn("email", chunk).get().await()
                    matchedUsers.addAll(snapshot.documents.mapNotNull { it.toObject(User::class.java) })
                }

                // Filtra para não adicionar você mesmo na lista de contatos do Room
                val usersToSave = matchedUsers.filter { it.id != currentUserId }

                if (usersToSave.isNotEmpty()) {
                    userDao.insertUsers(usersToSave.map { it.toEntity() })
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    // NOVO: Adiciona um contato pelo e-mail manual
    suspend fun addContactByEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUserId = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))

            // Busca o usuário exato pelo email no Firestore
            val snapshot = usersCollection.whereEqualTo("email", email).get().await()
            val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)

            if (user != null && user.id != currentUserId) {
                userDao.insertUser(user.toEntity()) // Salva no Room para aparecer na lista
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not found or is current user"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}