package com.example.messenger.security

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

object AuthenticatedUser {
    fun requireId(principalId: String, requestedId: String? = null): UUID {
        // превращаем id из токена в uuid
        val authenticatedId = parseUuid(principalId)
        // если клиент ничего не прислал, то возварщаем токен
        if (requestedId.isNullOrBlank()) return authenticatedId

        // превращаем id из запроса в uuid
        val suppliedId = parseUuid(requestedId)
        // если id не совпадают, то 403
        if (suppliedId != authenticatedId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User ID does not match authenticated user")
        }
        return authenticatedId
    }

    fun parseUuid(id: String): UUID = try {
        UUID.fromString(id)
    // если формат неверен, то вернем 400
    } catch (_: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user ID")
    }
}