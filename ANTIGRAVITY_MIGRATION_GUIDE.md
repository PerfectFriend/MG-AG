# 🚀 ANTIGRAVITY MIGRATION PLAYBOOK: Sovereign Backgammon & SimpleX Onion Messenger (Beta Release)

Этот документ содержит полное техническое руководство и **готовые промпты (copy-paste)** для агента **Antigravity** (или локальной среды с Android NDK / Gradle / Go / Haskell FFI), чтобы превратить экспортированный проект из AI Studio в **100% боевую бета-версию APK**, способную подключаться к реальным узлам **SimpleX SMP / XFTP** через **Tor Onion Routing (SOCKS5)** и **Xray/VLESS** на современных версиях Android (Android 10–15, `targetSdk = 36`).

---

## 📊 Что уже переведено в 100% ЗЕЛЁНЫЙ статус (внутри AI Studio)

Перед экспортом в Antigravity следующие модули были полностью доработаны до production-уровня на чистом Kotlin/JVM:
1. **`ZeroKnowledgeVaultEngine.kt` + `SimpleXftpClient.kt`**:
   - Настоящая арифметика поля Галуа **$GF(2^8)$** (примитивный полином `0x11D`), систематическая матрица Коши и **обращение матрицы методом Гаусса-Жордана** (`invertMatrixGF256`).
   - Восстановление файлов 4-из-6 работает при потере **любых двух шардов** (включая основные шарды данных Data #1 и Data #3) с проверкой SHA-256 и шифрованием **AES-256-GCM** + **PBKDF2-HMAC-SHA256**.
2. **`DoubleRatchetVault.kt`**:
   - Полная реализация **RFC 5869 HKDF-SHA256** (`hkdfExtract` / `hkdfExpand`).
   - Хранилище пропущенных ключей **`skippedMessageKeys` (`MAX_SKIP = 256`)** для расшифровки пакетов, пришедших с нарушением порядка (out-of-order) через асинхронные SMP/Tor очереди.
   - Привязка заголовков (Public Key, Sequence Number, Previous Chain Length) через **AEAD AAD (`cipher.updateAAD`)** и выравнивание пакетов до **16 КБ**.
3. **`HybridPostQuantumEngine.kt`**:
   - Реальная полиномиальная арифметика на решетках **Ring-LWE / ML-KEM** в кольце $R_q = \mathbb{Z}_{3329}[X] / (X^{256} + 1)$ с центрированным биномиальным распределением шума ($\eta = 2$), инкапсуляцией/декапсуляцией 256-битного ключа, гибридным HKDF-SHA256 и визуальным SAS-отпечатком (4 эмодзи).
4. **`SimpleXCallManager.kt`**:
   - Замена XOR на пакетное **AES-256-GCM AEAD** шифрование аудиофреймов (`[4B seq][12B IV][Ciphertext + 16B Tag]`) с защитой от replay-атак и **туннелированием голосового потока поверх TCP/Onion сокета (`AUDIO_FRAME`)**, когда UDP недоступен.
5. **`AppDatabase.kt` + `MessengerVaultRepository`**:
   - Персистентное хранение секретных комнат (`EncryptedRoomEntity`), сообщений (`EncryptedMessageEntity`) и контактов (`EncryptedContactEntity`) в **Room SQLite**, где каждое поле шифруется на диске алгоритмом **AES-256-GCM** (`SecureCryptoVault`).
6. **`CryptoBillingEngine.kt`**:
   - Реальные сетевые запросы (`OkHttpClient`) к публичным блокчейн-индексаторам **Mempool.space API (BTC)**, **TronScan API (USDT TRC-20)** и **Blockchair API (Monero XMR)** + живые котировки **Binance Spot Ticker API**.
7. **`P2PGameProtocol.kt` + `MentalDiceEngine.kt`**:
   - Криптографический протокол честного броска костей Commit-Reveal (HMAC-SHA256) и автоматическая сверка консенсуса доски после каждого хода через **SHA-256 Board State Hash (`BOARD_HASH`)**.

---

## 🔴 Что осталось в КРАСНОЙ зоне (Требует миграции в Antigravity)

В облачном контейнере AI Studio действуют ограничения Android SELinux (`W^X` / запрет `execve` из `filesDir` на Android 10+ при `targetSdk >= 29`) и отсутствие NDK/CMake/Go-компилятора. Поэтому в Antigravity необходимо выполнить **4 шага интеграции нативных библиотек (`jniLibs` / JNI / AAR)**:

1. **Упаковка нативных демонов (`libtor.so`, `libxray.so`) в `src/main/jniLibs/<abi>/` и включение `useLegacyPackaging = true`**, чтобы Android PackageManager распаковывал их в системную директорию `applicationInfo.nativeLibraryDir` с правом исполнения `r-xp` (обход блокировки SELinux `Permission denied` на Android 10–15).
2. **Подключение официального ядра SimpleX Core (`simplex-chat` / `libsimplex.so` через JNI C-FFI или WebSocket JSON-RPC сервер на `127.0.0.1:5225`)** для полной совместимости с бинарным протоколом SMP/XFTP v6.x и Double Ratchet `NaCl/crypto_box` официального приложения SimpleX Chat.
3. **Замена учебного `Tun2SocksPacketRouter.kt` на нативный стек `hev-socks5-tunnel` (`libhev-socks5-tunnel.so`) или `libv2ray.aar`**, чтобы `FoxrayVpnService` маршрутизировал реальные L3 IPv4/IPv6 пакеты из `ParcelFileDescriptor` (TUN) в SOCKS5-прокси Tor/Xray.
4. **Интеграция `liboqs-android` / `BouncyCastle PQC (bcprov-jdk18on)` + `libsignal-client`** (опционально для замены чистого Kotlin Ring-LWE и ECDH на сертифицированные NIST FIPS 203 ML-KEM-1024 и Curve25519/X25519).

---

## 📋 ПОШАГОВЫЕ ПРОМПТЫ ДЛЯ ANTIGRAVITY

Скопируйте и отправьте эти промпты по очереди в **Antigravity** после импорта архива проекта:

---

### 🔹 ПРОМПТ 1: Обход SELinux W^X на Android 10–15 и упаковка `libtor.so` и `libxray.so` в `jniLibs`

```text
Задача 1 (Нативная упаковка демонов Tor и Xray для обхода SELinux Android 10-15):

В Android при targetSdk >= 29 SELinux блокирует запуск бинарных файлов из context.filesDir или context.getDir("bin") с ошибкой EACCES (Permission denied). Единственный разрешённый путь запуска — распаковка исполняемых файлов как псевдо-библиотек `lib*.so` в `applicationInfo.nativeLibraryDir`.

Выполни следующие изменения в проекте:
1. В `app/build.gradle.kts` внутри блока `android { packaging { jniLibs { useLegacyPackaging = true } } }` включи `useLegacyPackaging = true`, чтобы APK распаковывал `.so` файлы в `nativeLibraryDir` при установке.
2. Скачай или скомпилируй актуальные бинарники для архитектур `arm64-v8a` и `x86_64`:
   - Tor Expert Bundle / `libtor.so` -> положи в `app/src/main/jniLibs/arm64-v8a/libtor.so` и `app/src/main/jniLibs/x86_64/libtor.so`.
   - Xray-core (`xray`) -> переименуй в `libxray.so` и положи в `app/src/main/jniLibs/arm64-v8a/libxray.so` и `app/src/main/jniLibs/x86_64/libxray.so`.
3. Обнови `TorEmbeddedController.kt`:
   - Ищи бинарник в `File(context.applicationInfo.nativeLibraryDir, "libtor.so")` (с фоллбэком на `TorResourceInstaller`, если `libtor.so` не найден).
   - Добавь параметр `--ControlPort 127.0.0.1:9051` и `--CookieAuthentication 1`, чтобы отслеживать статус Bootstrap и создание v3 Hidden Service в реальном времени.
4. Обнови `V2RayEmbeddedController.kt`:
   - Запускай процесс напрямую из `File(context.applicationInfo.nativeLibraryDir, "libxray.so")` вместо распаковки из `assets/bin`.
5. Проверь сборку `./gradlew assembleDebug` и убедись, что оба файла `libtor.so` и `libxray.so` лежат внутри `lib/arm64-v8a/` в итоговом APK.
```

---

### 🔹 ПРОМПТ 2: Подключение реального ядра SimpleX Core (`libsimplex.so` / WebSocket JSON-RPC over Tor Onion)

```text
Задача 2 (Интеграция официального ядра SimpleX Chat Core для подключения к SMP/XFTP через Tor Onion):

Сейчас в проекте есть `SimpleXEmbeddedController.kt` и `SimpleXSmpClient.kt`. Нам нужно подключить официальное ядро `simplex-chat` (v6.x+), чтобы приложение общалось по бинарному протоколу SMP и XFTP (включая `.onion` серверы) и было на 100% совместимо с официальным клиентом SimpleX Chat.

Выполни следующие шаги:
1. Помести нативный бинарник SimpleX CLI / Core для Android (`arm64-v8a` и `x86_64`) в:
   - `app/src/main/jniLibs/arm64-v8a/libsimplex.so`
   - `app/src/main/jniLibs/x86_64/libsimplex.so`
   (Либо подключи официальные JNI-биндинги `simplex-chat` C-FFI: `simplex_chat_start`, `simplex_chat_send_cmd`).
2. Модифицируй `SimpleXEmbeddedController.kt`:
   - Запускай `File(context.applicationInfo.nativeLibraryDir, "libsimplex.so")` в режиме локального WebSocket JSON-RPC сервера:
     `libsimplex.so -p 5225 -d <context.filesDir>/simplex_db --socks-proxy 127.0.0.1:9050` (или `127.0.0.1:10808`, если активен мост Xray->Tor).
   - Если заданы кастомные `.onion` адреса `smpOnionAddress` и `xftpOnionAddress`, передавай их через `-s <smpOnionAddress>` и `--xftp-server <xftpOnionAddress>`.
3. Обнови `SimpleXSmpClient.kt` так, чтобы при запущенном локальном демоне на `ws://127.0.0.1:5225` он отправлял команды в формате официального SimpleX JSON API:
   - `{"corrId": "1", "cmd": "/_create user ..."}` при инициализации профиля
   - `{"corrId": "2", "cmd": "/set_network_config {\"socksProxy\":\"127.0.0.1:9050\",\" onionHosts\":\"required\"}"}` для принудительного Onion-роутинга
   - `{"corrId": "3", "cmd": "/connect"}` для создания одноразового инвайта
   - `{"corrId": "4", "cmd": "/c <invitation_link>"}` для подключения по QR-коду или ссылке
   - `{"corrId": "5", "cmd": "@<contact> <text>"}` для отправки E2EE сообщений и игровых пакетов `P2PGameProtocol`
   - `{"corrId": "6", "cmd": "/f @<contact> <file_path>"}` для отправки файлов и шардов ZK-Vault через XFTP `.onion` ноды.
4. Обрабатывай входящие JSON-события (`newChatItems`, `contactConnected`, `rcvFileDescrReady`) от `ws://127.0.0.1:5225` и пробрасывай их в `GameViewModel.onMessageReceived` и `handleTorP2PMessage`.
```

---

### 🔹 ПРОМПТ 3: Нативный L3 TUN-to-SOCKS5 маршрутизатор (`hev-socks5-tunnel`) для `FoxrayVpnService`

```text
Задача 3 (Нативный пакетный роутер TUN -> SOCKS5 для системного VPN):

В `FoxrayVpnService.kt` сейчас используется учебный парсер заголовков `Tun2SocksPacketRouter.kt`, который не содержит полного TCP/IP стека (lwIP) и не пересылает полезную нагрузку TCP/UDP в SOCKS5.

Выполни следующие шаги:
1. Подключи нативную библиотеку `hev-socks5-tunnel` (или `badvpn-tun2socks`) через NDK/CMake или готовый `.so` в `jniLibs`:
   - Создай JNI-обёртку `NativeTun2Socks.kt` с методами `startService(configPath: String, tunFd: Int)` и `stopService()`.
2. В `FoxrayVpnService.kt`:
   - В `Builder()` добавь маршрутизацию всего трафика `.addRoute("0.0.0.0", 0)` и исключи само приложение через `.addDisallowedApplication(packageName)` (чтобы сокеты Tor и Xray не зацикливались внутри TUN-интерфейса!).
   - После вызова `vpnInterface = builder.establish()` сгенерируй YAML-конфиг для `hev-socks5-tunnel`, указывающий на локальный SOCKS5-порт Xray (`127.0.0.1:10808`) или Tor (`127.0.0.1:9050`), и передай `vpnInterface!!.fd` в `NativeTun2Socks.startService(configFile.absolutePath, vpnInterface!!.fd)`.
3. При вызове `stopVpn()` корректно останавливай `NativeTun2Socks.stopService()` перед закрытием `vpnInterface`.
```

---

### 🔹 ПРОМПТ 4: Криптографический апгрейд до BouncyCastle PQC (FIPS 203 ML-KEM) и Curve25519

```text
Задача 4 (Подключение сертифицированного криптопровайдера BouncyCastle PQC):

1. Добавь в `gradle/libs.versions.toml` и `app/build.gradle.kts` зависимость:
   `implementation("org.bouncycastle:bcprov-jdk18on:1.79")`
2. В `HybridPostQuantumEngine.kt` добавь поддержку стандартного NIST FIPS 203 `ML-KEM-768` / `ML-KEM-1024` (`org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider` + `KeyGenerator.getInstance("ML-KEM", "BCPQC")`) в паре с `X25519` (`KeyAgreement.getInstance("X25519", "BC")`), сохранив существующий API класса.
3. В `DoubleRatchetVault.kt` переключи генерацию DH-ключей с `secp256r1` на `X25519` через провайдер `BC`.
4. Прогони unit-тесты `./gradlew testDebugUnitTest` и собери финальный релизный/бета APK `./gradlew assembleDebug`.
```

---

## 🧪 Чек-лист проверки готовой Бета-версии после сборки в Antigravity

1. **Запуск Tor + Onion Hidden Service**:
   - Откройте настройки сети в приложении и нажмите **«Синхронизировать V2Ray + Tor + SimpleX»**.
   - В логах должна появиться строка `Bootstrapped 100% (done)` от `libtor.so`, а в разделе P2P — сгенерированный 56-символьный адрес `<hash>.onion:8080`.
2. **Проверка SMP и XFTP узлов через `.onion`**:
   - Нажмите кнопку **«Тест»** напротив `smp://...@...onion:5223` и `xftp://...@...onion:443`.
   - Пинг должен возвращать реальное время отклика через цепочку `127.0.0.1:9050` (Tor SOCKS5), статус — `ONLINE`.
3. **Обмен сообщениями и P2P-партия в нарды с настоящим SimpleX Chat**:
   - Сгенерируйте QR-код / ссылку приглашения в секретном чате.
   - Отсканируйте её вторым телефоном (с вашим APK или официальным SimpleX Chat).
   - Отправьте текстовое сообщение, голосовое сообщение рации (6 сек) и запустите P2P-матч с криптографическим контролем костей `MentalDiceEngine` и сверкой `BOARD_HASH`!
