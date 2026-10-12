package com.example.messenger.websocket

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

// аннотация обозначяющая что методы этого класса надо выполнить при старте
@Configuration
class WebSocketConfig(
    // спинг сам подставит ChatWebSocketHandler.
    private val chatWebSocketHandler: ChatWebSocketHandler
) {

    @Bean
    fun webSocketHandlerMapping(): HandlerMapping {
        // mapOf — создаёт неизменяемую карту; 
        //"/ws" путь
        //`to` создаёт пару ключ  значение:
        // запросы на /ws будет обрабатывать chatWebSocketHandler
        val map = mapOf("/ws" to chatWebSocketHandler)
        // порядок, чем меньше число, тем раньше проверяется
        val order = -1

        // возвращаем карту и порядок
        return SimpleUrlHandlerMapping(map, order)
    }

    @Bean
    // Без него WebFlux нашёл бы обработчик для /ws, но не знал бы, как его «запустить»
    fun handlerAdapter(): WebSocketHandlerAdapter {
        // Возвращаем стандартный адаптер без настроек; он выполняет рукопожатие (handshake) HTTP → WebSocket
        return WebSocketHandlerAdapter()
    }
}