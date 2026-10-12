package com.example.messenger.websocket

import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

data class ManagedWebSocketSession(
    val session: WebSocketSession,
    val outgoing: Sinks.Many<String>
)

@Component
class WebSocketSessionManager {
    private val sessions = ConcurrentHashMap<String, MutableSet<ManagedWebSocketSession>>()

    fun addSession(userId: String, session: WebSocketSession): ManagedWebSocketSession {
        val managed = ManagedWebSocketSession(session, Sinks.many().unicast().onBackpressureBuffer())
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(managed)
        return managed
    }

    fun removeSession(userId: String, managed: ManagedWebSocketSession) {
        sessions.computeIfPresent(userId) { _, current ->
            current.remove(managed)
            if (current.isEmpty()) null else current
        }
        synchronized(managed.outgoing) { managed.outgoing.tryEmitComplete() }
    }

    fun sendToUser(userId: String, payload: String): Boolean {
        val connections = sessions[userId].orEmpty()
        var delivered = false
        connections.forEach { connection ->
            synchronized(connection.outgoing) {
                if (connection.outgoing.tryEmitNext(payload).isSuccess) delivered = true
            }
        }
        return delivered
    }

    fun closeSessions(userId: String): Mono<Void> {
        val connections = sessions.remove(userId)?.toList().orEmpty()
        connections.forEach { connection ->
            synchronized(connection.outgoing) { connection.outgoing.tryEmitComplete() }
        }
        if (connections.isEmpty()) return Mono.empty()
        return Mono.whenDelayError(connections.map { it.session.close() })
    }
}
