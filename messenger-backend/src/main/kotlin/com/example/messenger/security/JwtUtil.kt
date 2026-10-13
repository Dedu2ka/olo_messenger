package com.example.messenger.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

@Component
class JwtUtil(
    // берем значение из yaml JWT_SECRET
    @Value("\${jwt.secret}")
    private val secret: String,

    // время жизни токена в милисекндах
    @Value("\${jwt.expiration-ms:900000}")
    private val expirationMs: Long,

    @Value("\${jwt.refresh-expiration-ms:2592000000}")
    private val refreshExpirationMs: Long
) {

    // делаем ключ преобразуя сроку в байты
    private val key = Keys.hmacShaKeyFor(
        secret.toByteArray(Charsets.UTF_8)
    )

    // создаем токен
    val accessTokenLifetimeSeconds: Long
        get() = expirationMs / 1000

    fun generateToken(
        userId: String,
        username: String
    ): String = generateToken(userId, username, ACCESS_TYPE, expirationMs)

    fun generateRefreshToken(userId: String, username: String): String =
        generateToken(userId, username, REFRESH_TYPE, refreshExpirationMs)

    private fun generateToken(userId: String, username: String, type: String, lifetimeMs: Long): String {

        val now = Date()
        // ммоент когда токен перестанет действовать
        val expiryDate = Date(
            now.time + lifetimeMs
        )

        return Jwts.builder()
            // владелец токена
            .setSubject(userId)
            // доп поле имени
            .claim("username", username)
            .claim("token_type", type)
            .setId(UUID.randomUUID().toString())
            // момент выдачи
            .setIssuedAt(now)
            // момент когда токен умрет
            .setExpiration(expiryDate)
            // подпись
            .signWith(key)
            // собираем все в 1 строку
            .compact()
    }

    // метод достает владельца токена
    fun getUserIdFromToken(token: String): String {
        return getClaims(token).subject ?: ""
    }

    // проверяем настоящий и ен просрочен ли токен
    fun validateToken(token: String): Boolean {
        // что вернет try то и вернет функция
        return try {
            // приверяем попись и срок, при подделке или просроке исключение
            val claims = getClaims(token)

            // доп проверка срока
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            // люая проблема, токен мусор
            false
        }
    }

    fun validateAccessToken(token: String): Boolean =
        validateToken(token) && runCatching {
            getClaims(token).get("token_type", String::class.java).let { it == null || it == ACCESS_TYPE }
        }.getOrDefault(false)

    fun validateRefreshToken(token: String): Boolean =
        validateToken(token) && runCatching {
            getClaims(token).get("token_type", String::class.java) == REFRESH_TYPE
        }.getOrDefault(false)

    // проверка сколько токену осталось жить
    fun getRemainingExpirationMillis(token: String): Long {
        return try {
            val expiration = getClaims(token).expiration
            // момент истечения минус сейчас
            expiration.time - System.currentTimeMillis()
        } catch (e: Exception) {
            // если токен плохой или просрочен, то все
            0L
        }
    }

    // метод для разбора токена
    private fun getClaims(token: String): Claims {
        return Jwts.parserBuilder()
            // ключ для проверки подписи
            .setSigningKey(key)
            // собираем
            .build()
            // разбираем токен с подписью проверяя токен и срок
            .parseClaimsJws(token)
            // содержимое токена
            .body
    }

    private companion object {
        const val ACCESS_TYPE = "access"
        const val REFRESH_TYPE = "refresh"
    }
}
