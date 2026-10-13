package com.example.messenger.security

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

data class WebSocketIdentity(val userId: String, val token: String)

@Service
class WebSocketTicketService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val jwtUtil: JwtUtil,
    private val tokenBlacklistService: TokenBlacklistService
) {
    // генератор
    private val random = SecureRandom()

    // не болкирует поток, выдает билет по id  и токену
    suspend fun create(userId: String, token: String): String {
        // проверяем что токен валиден и прнадлежит пользователю
        require(jwtUtil.validateAccessToken(token) && jwtUtil.getUserIdFromToken(token) == userId) {
            "Access token does not match authenticated user"
        }
        // смотрим, что токен не отозван
        check(!tokenBlacklistService.isBlacklisted(token).awaitSingle()) {
            "Access token has been revoked"
        }
        // заполняем массив из 32 байт случайцно
        val bytes = ByteArray(TICKET_BYTES).also(random::nextBytes)
        // превращяем байты с строку
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        // кладем с редис
        redisTemplate.opsForValue()
            .set(key(ticket), "$userId|$token", TICKET_TTL)
            .awaitSingle()
        return ticket
    }

    suspend fun consume(ticket: String): WebSocketIdentity? {
        // прочитать и удалить, если билета нет, то null
        val identity = redisTemplate.opsForValue().getAndDelete(key(ticket)).awaitSingleOrNull()
            ?: return null
        // получаем пользователя и токен
        val (userId, token) = identity.split('|', limit = 2).takeIf { it.size == 2 }
            ?: return null
        // проверяем живой ли токен
        if (!jwtUtil.validateAccessToken(token) || jwtUtil.getUserIdFromToken(token) != userId) return null
        // и не отозван ли
        if (tokenBlacklistService.isBlacklisted(token).awaitSingle()) return null
        return WebSocketIdentity(userId, token)
    }

    // проверям может ли соединение еще работать
    suspend fun isUsable(identity: WebSocketIdentity): Boolean =
        jwtUtil.validateAccessToken(identity.token) &&
            jwtUtil.getUserIdFromToken(identity.token) == identity.userId &&
            !tokenBlacklistService.isBlacklisted(identity.token).awaitSingle()

    // ключ в Redis для билета
    private fun key(ticket: String) = "ws:ticket:$ticket"

    private companion object {
        const val TICKET_BYTES = 32
        val TICKET_TTL: Duration = Duration.ofSeconds(30)
    }
}
