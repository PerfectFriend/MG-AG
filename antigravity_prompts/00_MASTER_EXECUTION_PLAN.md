# 🤖 ANTIGRAVITY AUTO-EXECUTION MASTER PLAN

Если ты — агент **Antigravity** (или локальный ИИ-инженер с доступом к Gradle, Android NDK, CMake и терминалу), выполни все задачи из этой папки **`/antigravity_prompts/`** строго по порядку номеров:

1. **`01_SELINUX_JNILIBS_TOR_XRAY.md`** — Обход блокировки Android 10–15 SELinux `W^X` (`EACCES: Permission denied`): упаковка нативных демонов `libtor.so` и `libxray.so` в `app/src/main/jniLibs/<abi>/`, включение `useLegacyPackaging = true` и запуск из `context.applicationInfo.nativeLibraryDir`.
2. **`02_SIMPLEX_CORE_JNI_WEBSOCKET.md`** — Интеграция официального бинарного ядра **SimpleX Chat Core (`libsimplex.so`)**: запуск локального WebSocket JSON-RPC сервера на `127.0.0.1:5225` через Tor SOCKS5 (`127.0.0.1:9050`) и подключение `SimpleXSmpClient.kt` + `SimpleXftpClient.kt` к реальным `.onion` узлам SMP и XFTP.
3. **`03_NATIVE_TUN2SOCKS_VPN_ROUTER.md`** — Замена учебного `Tun2SocksPacketRouter.kt` на нативный L3 TCP/IP стек **`hev-socks5-tunnel` (`libhev-socks5-tunnel.so`)** внутри `FoxrayVpnService.kt` с защитой сокетов приложения через `.addDisallowedApplication(packageName)`.
4. **`04_BOUNCYCASTLE_PQC_MLKEM_X25519.md`** — Подключение сертифицированного криптопровайдера **BouncyCastle PQC (`bcprov-jdk18on`)** для стандарта NIST FIPS 203 (`ML-KEM-1024`) и `X25519` в `HybridPostQuantumEngine.kt` и `DoubleRatchetVault.kt`.
5. **`05_VERIFICATION_AND_RELEASE_BUILD.md`** — Финальное тестирование unit-тестов (`./gradlew testDebugUnitTest`), проверка структуры нативных библиотек в APK и сборка готового к бою бета-релиза (`./gradlew assembleDebug`).

---

## ✅ Что уже готово на 100% (НЕ ЛОМАТЬ и сохранить совместимость API):
- **`ZeroKnowledgeVaultEngine.kt`**: Реальное помехоустойчивое кодирование Рида-Соломона 4-из-6 в поле Галуа $GF(2^8)$ с обращением матриц методом Гаусса-Жордана + `AES-256-GCM` + `PBKDF2-HMAC-SHA256`.
- **`SimpleXftpClient.kt`**: Шифрование чанков эфемерным 256-битным ключом `AES-256-GCM`, спулер осколков и отправка через SOCKS5.
- **`DoubleRatchetVault.kt`**: Полная реализация RFC 5869 HKDF-SHA256, буфер пропущенных ключей `skippedMessageKeys` (`MAX_SKIP = 256`) и AEAD AAD привязка заголовков.
- **`HybridPostQuantumEngine.kt`**: Полиномиальная арифметика на решётках Ring-LWE в кольце $R_q = \mathbb{Z}_{3329}[X]/(X^{256}+1)$ + гибридный HKDF-SHA256 + SAS эмодзи-верификация.
- **`SimpleXCallManager.kt`**: Пакетное шифрование аудиопотока `AES-256-GCM` с защитой от replay-атак и туннелированием `AUDIO_FRAME` поверх Tor Onion P2P TCP.
- **`AppDatabase.kt` (`MessengerVaultRepository`)**: Шифрованное хранение комнат, сообщений и контактов в Room SQLite (`AES-256-GCM`).
- **`CryptoBillingEngine.kt`**: Живая проверка транзакций в блокчейне через публичные RPC API Mempool.space (BTC), TronScan (USDT TRC-20) и Blockchair (Monero XMR).
- **`P2PGameProtocol.kt` + `MentalDiceEngine.kt`**: Криптографический протокол честной кости Commit-Reveal (HMAC-SHA256) и сверка хеша состояния доски `BOARD_HASH` после каждого хода.
