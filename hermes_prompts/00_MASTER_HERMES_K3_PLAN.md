# 🤖 HERMES AGENT (KIMI K3 / DEEPSEEK V4.1) MASTER EXECUTION PLAN

## 🎯 ПРЕДНАЗНАЧЕНИЕ
Данный план составлен специально для **Hermes Agent** с поддержкой продвинутых reasoning-моделей (**Kimi k3** или **DeepSeek v4.1 Flash**). Он переводит проект **MadGammon** из предрелизного состояния в полностью функциональную бета-версию с нативной изоляцией демонов Tor и Xray, ядром SimpleX Chat Core, L3 VPN-туннелем и сертифицированным криптографическим стеком BouncyCastle PQC (FIPS 203).

---

## 🛡️ СИСТЕМНЫЕ ИНВАРИАНТЫ ДЛЯ АГЕНТА (СТРОЖАЙШИЙ КОНТРАКТ)

1. **ЗАПРЕТ НА ДЕГРАДАЦИЮ КРИПТОГРАФИИ**:
   - Ни при каких обстоятельствах НЕ заменять математические алгоритмы на заглушки или симуляции.
   - В коде уже реализованы:
     * Кодирование Рида-Соломона 4-из-6 в поле Галуа $GF(2^8)$ с матрицами Вандермонда в `ZeroKnowledgeVaultEngine.kt`.
     * Полиномиальное умножение Ring-LWE в кольце $R_q = \mathbb{Z}_{3329}[X]/(X^{256}+1)$ в `HybridPostQuantumEngine.kt`.
     * Полный Double Ratchet с RFC 5869 HKDF-SHA256 и буфером пропущенных ключей в `DoubleRatchetVault.kt`.
     * Защищённое хранилище Room SQLite с шифрованием AES-256-GCM в `AppDatabase.kt`.
     * Протокол Commit-Reveal HMAC-SHA256 в `MentalDiceEngine.kt`.
   - **Все эти модули должны оставаться полностью функциональными.**

2. **ЗАПРЕТ НА СОКРАЩЕНИЯ В КОДЕ**:
   - Модели `Kimi k3` и `DeepSeek v4.1` должны выводить исходный код **целиком**, включая все `import`, аннотации и обработку исключений. Никаких `// ... остальной код без изменений` или `// TODO`.

3. **АВТОНОМНЫЙ КОНТРОЛЬ ТЕРМИНАЛА**:
   - Ты обладаешь доступом к терминалу. Запускай скрипты, инспектируй скомпилированные `.so` через `file` или `readelf`, проверяй компиляцию через `./gradlew compileDebugKotlin` и `./gradlew assembleDebug`.

---

## 📋 МАРШРУТ ВЫПОЛНЕНИЯ ЗАДАЧ

Выполняй задачи строго последовательно:

```
[01_SELINUX_AND_JNILIBS.md]
  │  Обход SELinux W^X, скачивание бинарников Tor/Xray, useLegacyPackaging
  ▼
[02_SIMPLEX_CHAT_CORE_WS.md]
  │  Запуск SimpleX Chat Core daemon, WebSocket JSON-RPC, маршрутизация SMP/XFTP через Tor SOCKS5
  ▼
[03_HEV_TUN2SOCKS_NDK.md]
  │  Сборка/линковка hev-socks5-tunnel C NDK, JNI-мост, FoxrayVpnService L3 туннель
  ▼
[04_BOUNCYCASTLE_MLKEM1024.md]
  │  Подключение org.bouncycastle:bcprov-jdk18on, стандарт FIPS 203 ML-KEM-1024 + X25519
  ▼
[05_VERIFICATION_BUILD_RELEASE.md]
  │  Запуск тестов ./gradlew testDebugUnitTest, валидация ELF в APK, финальный билд
```

---

## ⚡ БЫСТРЫЙ СТАРТ ДЛЯ АГЕНТА:
Открой `01_SELINUX_AND_JNILIBS.md` и начни с запуска `./scripts/fetch_native_binaries.sh` либо ручного скачивания библиотек.
