# ЗАДАЧА 3: Нативный L3 TUN-to-SOCKS5 маршрутизатор (`hev-socks5-tunnel`) для системного `FoxrayVpnService`

## Контекст проблемы
В `app/src/main/java/com/example/network/FoxrayVpnService.kt` сейчас используется `Tun2SocksPacketRouter.kt`, который парсит L3 IPv4 заголовки из `vpnInterface.fileDescriptor` для телеметрии, но не содержит полного нативного TCP/IP стека (lwIP) для сборки TCP-потоков и их инкапсуляции в SOCKS5-прокси (`127.0.0.1:10808` или `127.0.0.1:9050`).

Чтобы системный VPN Android реально туннелировал весь трафик через цепочку Xray VLESS / Tor SOCKS5, необходимо подключить легковесный нативный стек **`hev-socks5-tunnel`** (`libhev-socks5-tunnel.so`).

---

## Инструкции к выполнению

### Шаг 1. Подключение `libhev-socks5-tunnel.so`
1. Скомпилируй через Android NDK или подключи готовый бинарник `libhev-socks5-tunnel.so` (из проекта `heiher/hev-socks5-tunnel`) для `arm64-v8a` и `x86_64`:
   - `app/src/main/jniLibs/arm64-v8a/libhev-socks5-tunnel.so`
   - `app/src/main/jniLibs/x86_64/libhev-socks5-tunnel.so`
2. Создай JNI-обёртку `app/src/main/java/com/example/network/NativeTun2Socks.kt`:
   ```kotlin
   package com.example.network

   object NativeTun2Socks {
       var isLibraryLoaded = false
           private set

       init {
           try {
               System.loadLibrary("hev-socks5-tunnel")
               isLibraryLoaded = true
           } catch (_: UnsatisfiedLinkError) {
               isLibraryLoaded = false
           }
       }

       external fun TProxyStartService(configPath: String, fd: Int)
       external fun TProxyStopService()
       external fun TProxyGetStats(): LongArray
   }
   ```

### Шаг 2. Обновление `FoxrayVpnService.kt`
1. Открой `app/src/main/java/com/example/network/FoxrayVpnService.kt`.
2. Внутри `startVpn(...)` при настройке `Builder()`:
   - Добавь перехват маршрута по умолчанию:
     ```kotlin
     builder.addRoute("0.0.0.0", 0)
     ```
   - **КРИТИЧЕСКИ ВАЖНО**: Исключи само приложение из VPN-туннеля, чтобы исходящие сокеты демонов `libtor.so`, `libxray.so` и `libsimplex.so` шли напрямую в интернет и не попадали в бесконечную петлю внутри TUN-интерфейса:
     ```kotlin
     try {
         builder.addDisallowedApplication(packageName)
     } catch (_: Exception) {}
     ```
3. После создания `vpnInterface = builder.establish()` сгенерируй конфигурационный файл `File(filesDir, "tproxy.yml")`:
   ```yaml
   tunnel:
     mtu: 1500
     ipv4: 10.255.0.1
   socks5:
     port: 10808
     address: 127.0.0.1
     udp: 'udp'
   ```
   (Если Xray не запущен, а работает только Tor, используй порт `9050`).
4. Если `NativeTun2Socks.isLibraryLoaded == true`, запусти нативный туннель:
   ```kotlin
   NativeTun2Socks.TProxyStartService(tproxyConfigFile.absolutePath, vpnInterface!!.fd)
   ```
   Иначе оставь существующий `packetRouter` как резервный вариант.
5. В методе `stopVpn()` вызывай `if (NativeTun2Socks.isLibraryLoaded) NativeTun2Socks.TProxyStopService()` перед закрытием `vpnInterface?.close()`.
