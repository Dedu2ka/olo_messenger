package com.example.messenger

import tools.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.Base64
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessengerApiIntegrationTests {
    @LocalServerPort
    private var port: Int = 0
    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUpClient() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }
    @Test
    fun `auth profile search messages pagination and web push subscription work together`() {
        val senderName = "sender${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val recipientName = "recipient${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val sender = register(senderName, "  StrongPass1!  ")
        val recipient = register(recipientName, "OtherPass2!")
        client.put().uri("/api/v1/users/me?userId=${recipient.userId}")
            .header("Authorization", "Bearer ${recipient.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("displayName" to "ЁЖ", "avatarUrl" to null))
            .exchange().expectStatus().isOk

        val login = client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to " $senderName ", "password" to " StrongPass1! "))
            .exchange().expectStatus().isOk.expectBody<JsonNode>().returnResult().responseBody!!
        assertEquals(sender.userId, login["userId"].asText())

        val searchResults = client.get().uri("/api/v1/users/search?query=еж")
            .header("Authorization", "Bearer ${sender.token}")
            .exchange().expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<JsonNode>>() {})
            .returnResult().responseBody!!
        assertEquals(1, searchResults.size, "Search must exclude the authenticated account")
        assertEquals(recipientName, searchResults.single()["username"].asText())

        client.put().uri("/api/v1/users/me?userId=${sender.userId}")
            .header("Authorization", "Bearer ${sender.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("displayName" to "Sender Name", "avatarUrl" to null))
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.displayName").isEqualTo("Sender Name")

        val sent = (1..3).map { n ->
            client.post().uri("/api/v1/chats/direct/messages")
                .header("Authorization", "Bearer ${sender.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "targetUserId" to recipient.userId,
                        "content" to "integration message $n",
                        "clientMessageId" to UUID.randomUUID().toString()
                    )
                )
                .exchange().expectStatus().isOk
                .expectBody<JsonNode>().returnResult().responseBody!!
        }
        val chatList = client.get().uri("/api/v1/chats?userId=${sender.userId}")
            .header("Authorization", "Bearer ${sender.token}")
            .exchange().expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<JsonNode>>() {})
            .returnResult().responseBody!!
        assertEquals(1, chatList.size)
        val chatId = chatList.single()["id"].asText()
        assertEquals("integration message 3", chatList.single()["lastMessage"].asText())
        assertEquals(sender.userId, chatList.single()["lastMessageSenderId"].asText())
        assertNotNull(chatList.single()["lastMessageAt"]?.asText())

        val newestPage = getMessages(chatId, sender.token, "limit=2")
        assertEquals(listOf(sent[1]["id"].asText(), sent[2]["id"].asText()), newestPage.map { it["id"].asText() })
        val olderPage = getMessages(chatId, sender.token, "limit=2&beforeMessageId=${newestPage.first()["id"].asText()}")
        assertEquals(listOf("integration message 1"), olderPage.map { it["content"].asText() })
        val receivedByRecipient = getMessages(chatId, recipient.token, "limit=50")
        assertEquals(listOf("integration message 1", "integration message 2", "integration message 3"), receivedByRecipient.map { it["content"].asText() })

        val endpoint = "https://web.push.apple.com/${UUID.randomUUID()}"
        val subscription = mapOf(
            "endpoint" to endpoint,
            "keys" to mapOf("p256dh" to base64Url(ByteArray(65) { 1 }), "auth" to base64Url(ByteArray(16) { 2 }))
        )
        client.post().uri("/api/v1/notifications/web-push")
            .header("Authorization", "Bearer ${recipient.token}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(subscription)
            .exchange().expectStatus().isOk
        client.method(org.springframework.http.HttpMethod.DELETE).uri("/api/v1/notifications/web-push")
            .header("Authorization", "Bearer ${recipient.token}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("endpoint" to endpoint))
            .exchange().expectStatus().isOk

        val refreshed = client.post().uri("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("refreshToken" to sender.refreshToken))
            .exchange().expectStatus().isOk.expectBody<JsonNode>().returnResult().responseBody!!
        assertNotNull(refreshed["refreshToken"]?.asText())
        client.post().uri("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("refreshToken" to sender.refreshToken))
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `authentication trims edge whitespace and rejects whitespace inside credentials`() {
        val username = "trim${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val user = register(" $username ", "  StrongPass1!  ")
        assertEquals(username, user.username)

        client.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "bad${UUID.randomUUID().toString().take(8)}", "password" to "Strong Pass1!"))
            .exchange().expectStatus().isBadRequest
    }

    private fun register(username: String, password: String): Session {
        val result = client.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to username, "password" to password))
            .exchange().expectStatus().isOk.expectBody<JsonNode>().returnResult().responseBody!!
        return Session(
            token = result["token"].asText(),
            refreshToken = result["refreshToken"].asText(),
            userId = result["userId"].asText(),
            username = result["username"].asText()
        )
    }

    private fun getMessages(chatId: String, token: String, query: String): List<JsonNode> =
        client.get().uri("/api/v1/chats/$chatId/messages?$query")
            .header("Authorization", "Bearer $token")
            .exchange().expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<JsonNode>>() {})
            .returnResult().responseBody!!

    private fun base64Url(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private data class Session(val token: String, val refreshToken: String, val userId: String, val username: String)

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine"))

        @Container
        @JvmStatic
        val redis = FixedPortRedisContainer()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}" }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
            registry.add("spring.data.redis.host") { "localhost" }
            registry.add("spring.data.redis.port") { 16379 }
            registry.add("jwt.secret") { "test-secret-that-is-more-than-thirty-two-bytes-long" }
            registry.add("app.message-encryption-key-base64") {
                Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
            }
            registry.add("app.fcm.service-account-path") { "" }
            registry.add("app.web-push.public-key") { "" }
            registry.add("app.web-push.private-key") { "" }
            registry.add("app.cors.allowed-origins") { "https://dedu2ka.github.io" }
        }
    }

    class FixedPortRedisContainer : GenericContainer<FixedPortRedisContainer>(DockerImageName.parse("redis:7-alpine")) {
        init { addFixedExposedPort(16379, 6379) }
    }
}
