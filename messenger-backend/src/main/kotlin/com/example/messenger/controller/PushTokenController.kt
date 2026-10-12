package com.example.messenger.controller

import com.example.messenger.domain.PushTokenRepository
import com.example.messenger.security.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RegisterPushTokenRequest(val token: String)

@RestController
@RequestMapping("/api/v1/notifications/device-token")
class PushTokenController(private val pushTokenRepository: PushTokenRepository) {

    @PostMapping
    suspend fun registerToken(
        @RequestBody request: RegisterPushTokenRequest,
        @AuthenticationPrincipal principalId: String
    ) {
        val token = request.token.trim()
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid device token")
        }
        pushTokenRepository.registerToken(token, AuthenticatedUser.requireId(principalId))
    }

    @DeleteMapping
    suspend fun deleteToken(
        @RequestParam token: String,
        @AuthenticationPrincipal principalId: String
    ) {
        pushTokenRepository.deleteToken(token, AuthenticatedUser.requireId(principalId))
    }

    private companion object {
        const val MAX_TOKEN_LENGTH = 4096
    }
}
