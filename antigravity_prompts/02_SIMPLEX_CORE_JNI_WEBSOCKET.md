# ЗАДАЧА 2: Интеграция официального ядра SimpleX Chat Core (`libsimplex.so`) с поддержкой SMP/XFTP через Tor `.onion`

## Контекст задачи
В проекте реализованы `SimpleXEmbeddedController.kt`, `SimpleXSmpClient.kt` и `SimpleXftpClient.kt`.
Чтобы приложение было на 100% совместимо с реальными публичными и приватными серверами **SimpleX SMP / XFTP** (включая `.onion` v3 скрытые сервисы) и могло обмениваться сообщениями, файлами и игровыми ходами с официальным приложением **SimpleX Chat**, необходимо подключить официальное нативное ядро `simplex-chat` в режиме локального WebSocket JSON-RPC сервера (`ws://127.0.0.1:5225`), маршрутизирующего весь внешний трафик через локальный Tor SOCKS5 (`127.0.0.1:9050`).

---

## Инструкции к выполнению

### Шаг 1. Добавление нативного бинарника `libsimplex.so`
1. Скачай или скомпилируй официальный бинарник `simplex-chat` (CLI / Core) для Android (`arm64-v8a` и `x86_64`) из репозитория `simplex-chat/simplex-chat`.
2. Помести исполняемый ELF-файл в:
   - `app/src/main/jniLibs/arm64-v8a/libsimplex.so`
   - `app/src/main/jniLibs/x86_64/libsimplex.so`

### Шаг 2. Модификация `SimpleXEmbeddedController.kt`
1. Открой `app/src/main/java/com/example/network/SimpleXEmbeddedController.kt`.
2. В методе `startSimpleX(...)` добавь приоритетный поиск нативного бинарника в `nativeLibraryDir`:
   ```kotlin
   val nativeSimplex = File(context.applicationInfo.nativeLibraryDir, "libsimplex.so")
   ```
3. Если `nativeSimplex.exists() && nativeSimplex.canExecute()`, запускай его через `ProcessBuilder` как локальный WebSocket JSON-RPC демон на порту `5225`:
   ```kotlin
   val dbDir = File(context.filesDir, "simplex_v6_core").apply { mkdirs() }
   val cmd = mutableListOf(
       nativeSimplex.absolutePath,
       "-p", "5225",
       "-d", File(dbDir, "simplex_client").absolutePath,
       "--socks-proxy", "127.0.0.1:$torSocksPort"
   )
   if (smpOnionAddress.isNotBlank()) {
       cmd.addAll(listOf("-s", smpOnionAddress))
   }
   if (xftpOnionAddress.isNotBlank()) {
       cmd.addAll(listOf("--xftp-server", xftpOnionAddress))
   }
   ```
4. После запуска процесса открывай WebSocket-соединение (`OkHttpClient.newWebSocket`) к `ws://127.0.0.1:5225`.

### Шаг 3. Интеграция протокола команд SimpleX JSON-RPC API
Внутри `SimpleXEmbeddedController.kt` и `SimpleXSmpClient.kt` реализуй отправку и обработку стандартных JSON-RPC команд ядра SimpleX (`{"corrId": "<id>", "cmd": "<command>"}`):

1. **Инициализация профиля при старте**:
   - Отправь `{"corrId": "init_user", "cmd": "/u"}` — если активного профиля нет (`chatCmdError`), создай его командой:
     `{"corrId": "create_user", "cmd": "/_create user {\"displayName\": \"SovereignPlayer\"}"}`
2. **Принудительная маршрутизация через Tor Onion (`.onion` hosts)**:
   - Отправь:
     `{"corrId": "net_cfg", "cmd": "/set_network_config {\"socksProxy\": \"127.0.0.1:9050\", \"onionHosts\": \"prefer\"}"}`
3. **Создание одноразового инвайта / QR-ссылки**:
   - Метод `createInvitationLink()` должен отправлять `{"corrId": "invite", "cmd": "/connect"}` и извлекать сгенерированную `smp://...` ссылку из ответа `connReqInvitation`.
4. **Подключение к пиру по ссылке/QR**:
   - Метод `connectByInvitation(link: String)` должен отправлять `{"corrId": "join", "cmd": "/c $link"}`.
5. **Отправка сообщений и P2P-ходов нард (`P2PGameProtocol`)**:
   - В `sendMessage(recipient: String, text: String)` отправляй:
     `{"corrId": "msg_${System.currentTimeMillis()}", "cmd": "@$recipient $text"}`
6. **Отправка файлов и шардов ZK-Vault через XFTP**:
   - В `sendFile(recipient: String, filePath: String)` отправляй:
     `{"corrId": "file_${System.currentTimeMillis()}", "cmd": "/f @$recipient $filePath"}`
7. **Обработка входящих событий**:
   - Слушай события `newChatItems`, `contactConnected`, `rcvFileDescrReady` из WebSocket `ws://127.0.0.1:5225` и передавай текст входящих сообщений в `onMessageReceived(sender, text)`, чтобы `GameViewModel` автоматически обрабатывал как сообщения чата, так и P2P-команды игры в нарды (`MOVE:`, `ROLL:`, `BOARD_HASH:`, `AUDIO_FRAME:`).
