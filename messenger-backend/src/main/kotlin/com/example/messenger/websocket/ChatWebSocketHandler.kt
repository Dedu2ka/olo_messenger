package com.example.messenger.websocket

import com.example.messenger.security.WebSocketIdentity
import com.example.messenger.security.WebSocketTicketService
import com.example.messenger.service.MessageDeliveryService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.util.UUID

data class WsIncomingMessage(
    val type: String = "CHAT_MESSAGE",
    val recipientId: String,
    val content: String,
    val sentAt: String? = null,
    val clientMessageId: UUID? = null
)

data class WsMessageStatusEvent(
    val id: String,
    val chatId: String,
    val status: String,
    val type: String = "MESSAGE_STATUS"
)

private data class WsErrorMessage(
    val type: String = "ERROR",
    val message: String
)

@Component
class ChatWebSocketHandler(
    private val sessionManager: WebSocketSessionManager,
    private val messageDeliveryService: MessageDeliveryService,
    private val webSocketTicketService: WebSocketTicketService
) : WebSocketHandler {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val ticket = UriComponentsBuilder.fromUri(session.handshakeInfo.uri)
            .build()
            .queryParams
            .getFirst("ticket")
            .orEmpty()
        if (ticket.isBlank()) return session.close()

        return mono {
            webSocketTicketService.consume(ticket)
                ?: throw IllegalArgumentException("Invalid or expired WebSocket ticket")
        }
            .flatMap { identity -> handleAuthenticatedSession(session, identity) }
            .onErrorResume { session.close() }
    }

    private fun handleAuthenticatedSession(session: WebSocketSession, identity: WebSocketIdentity): Mono<Void> {
        val userId = identity.userId
        val authenticatedId = runCatching { UUID.fromString(userId) }.getOrNull()
            ?: return session.close()
        val managed = sessionManager.addSession(userId, session)

        return mono { webSocketTicketService.isUsable(identity) }
            .flatMap { usable ->
                if (!usable) {
                    sessionManager.removeSession(userId, managed)
                    session.close()
                } else runActiveSession(session, userId, authenticatedId, managed)
            }
    }

    private fun runActiveSession(
        session: WebSocketSession,
        userId: String,
        authenticatedId: UUID,
        managed: ManagedWebSocketSession
    ): Mono<Void> {
        val outbound = session.send(
            managed.outgoing.asFlux().map(session::textMessage)
        )
        val inbound = session.receive()
            .concatMap { frame -> processIncoming(frame, authenticatedId, userId) }
            .doFinally { sessionManager.removeSession(userId, managed) }
            .then()

        return Mono.`when`(inbound, outbound).then()
    }

    private fun processIncoming(
        frame: WebSocketMessage,
        senderId: UUID,
        senderIdString: String
    ): Mono<Void> = mono {
        try {
            val incoming = objectMapper.readValue(frame.payloadAsText, WsIncomingMessage::class.java)
            require(incoming.type == "CHAT_MESSAGE") { "Unsupported message type" }

            val recipientId = UUID.fromString(incoming.recipientId)
            messageDeliveryService.sendDirect(
                senderId = senderId,
                recipientId = recipientId,
                content = incoming.content,
                sentAt = incoming.sentAt?.let { value ->
                    try {
                        java.time.OffsetDateTime.parse(value)
                    } catch (exception: java.time.format.DateTimeParseException) {
                        throw IllegalArgumentException("sentAt must be an ISO-8601 timestamp", exception)
                    }
                },
                clientMessageId = incoming.clientMessageId
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.warn("Unable to process incoming WebSocket message from {}", senderIdString, exception)
            sessionManager.sendToUser(
                senderIdString,
                objectMapper.writeValueAsString(WsErrorMessage(message = "Message could not be processed"))
            )
        }
    }.then()

}
