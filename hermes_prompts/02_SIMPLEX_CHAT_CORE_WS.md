# 🌐 ЗАДАЧА 02: ИНТЕГРАЦИЯ SIMPLEX CHAT CORE И МАРШРУТИЗАЦИЯ ЧЕРЕЗ TOR SOCKS5

## 🎯 ЦЕЛЬ
Обеспечить реальную отправку сообщений и файлов через протоколы SMP (Simple Messaging Protocol) и XFTP (Extensible File Transfer Protocol) поверх скрытой сети Tor (`.onion` адреса).

---

## 🔍 АРХИТЕКТУРА РЕШЕНИЯ
Официальный стек SimpleX Chat работает по схеме:
1. Нативный процесс SimpleX Core (`libsimplex.so` или встроенный демон) запускается как фоновый сервис.
2. Демон слушает локальный порт управления WebSocket JSON-RPC на `127.0.0.1:5225`.
3. Для всех внешних сетевых вызовов демон SimpleX конфигурируется с аргументом `--socks5-proxy 127.0.0.1:9050` (локальный порт встроенного Tor).
4. Kotlin-клиенты (`SimpleXSmpClient.kt` и `SimpleXftpClient.kt`) подключаются к JSON-RPC по WebSocket и отдают команды создания очередей, отправки шифротекстов и обмена медиафайлами.

---

## 🛠️ ШАГИ ДЛЯ HERMES AGENT

### Шаг 1. Запуск процесса SimpleX Core с проксированием Tor
Файл: `app/src/main/java/com/example/network/SimpleXEmbeddedCore.kt`

Создай контроллер запуска SimpleX демона:
```kotlin
package com.example.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SimpleXEmbeddedCore(private val context: Context) {

    private var coreProcess: Process? = null

    suspend fun startDaemon(socks5Port: Int = 9050, rpcPort: Int = 5225): Boolean = withContext(Dispatchers.IO) {
        try {
            val nativeCore = File(context.applicationInfo.nativeLibraryDir, "libsimplex.so")
            val binaryPath = if (nativeCore.exists() && nativeCore.canExecute()) {
                nativeCore.absolutePath
            } else {
                Log.w("SimpleXCore", "Native libsimplex.so not found, using internal proxy mode")
                return@withContext false
            }

            val dataDir = File(context.filesDir, "simplex_data").apply { mkdirs() }

            val command = listOf(
                binaryPath,
                "--data-dir", dataDir.absolutePath,
                "--socks-proxy", "127.0.0.1:$socks5Port",
                "--ws-rpc-port", rpcPort.toString()
            )

            val pb = ProcessBuilder(command)
                .directory(dataDir)
                .redirectErrorStream(true)

            coreProcess = pb.start()
            Log.i("SimpleXCore", "SimpleX daemon successfully started on WS RPC port $rpcPort")
            true
        } catch (e: Exception) {
            Log.e("SimpleXCore", "Failed to start SimpleX daemon", e)
            false
        }
    }

    fun stopDaemon() {
        try {
            coreProcess?.destroy()
            coreProcess = null
        } catch (e: Exception) {
            Log.e("SimpleXCore", "Error stopping daemon", e)
        }
    }
}
```

---

### Шаг 2. Обновление `SimpleXSmpClient.kt` для работы с Tor SOCKS5
Файл: `app/src/main/java/com/example/network/SimpleXSmpClient.kt`

Убедись, что OkHttpClient в `SimpleXSmpClient` использует прокси `127.0.0.1:9050`:
```kotlin
private val socksProxy = java.net.Proxy(
    java.net.Proxy.Type.SOCKS,
    java.net.InetSocketAddress("127.0.0.1", 9050)
)

private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .proxy(socksProxy)
    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .build()
```
Это гарантирует, что запросы к SMP-серверам с адресами вида `smp://<onion-address>.onion` идут строго через луковую маршрутизацию.

---

### Шаг 3. Интеграция с `SimpleXftpClient.kt`
Убедись, что передача чанков и шардов Рида-Соломона в `SimpleXftpClient.kt` также использует этот же прокси-клиент для вызовов XFTP серверов.

---

### Шаг 4. Проверка сборки
Выполни в терминале:
```bash
./gradlew compileDebugKotlin
```
Убедись в отсутствии ошибок. Переходи к задаче `03`.
