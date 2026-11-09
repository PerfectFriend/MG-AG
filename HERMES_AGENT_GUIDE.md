# 🚀 РУКОВОДСТВО ПО ЗАПУСКУ HERMES AGENT ДЛЯ MADGAMMON

Это руководство предназначено для запуска и сквозного выполнения задач миграции и компиляции проекта **MadGammon** с помощью **Hermes Agent** на локальной машине (Linux, macOS или Windows WSL2).

---

## 🏆 1. СРАВНИТЕЛЬНЫЙ АНАЛИЗ И ВЫБОР МОДЕЛИ

У вас на выбор три модели: **Kimi k3**, **DeepSeek v4.1 Flash** и **GLM 5.3**.

| Критерий | **Kimi k3** (Moonshot) 🥇 | **DeepSeek v4.1 Flash** 🥈 | **GLM 5.3** (Zhipu) 🥉 |
| :--- | :--- | :--- | :--- |
| **Репозиторный контекст** | **До 2 000 000 токенов**, рекордный поиск по всей кодовой базе | 128k токенов (в Flash-режиме может терять ранние файлы) | 128k токенов |
| **Android / Kotlin / Gradle DSL** | **Идеально** понимает Kotlin DSL `build.gradle.kts` 8.x | Отлично, но иногда путает версии KSP/AGP | Средне, частые синтаксические огрехи |
| **Системный C / JNI / NDK** | Отлично генерирует JNI и CMakeLists | **Превосходно** (исторический конёк DeepSeek) | Хорошо |
| **Следование протоколам и правилам** | **Максимальная дисциплина**, никогда не удаляет существующий код | Высокая, но Flash-версия склонна сокращать код (`// ...`) | Средняя дисциплина |
| **Итоговая рекомендация** | **ТОП-1 (ОСНОВНОЙ ВЫБОР ДЛЯ HERMES)** | **ТОП-2 (АЛЬТЕРНАТИВА / СКОРОСТЬ)** | **ТОП-3** |

### 🎯 ВЕРДИКТ:
> **Используйте `Kimi k3` как основную модель для Hermes Agent.**
> 
> **Почему:** В проекте MadGammon реализованы сложнейшие криптографические подсистемы (кодирование Рида-Соломона 4-из-6 в поле Галуа $GF(2^8)$, решётчатая криптография Ring-LWE, Double Ratchet, база данных Room SQLite с AES-256-GCM). Kimi k3 держит в памяти весь репозиторий целиком, не галлюцинирует и **гарантированно не сломает рабочие модули**, аккуратно встроив нативные демоны Tor/Xray и L3 VPN.
>
> *(Если скорость для вас важнее всего или нужно точечно скомпилировать сложный C-код tun2socks — переключитесь на `DeepSeek v4.1 Flash`, наши промпты оптимизированы под обе модели!)*

---

## 📂 2. СТРУКТУРА ПАПКИ ПРОМПТОВ ДЛЯ АГЕНТА

Вся программа действий разбита на автономные модули в директории **`/hermes_prompts/`**:

1. **`00_MASTER_HERMES_K3_PLAN.md`** — Главный манифест и системные правила (Safety Invariants, Zero Mock Data, Terminal CLI).
2. **`01_SELINUX_AND_JNILIBS.md`** — Обход SELinux W^X (`useLegacyPackaging`), скрипт скачивания и линковка `libtor.so` и `libxray.so` в `jniLibs`.
3. **`02_SIMPLEX_CHAT_CORE_WS.md`** — Интеграция SimpleX Chat Core, запуск демона с Tor SOCKS5 проксированием и WebSocket JSON-RPC клиентом на Kotlin.
4. **`03_HEV_TUN2SOCKS_NDK.md`** — Сборка и линковка нативного L3 TCP/IP VPN стека `hev-socks5-tunnel` через CMake / JNI.
5. **`04_BOUNCYCASTLE_MLKEM1024.md`** — Подключение официального провайдера `bcprov-jdk18on` для NIST FIPS 203 (`ML-KEM-1024`) и гибридного `X25519`.
6. **`05_VERIFICATION_BUILD_RELEASE.md`** — Запуск unit-тестов, инспекция ELF-библиотек в APK и финальная сборка `assembleDebug`.

