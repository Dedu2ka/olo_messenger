package com.example.messenger.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date

@Component
class JwtUtil(
    // берем значение из yaml JWT_SECRET
    @Value("\${jwt.secret}")
    private val secret: String,

    // время жизни токена в милисекндах
    @Value("\${jwt.expiration-ms:86400000}")
    private val expirationMs: Long
) {

    // делаем ключ преобразуя сроку в байты
    private val key = Keys.hmacShaKeyFor(
        secret.toByteArray(Charsets.UTF_8)
    )

    // создаем токен
    fun generateToken(
        userId: String,
        username: String
    ): String {

        val now = Date()
        // ммоент когда токен перестанет действовать
        val expiryDate = Date(
            now.time + expirationMs
        )

        return Jwts.builder()
            // владелец токена
            .setSubject(userId)
            // доп поле имени
            .claim("username", username)
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
}