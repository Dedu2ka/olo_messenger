package com.example.messenger

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// аннотация позволяющая обьявлять бин методы, берем из зависимостей и настроек данные и создаем обьекты
// позволяет видеть сервисы, компоненты, контроллеры
@SpringBootApplication
// пустой класс, нужен для сканирования спрингом пакетов
class MessengerApplication

// запускаем программу, аргументы командой строки передаем
fun main(args: Array<String>) {
	// runApplication — запускаем спринг конейнер и сервер
	// <MessengerApplication> — указываем главный класс
	// *args — распаковываем массив в отдельные агрументы
	runApplication<MessengerApplication>(*args)
}