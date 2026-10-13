package com.example.messenger.security

import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration

//создает экзепляр чтобы, класс с бизнес логикой
@Service
class TokenBlacklistService(
    // создлаем класс редиса и передаем сюда
    private val redisTemplate: ReactiveStringRedisTemplate
) {

    // имя ключа в редиса, указываем блэклист чтобы разные токеныы не путались
    private fun key(token: String): String = "blacklist:token:$token"

    // кладем токен в черный список, когад токен сам умрет, то и запись удалится
    fun blacklist(token: String, ttlMillis: Long): Mono<Boolean> {
        // еслитокен истек не записываем
        if (ttlMillis <= 0) {
            // ответ, что все в порядке
            return Mono.just(true)
        }
        return redisTemplate.opsForValue()          // записываем, что мы отозвали токен указывая время жизни чтобы редис автоматичкси удалил его
            .set(key(token), "revoked", Duration.ofMillis(ttlMillis))
    }

    /** Atomically claims a token for one-time refresh rotation. */
    fun blacklistIfAbsent(token: String, ttlMillis: Long): Mono<Boolean> {
        if (ttlMillis <= 0) return Mono.just(false)
        return redisTemplate.opsForValue()
            .setIfAbsent(key(token), "revoked", Duration.ofMillis(ttlMillis))
            .defaultIfEmpty(false)
    }

    // чекаеме сть ли ключ в черном списке, если есть вернем тру
    fun isBlacklisted(token: String): Mono<Boolean> {
        return redisTemplate.hasKey(key(token))
    }
}
