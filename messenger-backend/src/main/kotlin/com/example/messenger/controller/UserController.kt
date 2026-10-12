package com.example.messenger.controller

import com.example.messenger.domain.User
import com.example.messenger.domain.UserRepository
import com.example.messenger.security.AuthenticatedUser
import com.example.messenger.service.UserCacheService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import tools.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class UserDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?
)

private fun User.toDto(): UserDto = UserDto(
    id = requireNotNull(id) { "User ID is missing" }.toString(),
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl
)

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userRepository: UserRepository,
    private val userCacheService: UserCacheService
) {

    @GetMapping("/me")
    suspend fun getCurrentUser(
        @RequestParam(required = false) userId: String?,
        @AuthenticationPrincipal principalId: String
    ): UserDto {
        val uId = AuthenticatedUser.requireId(principalId, userId)

        // Новое: сначала смотрим в кэш, чтобы не ходить в Postgres на каждый запрос профиля
        userCacheService.get(uId.toString()).awaitSingleOrNull()?.let { cached ->
            return cached
        }

        val user = userRepository.findById(uId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val dto = user.toDto()

        // Новое: кладём результат в кэш на будущее
        userCacheService.put(uId.toString(), dto).awaitSingleOrNull()

        return dto
    }

    @PutMapping("/me")
    suspend fun updateProfile(
        @RequestParam(required = false) userId: String?,
        @AuthenticationPrincipal principalId: String,
        @RequestBody request: JsonNode
    ): UserDto {
        val uId = AuthenticatedUser.requireId(principalId, userId)
        if (!request.isObject) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile update body must be a JSON object")
        }

        val user = userRepository.findById(uId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val updatedUser = user.copy(
            displayName = request.getNullableText("displayName", user.displayName),
            avatarUrl = request.getNullableText("avatarUrl", user.avatarUrl)
        )

        val savedUser = userRepository.save(updatedUser)
        val dto = savedUser.toDto()

        // Новое: сразу обновляем кэш свежими данными — иначе ещё 10 минут отдавался бы старый профиль
        userCacheService.put(uId.toString(), dto).awaitSingleOrNull()

        return dto
    }

    @GetMapping("/search")
    suspend fun searchUsers(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): List<UserDto> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length > 100 || page < 0 || size !in 1..50) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search parameters")
        }
        if (normalizedQuery.isEmpty()) return emptyList()
        val offset = page.toLong() * size
        if (offset > Int.MAX_VALUE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page is too large")
        }
        val users = userRepository.searchByUsernameOrDisplayName(normalizedQuery, size, offset.toInt()).toList()

        return users.map { it.toDto() }
    }

    private fun JsonNode.getNullableText(field: String, existingValue: String?): String? {
        if (!has(field)) return existingValue
        val value = get(field)
        if (value.isNull) return null
        if (!value.isTextual) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be a string or null")
        }
        return value.asText()
    }
}
