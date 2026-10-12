param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')

Write-Host "Вход в Olo Messenger API" -ForegroundColor Cyan
Write-Host "Скрипт войдёт под аккаунтом-отправителем. Телефон должен быть авторизован под вторым участником выбранного чата.`n"

$username = Read-Host "Логин аккаунта-отправителя"
$securePassword = Read-Host "Пароль" -AsSecureString
$passwordPtr = [IntPtr]::Zero

try {
    $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)

    $loginBody = @{
        username = $username
        passwordHash = $password
    } | ConvertTo-Json -Compress

    $login = Invoke-RestMethod -Method Post `
        -Uri "$BaseUrl/api/v1/auth/login" `
        -ContentType "application/json" `
        -Body $loginBody
}
finally {
    if ($passwordPtr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
    }
    $password = $null
    $loginBody = $null
    $securePassword.Dispose()
}

$headers = @{ Authorization = "Bearer $($login.token)" }
$chatsUri = "$BaseUrl/api/v1/chats?userId=$([uri]::EscapeDataString($login.userId))"
$chats = @(Invoke-RestMethod -Method Get -Uri $chatsUri -Headers $headers)

if ($chats.Count -eq 0) {
    throw "У этого аккаунта нет чатов. Сначала создайте чат с аккаунтом, который открыт на телефоне."
}

Write-Host "`nЧаты аккаунта:`n" -ForegroundColor Cyan
for ($i = 0; $i -lt $chats.Count; $i++) {
    $partner = $chats[$i].partnerName
    if ([string]::IsNullOrWhiteSpace($partner)) { $partner = "Чат" }
    $preview = $chats[$i].lastMessage
    if ([string]::IsNullOrWhiteSpace($preview)) { $preview = "(сообщений пока нет)" }
    Write-Host ("[{0}] {1} — {2}" -f ($i + 1), $partner, $preview)
}

$choice = 0
while ($choice -lt 1 -or $choice -gt $chats.Count) {
    $rawChoice = Read-Host "Введите номер чата"
    if (-not [int]::TryParse($rawChoice, [ref]$choice) -or $choice -lt 1 -or $choice -gt $chats.Count) {
        $choice = 0
        Write-Host "Введите число от 1 до $($chats.Count)." -ForegroundColor Yellow
    }
}

$content = Read-Host "Текст тестового сообщения"
if ([string]::IsNullOrWhiteSpace($content)) {
    throw "Текст сообщения не может быть пустым."
}

$selectedChat = $chats[$choice - 1]
$messageBody = @{
    senderId = $login.userId
    content = $content
} | ConvertTo-Json -Compress

$sent = Invoke-RestMethod -Method Post `
    -Uri "$BaseUrl/api/v1/chats/$($selectedChat.id)/messages" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $messageBody

Write-Host "`nСообщение отправлено в чат с $($selectedChat.partnerName). ID: $($sent.id)" -ForegroundColor Green
Write-Host "Если push настроен и выбранный собеседник авторизован на телефоне, уведомление должно прийти ему."
