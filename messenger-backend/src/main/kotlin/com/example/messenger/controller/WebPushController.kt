package com.example.messenger.controller

import com.example.messenger.domain.WebPushSubscriptionRepository
import com.example.messenger.security.AuthenticatedUser
import com.example.messenger.service.WebPushNotificationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

data class WebPushKeys(val p256dh: String, val auth: String)
data class WebPushSubscriptionRequest(val endpoint: String, val keys: WebPushKeys)
data class WebPushPublicKeyResponse(val publicKey: String)

@RestController
@RequestMapping("/api/v1/notifications/web-push")
class WebPushController(
    private val repository: WebPushSubscriptionRepository,
    private val webPushNotificationService: WebPushNotificationService
) {
    @GetMapping("/public-key")
    suspend fun publicKey(): WebPushPublicKeyResponse = WebPushPublicKeyResponse(
        webPushNotificationService.publicKey
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Web push is not configured")
    )

    @PostMapping
    suspend fun subscribe(
        @RequestBody request: WebPushSubscriptionRequest,
        @AuthenticationPrincipal principalId: String
    ) {
        val endpoint = request.endpoint.trim()
        if (endpoint.isBlank() || endpoint.length > MAX_ENDPOINT_LENGTH ||
            request.keys.p256dh.length !in 80..100 || request.keys.auth.length !in 16..64
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid web push subscription")
        }
        val uri = runCatching { URI(endpoint) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        val trustedPushHost = host == "web.push.apple.com" || host.endsWith(".push.apple.com") ||
            host == "fcm.googleapis.com" || host.endsWith(".push.services.mozilla.com") ||
            host.endsWith(".notify.windows.com")
        val validKeys = runCatching {
            val decoder = java.util.Base64.getUrlDecoder()
            decoder.decode(request.keys.p256dh).size == 65 && decoder.decode(request.keys.auth).size == 16
        }.getOrDefault(false)
        if (uri?.scheme != "https" || uri.userInfo != null || !trustedPushHost || !validKeys) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid web push subscription")
        }
        repository.saveSubscription(
            endpoint,
            AuthenticatedUser.requireId(principalId),
            request.keys.p256dh,
            request.keys.auth
        )
    }

    @DeleteMapping
    suspend fun unsubscribe(
        @RequestBody request: Map<String, String>,
        @AuthenticationPrincipal principalId: String
    ) {
        val endpoint = request["endpoint"]?.trim().orEmpty()
        if (endpoint.isBlank() || endpoint.length > MAX_ENDPOINT_LENGTH) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid web push endpoint")
        }
        repository.deleteForUser(endpoint, AuthenticatedUser.requireId(principalId))
    }

    private companion object { const val MAX_ENDPOINT_LENGTH = 4096 }
}
