@echo off
chcp 65001 >nul
echo ==================================================
echo   Запуск сервера Messenger (БД, Redis, Бэкенд)
echo ==================================================

cd /d "%~dp0"

echo [1/2] Очистка и сборка JAR-файла через Gradle...
call .\gradlew clean bootJar
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Не удалось собрать JAR-файл!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Пересборка и запуск контейнеров Docker...
docker compose up --build -d
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Не удалось запустить Docker контейнеры!
    pause
    exit /b %ERRORLEVEL%
)

echo ==================================================
echo   Сервер успешно запущен и работает в фоновом режиме!
echo ==================================================
timeout /t 5