---

## 🛠️ 3. КАК ЗАПУСТИТЬ HERMES AGENT (ПОШАГОВО)

1. Распакуйте ZIP-архив проекта в отдельную папку на диске (например, `~/MadGammon` или `D:\MadGammon`).
2. Откройте терминал в этой папке.
3. Убедитесь, что у вас установлены:
   - **Java 17 или 21** (`java -version`)
   - **Android SDK & NDK** (переменная `$ANDROID_HOME` или `local.properties` с `sdk.dir`)
4. Запустите **Hermes Agent** с выбранной моделью (`Kimi k3` или `DeepSeek v4.1 Flash`).
5. Отправьте агенту следующий стартовый промпт:

---

### 💬 СТАРТОВЫЙ ПРОМПТ ДЛЯ HERMES AGENT:

```text
Привет! Ты выступаешь в роли Senior Android & Systems Security Engineer.
Перед тобой проект MadGammon — децентрализованный защищённый P2P-мессенджер с гибридной квантово-устойчивой криптографией, Tor Onion роутингом и нардной игрой.

Изучи корневую структуру проекта, прочитай файл /HERMES_AGENT_GUIDE.md и открой мастер-план /hermes_prompts/00_MASTER_HERMES_K3_PLAN.md.

Твоя задача — строго последовательно выполнить задачи из папки /hermes_prompts/:
1. /hermes_prompts/01_SELINUX_AND_JNILIBS.md
2. /hermes_prompts/02_SIMPLEX_CHAT_CORE_WS.md
3. /hermes_prompts/03_HEV_TUN2SOCKS_NDK.md
4. /hermes_prompts/04_BOUNCYCASTLE_MLKEM1024.md
5. /hermes_prompts/05_VERIFICATION_BUILD_RELEASE.md

Главные правила:
- НЕ УДАЛЯЙ и НЕ УПРОЩАЙ рабочий криптографический код (Reed-Solomon 4-of-6, Ring-LWE, Double Ratchet, Room AES-256-GCM, Zero-Knowledge Vault).
- Никаких заглушек или mock-данных — пиши полный, продакшн-код с обработкой ошибок.
- Используй терминал для запуска bash-скриптов и Gradle-сборки (./gradlew assembleDebug).
- После каждого шага проводи верификацию и отчитывайся о статусе.

Приступай к изучению репозитория и выполнению задачи 01!
```

---

## 🔒 4. ЧТО УЖЕ 100% РАБОТАЕТ В ПРОЕКТЕ (НЕ ТРОГАТЬ):
- 🧬 **`ZeroKnowledgeVaultEngine.kt`**: Кодирование Рида-Соломона 4-из-6 в поле Галуа $GF(2^8)$, матрицы Вандермонда, метод Гаусса-Жордана, шифрование осколков AES-256-GCM.
- 🔑 **`DoubleRatchetVault.kt`**: Стандарт RFC 5869 HKDF-SHA256, хранилище пропущенных ключей (`MAX_SKIP = 256`), AEAD AAD заголовки.
- 🛡️ **`HybridPostQuantumEngine.kt`**: Полиномиальное кольцо $R_q = \mathbb{Z}_{3329}[X]/(X^{256}+1)$, эллиптическая криптография, SAS-эмодзи верификация.
- 🎙️ **`SimpleXCallManager.kt`**: Защищённый аудиостриминг AES-256-GCM с защитой от replay-атак поверх Tor TCP.
- 📦 **`AppDatabase.kt` / `MessengerVaultRepository`**: Полная SQLite база на Room с прозрачным AES-256-GCM шифрованием.
- 🎲 **`MentalDiceEngine.kt` / `P2PGameProtocol.kt`**: Честный бросок костей по протоколу Commit-Reveal (HMAC-SHA256) и аудит доски `BOARD_HASH`.
- 💰 **`CryptoBillingEngine.kt`**: Реальные вызовы RPC блокчейнов BTC, USDT (TRC-20) и XMR (Monero).
