package com.example.messenger.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

// аннотация обозначяющая что методы этого класса надо выполнить при старте
@Configuration
// аннонотация включающая реактивную защиту
@EnableWebFluxSecurity
class SecurityConfig(
    // фильтр проверяющий jwt токен
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    // \${app.cors.allowed-origins} адрес в внашем ямл файле настроек
    // аннотация Value говрит найти в файле настроек ключ по адлресу и положить значение в параметр
    @org.springframework.beans.factory.annotation.Value("\${app.cors.allowed-origins}")
    private val allowedOrigins: List<String>
) {

    // Бин кодировщика паролей. Его используют AuthController
    @Bean
    // Возвращаем интерфейс PasswordEncoder
    fun passwordEncoder(): PasswordEncoder {
        // кодируем пароли
        return BCryptPasswordEncoder()
    }

    // Источник CORS-конфигурации подключается непосредственно к Spring Security,
    // чтобы preflight OPTIONS обрабатывался до проверки JWT.
    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        // список правил
        val config = CorsConfiguration().apply {
            // Указываем, что разрешенные сайты, это те которые указаны в списке настроек
            allowedOrigins = this@SecurityConfig.allowedOrigins
            addAllowedHeader("*")        // любые заголовки разрешены
            addAllowedMethod("*")        // любые типы запросов разрешены
            // cookie вместе с запросом не нужны
            allowCredentials = false
        }

        // эти правила действуют для всех адресов сервера
        val source = UrlBasedCorsConfigurationSource()
        // "/**" значит «любой адрес»
        source.registerCorsConfiguration("/**", config)
        return source
    }

    // бин описывающий какие url разрешены и как
    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        // делаем вызовы, билд вернет результат
        return http
            // csfr защита нужна для куки, у нас нету
            .csrf { it.disable() }
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            // описываем правила для доступа к url
            .authorizeExchange { exchanges ->
                exchanges
                    // (регистрация, вход, выход) доуступ для всех, токен не проверяем
                    .pathMatchers("/api/v1/auth/**").permitAll()
                    // профили только с токеном
                    .pathMatchers("/api/v1/users/**").authenticated()
                    // чаты только елси если у нас есть право
                    .pathMatchers("/api/v1/chats/**").hasAuthority("ROLE_USER")
                    // для вебсокета, мы не смотрим заголовок, а проверяем тикет в адресе
                    .pathMatchers("/ws", "/ws/**").permitAll()
                    // остальные url  с токеном
                    .anyExchange().authenticated()
            }
            // дорбавляем наш фильтр вместо стандартной аунтификации
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }
}
