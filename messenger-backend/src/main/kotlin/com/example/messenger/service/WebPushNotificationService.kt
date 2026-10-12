package com.example.messenger.service

import com.example.messenger.domain.UserRepository
import com.example.messenger.domain.WebPushSubscriptionRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.Security
import java.util.UUID

@Service
class WebPushNotificationService(
    private val subscriptions: WebPushSubscriptionRepository,
    private val users: UserRepository,
    @Value("\${app.web-push.public-key:}") publicKeyValue: String,
    @Value("\${app.web-push.private-key:}") privateKeyValue: String,
    @Value("\${app.web-push.subject:mailto:admin@olo-chat.duckdns.org}") subject: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()
    private val configuredPublicKey = publicKeyValue.takeIf(String::isNotBlank)
    private val pushService: PushService? = runCatching {
        if (publicKeyValue.isBlank() || privateKeyValue.isBlank()) return@runCatching null
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        PushService(publicKeyValue, privateKeyValue, subject)
    }.onFailure { logger.error("Web Push could not be initialized; web notifications are disabled", it) }
        .getOrNull()
    val publicKey: String? = configuredPublicKey.takeIf { pushService != null }

    suspend fun notifyUser(userId: UUID, chatId: UUID, senderId: UUID, content: String) {
        val service = pushService ?: return
        val senderName = runCatching { users.findById(senderId)?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.username } }
            .getOrNull() ?: "Новое сообщение"
        val payload = mapper.writeValueAsString(
            mapOf(
                "title" to senderName.take(MAX_TITLE_LENGTH),
                "body" to content.take(MAX_BODY_LENGTH),
                "chatId" to chatId.toString()
            )
        )
        val targets = runCatching { subscriptions.findAllByUserId(userId).toList() }
            .onFailure { logger.warn("Unable to load Web Push subscriptions for user {}", userId, it) }
            .getOrDefault(emptyList())

        for (subscription in targets) {
            try {
                val notification = Notification(
                    subscription.endpoint,
                    subscription.p256dhKey,
                    subscription.authSecret,
                    payload.toByteArray(Charsets.UTF_8),
                    TTL_SECONDS
                )
                val response = withContext(Dispatchers.IO) { service.send(notification) }
                val status = response.statusLine.statusCode
                if (status == 404 || status == 410) {
                    subscriptions.deleteForUser(subscription.endpoint, userId)
                } else if (status !in 200..299) {
                    logger.warn("Web Push endpoint returned HTTP {} for user {}", status, userId)
                }
            } catch (exception: Exception) {
                logger.warn("Unable to send Web Push notification to user {}", userId, exception)
            }
        }
    }

    private companion object {
        const val TTL_SECONDS = 60 * 60
        const val MAX_TITLE_LENGTH = 100
        const val MAX_BODY_LENGTH = 300
    }
}
