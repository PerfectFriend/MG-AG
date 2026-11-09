# Шеш-Беш (MadGammon) — Sovereign Crypto-Privacy Backgammon

<div align="center">
  <h3>Суверенный игровой комплекс и распределенная экосистема защищенной связи</h3>
  <p><b>Kotlin · Jetpack Compose · Rust (UniFFI / cargo-ndk) · Tor Onion · SimpleX · BouncyCastle</b></p>
</div>

---

## 🎲 Архитектурная концепция: Plausible Deniability

**MadGammon** спроектирован по принципу **правдоподобного отрицания (Plausible Deniability)**:

1. **Внешний слой (Фасад):**
   * Полноценная игра в нарды: **MAD** («Бешеные нарды» с каскадными кушами и передачей несыгранных зариков), **LONG** (классические длинные нарды), **SHORT** (короткие нарды с баром).
   * Процедурный синтез звуков зариков и фоновый стриминг интернет-радио (SomaFM).
   * Защита от кейлоггеров: встроенная клавиатура `MatrixKeyboard`, работающая напрямую с буфером приложения в обход системного ввода Android.
2. **Внутренний слой (Суверенный приватный контур):**
   * Активация скрытым криптографическим триггером (радиостанция *Radio Armageddon FM* или мастер-PIN).
   * Защищённый P2P-мессенджер на базе протоколов SimpleX (SMP/XFTP) и сквозного шифрования **Double Ratchet** (с фиксированным размером блока 16KB для защиты от DPI-анализа трафика).
   * Честный протокол броска костей без центрального сервера (**`MentalDiceEngine`** на базе HMAC-SHA256 Commit-Reveal).
   * Антицензурный стек туннелирования: Tor SOCKS5, V2Ray/Xray и нативный Tun2Socks роутер.
   * Аппаратная защита секретов через **`AndroidKeyStore`** (`AES-256-GCM`), исключающая хранение сидов и ключей в открытом виде.

---

## 🦀 Нативное Rust-ядро (`native/madgammon-core`)

Критические компоненты перенесены на **Rust** с гарантированным занулением структур в памяти (`ZeroizeOnDrop`) и бесшовными биндингами через **Mozilla UniFFI**:

* **`crypto::mental_dice`** — Provably fair commit-reveal алгоритм броска костей.
* **`crypto::double_ratchet`** — E2EE Double Ratchet с 16KB constant-size padding и AEAD `AES-256-GCM`.
* **`crypto::hybrid_pq`** — Гибридный KEM (`X25519`) с генерацией 4-эмодзи SAS-кодов для визуальной сверки абонентов.
* **`vault::erasure`** — Стирающее кодирование Рида-Соломона в поле Галуа $GF(2^8)$ (`reed-solomon-erasure`) с восстановлением из любых $K$ шардов.
* **`vault::mnemonic`** — Генератор и валидатор 12/24 слов BIP-39 и деривация seed через PBKDF2-HMAC-SHA512.
* **`engine::rules`** — Детерминированный движок нард трёх разновидностей с верификацией шестиблока и правила головы.
* **`net::tun2socks`** — Асинхронный захват и роутинг сетевых пакетов интерфейса `tun_fd` в локальный SOCKS5-прокси.
* **`net::simplex`** — Кодек и парсер команд протокола SimpleX SMP/XFTP.

---

## 🛠️ Сборка и запуск

### 1. Требования к окружению
* **Java:** OpenJDK 17
* **Android SDK:** Compile & Target SDK 36, NDK r27c
* **Rust:** Stable Toolchain с таргетами:
  ```bash
  rustup target add aarch64-linux-android x86_64-linux-android
  cargo install cargo-ndk
  cargo install uniffi-bindgen --version 0.28.0
  ```

### 2. Сборка нативного Rust ядра
```bash
chmod +x scripts/build_rust_core.sh
./scripts/build_rust_core.sh
```

### 3. Сборка Android APK
```bash
chmod +x gradlew
./gradlew assembleDebug
```

### 4. Запуск юнит-тестов
* **Тесты Rust ядра:**
  ```bash
  cd native/madgammon-core && cargo test
  ```
* **JVM тесты Android:**
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 📜 Документация проекта

* [**`docs/ACCEPTED_EXECUTION_PLAN.md`**](./docs/ACCEPTED_EXECUTION_PLAN.md) — Результаты аудита и закрытые задачи.
* [**`docs/RULES.md`**](./docs/RULES.md) — Официальный канонический свод правил нард (MAD, LONG, SHORT).
* [**`UBUNTU_LOCAL_DEV_SETUP.md`**](./UBUNTU_LOCAL_DEV_SETUP.md) — Пошаговая настройка headless Linux окружения.
