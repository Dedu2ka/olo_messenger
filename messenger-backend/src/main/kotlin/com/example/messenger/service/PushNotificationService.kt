package com.example.messenger.service

import com.example.messenger.domain.PushTokenRepository
import com.example.messenger.domain.UserRepository
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.io.File
import java.util.UUID

@Service
class PushNotificationService(
    private val pushTokenRepository: PushTokenRepository,
    private val userRepository: UserRepository,
    @Value("\${app.fcm.service-account-path:}") serviceAccountPath: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val firebaseMessaging: FirebaseMessaging? = initializeFirebase(serviceAccountPath)

    suspend fun notifyUser(userId: UUID, chatId: UUID, senderId: UUID, content: String) {
        val messaging = firebaseMessaging ?: return
        val sender = try {
            userRepository.findById(senderId)
        } catch (exception: Exception) {
            logger.warn("Unable to load sender details for push notification", exception)
            null
        }
        val senderName = (sender?.displayName?.takeIf { it.isNotBlank() } ?: sender?.username ?: "Новое сообщение")
            .take(MAX_SENDER_NAME_LENGTH)
        // FCM data payloads are limited in size; keep previews safely below that limit.
        val messagePreview = content.take(MAX_MESSAGE_PREVIEW_LENGTH).let {
            if (content.length > MAX_MESSAGE_PREVIEW_LENGTH) "$it…" else it
        }
        val tokens = try {
            pushTokenRepository.findTokensByUserId(userId).toList()
        } catch (exception: Exception) {
            logger.warn("Unable to load push tokens for user {}", userId, exception)
            return
        }

        tokens.forEach { token ->
            val message = Message.builder()
                .setToken(token)
                .putData("title", senderName)
                .putData("body", messagePreview)
                .putData("chatId", chatId.toString())
                .setAndroidConfig(
                    AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build()
                )
                .build()
            try {
                withContext(Dispatchers.IO) { messaging.send(message) }
            } catch (exception: Exception) {
                logger.warn("Unable to send push notification to token for user {}", userId, exception)
            }
        }
    }

    private fun initializeFirebase(path: String): FirebaseMessaging? {
        if (path.isBlank()) {
            logger.warn("FCM is disabled: set FCM_SERVICE_ACCOUNT_PATH to enable push notifications")
            return null
        }
        if (!File(path).isFile) {
            logger.warn("FCM is disabled: service-account file does not exist at {}", path)
            return null
        }
        return try {
            val app = FirebaseApp.getApps().firstOrNull { it.name == FIREBASE_APP_NAME }
                ?: FileInputStream(path).use { input ->
                    val options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(input))
                        .build()
                    FirebaseApp.initializeApp(options, FIREBASE_APP_NAME)
                }
            FirebaseMessaging.getInstance(app)
        } catch (exception: Exception) {
            logger.error("FCM could not be initialized; push notifications are disabled", exception)
            null
        }
    }

    private companion object {
        const val FIREBASE_APP_NAME = "olo-push"
        const val MAX_SENDER_NAME_LENGTH = 100
        const val MAX_MESSAGE_PREVIEW_LENGTH = 500
    }
}
