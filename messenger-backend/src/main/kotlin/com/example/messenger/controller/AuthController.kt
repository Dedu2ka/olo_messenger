package com.example.messenger.controller

import com.example.messenger.domain.User
import com.example.messenger.domain.UserRepository
import com.example.messenger.security.JwtUtil
import com.example.messenger.security.TokenBlacklistService
import com.example.messenger.service.DatabaseAdvisoryLockService
import com.example.messenger.websocket.WebSocketSessionManager
import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.support.WebExchangeBindException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull

data class AuthRequest(
    @field:NotBlank(message = "Укажите логин")
    @field:Pattern(
        regexp = "^[A-Za-z0-9]+$",
        message = "Имя пользователя может содержать только латинские буквы и цифры"
    )
    val username: String,

    @field:NotBlank(message = "Укажите пароль")
    @field:Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
    @field:Pattern(
        regexp = "^[A-Za-z0-9!@#\$%^&*()_\\-+=\\[\\]{}|;:'\",.<>/?~`]+$",
        message = "Пароль может содержать только латинские буквы, цифры и спец символы"
    )
    @field:JsonAlias("passwordHash")
    val password: String
)

data class AuthResponse(
    val token: String,
    val userId: String,
    val username: String
)

data class AuthErrorResponse(
    val message: String
)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val tokenBlacklistService: TokenBlacklistService,
    private val webSocketSessionManager: WebSocketSessionManager,
    private val advisoryLockService: DatabaseAdvisoryLockService
) {

    @PostMapping("/register")
    @Transactional
    suspend fun register(
        @Valid @RequestBody request: AuthRequest
    ): ResponseEntity<AuthResponse> {
        advisoryLockService.lock("username:${request.username}")
        if (userRepository.findByUsername(request.username) != null) {
            return ResponseEntity(null, HttpStatus.CONFLICT)
        }

        val encodedPassword = passwordEncoder.encode(request.password)
            ?: throw IllegalStateException("Password encoding returned null")
        val savedUser = userRepository.save(
            User(
                username = request.username,
                passwordHash = encodedPassword
            )
        )
        val userId = savedUser.id?.toString()
            ?: throw IllegalStateException("User ID was not generated after saving user")

        return ResponseEntity.ok(
            AuthResponse(
                token = jwtUtil.generateToken(userId, savedUser.username),
                userId = userId,
                username = savedUser.username
            )
        )
    }

    @PostMapping("/login")
    suspend fun login(
        @Valid @RequestBody request: AuthRequest
    ): ResponseEntity<AuthResponse> {
        val user = userRepository.findByUsername(request.username)
            ?: return ResponseEntity(null, HttpStatus.UNAUTHORIZED)

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            return ResponseEntity(null, HttpStatus.UNAUTHORIZED)
        }

        val userId = user.id?.toString()
            ?: throw IllegalStateException("User ID is missing")

        return ResponseEntity.ok(
            AuthResponse(
                token = jwtUtil.generateToken(userId, user.username),
                userId = userId,
                username = user.username
            )
        )
    }

    // Новое: logout — кладёт токен в чёрный список Redis до конца его естественного срока жизни
    @PostMapping("/logout")
    suspend fun logout(
        @RequestHeader(value = "Authorization", required = false) authHeader: String?
    ): ResponseEntity<Void> {

        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build()
        }

        val token = authHeader.substring(7)

        if (!jwtUtil.validateToken(token)) {
            // Токен и так уже невалиден/истёк — отзывать нечего, но это не ошибка клиента
            return ResponseEntity.noContent().build()
        }

        val ttlMillis = jwtUtil.getRemainingExpirationMillis(token)

        tokenBlacklistService.blacklist(token, ttlMillis).awaitSingle()
        val userId = jwtUtil.getUserIdFromToken(token)
        webSocketSessionManager.closeSessions(userId).awaitSingleOrNull()
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationErrors(ex: WebExchangeBindException): ResponseEntity<AuthErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { it.defaultMessage ?: "Invalid value for ${it.field}" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(AuthErrorResponse(message = message))
    }
}
