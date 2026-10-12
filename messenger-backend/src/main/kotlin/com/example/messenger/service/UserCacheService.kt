package com.example.messenger.service

import com.example.messenger.controller.UserDto
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class UserCacheService(
    private val redisTemplate: ReactiveStringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val objectMapper = jacksonObjectMapper()
    private val ttl = Duration.ofMinutes(10)

    private fun key(userId: String): String = "cache:user:$userId"

    fun get(userId: String): Mono<UserDto> {
        return redisTemplate.opsForValue().get(key(userId))
            .mapNotNull { json ->
                try {
                    objectMapper.readValue<UserDto>(json)
                } catch (e: Exception) {
                    // Битый/несовместимый JSON в кэше — считаем как промах кэша, а не ошибку
                    null
                }
            }
            .doOnError { logger.warn("User cache read failed for userId={}", userId, it) }
            .onErrorResume { Mono.empty() }
    }

    fun put(userId: String, dto: UserDto): Mono<Boolean> {
        val json = objectMapper.writeValueAsString(dto)
        return redisTemplate.opsForValue().set(key(userId), json, ttl)
            .doOnError { logger.warn("User cache write failed for userId={}", userId, it) }
            .onErrorResume { Mono.just(false) }
    }

    fun evict(userId: String): Mono<Long> {
        return redisTemplate.delete(key(userId))
            .doOnError { logger.warn("User cache eviction failed for userId={}", userId, it) }
            .onErrorResume { Mono.just(0L) }
    }
}
