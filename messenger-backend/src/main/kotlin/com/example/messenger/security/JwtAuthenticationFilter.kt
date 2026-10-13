package com.example.messenger.security

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

// создаем 1 экземпляр компонент которым spring  в праве пользоваться
@Component
class JwtAuthenticationFilter(
    // Для проверки токена
    private val jwtUtil: JwtUtil,
    // Для проверки, не отозван ли токен при выходе
    private val tokenBlacklistService: TokenBlacklistService
) : WebFilter {

    // метод вызывается на каждый запрос, exchange запрос+ответ, chain передаем запрос следующему фильтру
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // путь запроса
        val path = exchange.request.uri.path

        // при вебсокете  или входа регистрации не проверяем токен
        if (path.startsWith("/api/v1/auth/") || path == "/ws" || path.startsWith("/ws/")) {
            // пропускаем
            return chain.filter(exchange)
        }

        // читаем загловок
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)

        // проверяем что загловок есть и начинается с бир
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // отреаем бир и остается сам токен
            val token = authHeader.substring(7)
            // чекаем, что токен валиден
            if (jwtUtil.validateAccessToken(token)) {
                // проверяем не отозван ли токен
                return tokenBlacklistService.isBlacklisted(token)
                    .flatMap { isBlacklisted ->
                        if (isBlacklisted) {
                            // если отозванп пропускаем без отметки о пользователе, чтобы наш securiry развернул
                            chain.filter(exchange)
                        } else {
                            // достуаем владельца токена
                            val userId = jwtUtil.getUserIdFromToken(token)
                            // выдаем право
                            val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
                            // создаем личность
                            val auth = UsernamePasswordAuthenticationToken(userId, null, authorities)

                            // передаем дальше
                            chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                        }
                    }
            }
        }
        // если токена нет, пропускаем без отметки
        return chain.filter(exchange)
    }
}
