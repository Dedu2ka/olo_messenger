package com.example.messenger.controller

import com.example.messenger.security.WebSocketTicketService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class WebSocketTicketResponse(
    val ticket: String,
    val expiresInSeconds: Long = 30
)

@RestController
@RequestMapping("/api/v1/ws-ticket")
class WebSocketTicketController(
    private val webSocketTicketService: WebSocketTicketService
) {
    @PostMapping
    suspend fun createTicket(
        @AuthenticationPrincipal principalId: String,
        @RequestHeader("Authorization") authorization: String
    ): WebSocketTicketResponse {
        val token = authorization.removePrefix("Bearer ")
        return WebSocketTicketResponse(webSocketTicketService.create(principalId, token))
    }
}
