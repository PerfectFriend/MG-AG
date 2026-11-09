# ПОШАГОВЫЕ ПРОМПТЫ ДЛЯ АГЕНТА В ANTIGRAVITY (ИЛИ КУРСОРА / ANDROID STUDIO)

Данные промпты предназначены для последовательной подачи агенту после распаковки проекта в локальной папке Antigravity.
Подавайте их строго по одному, дожидаясь успешного выполнения предыдущего.

---

## ПОДГОТОВИТЕЛЬНЫЙ ЭТАП (ВЫПОЛНЯЕТСЯ В ТЕРМИНАЛЕ ПЕРЕД СТАРТОМ)

Перед запуском агента выполните в терминале папки проекта загрузку бинарников:

```bash
mkdir -p app/src/main/assets/bin

# Скачивание официального бинарника SimpleX Chat CLI
curl -L -o app/src/main/assets/bin/simplex-arm64 https://github.com/simplex-chat/simplex-chat/releases/download/v6.0.0/simplex-chat-linux-arm64
curl -L -o app/src/main/assets/bin/simplex-x86_64 https://github.com/simplex-chat/simplex-chat/releases/download/v6.0.0/simplex-chat-linux-x86_64

# Скачивание официального Xray-core для Android
curl -L -o /tmp/xray-arm64.zip https://github.com/XTLS/Xray-core/releases/download/v1.8.4/Xray-android-arm64-v8a.zip
unzip -p /tmp/xray-arm64.zip xray > app/src/main/assets/bin/xray-arm64

curl -L -o /tmp/xray-x86_64.zip https://github.com/XTLS/Xray-core/releases/download/v1.8.4/Xray-android-x86_64.zip
unzip -p /tmp/xray-x86_64.zip xray > app/src/main/assets/bin/xray-x86_64

chmod +x app/src/main/assets/bin/*
```

---

## ПРОМПТ 1: Проверка окружения, зависимостей и сборка базы

```text
Привет! Мы открыли проект децентрализованного мессенджера и нардов на Android в Antigravity.
Проверь файл app/build.gradle.kts и убедись, что:
1. Зависимость libs.tor.android.binary подключена и корректно резолвится.
2. В app/src/main/assets/bin/ присутствуют бинарники xray-arm64, xray-x86_64, simplex-arm64, simplex-x86_64. Если их нет, выполни команды скачивания в терминале.
3. Проект успешно компилируется и собирается командой: ./gradlew assembleDebug.
Отчитайся о результате сборки APK и наличии всех необходимых модулей.
```

---

## ПРОМПТ 2: Автономный запуск Tor Daemon и генерация Hidden Service v3

```text
В проекте есть класс com/example/data/TorEmbeddedController.kt. Сделай так, чтобы при старте приложения:
1. Автоматически распаковывался и запускался Tor из библиотеки org.torproject.android.binary (TorResourceInstaller).
2. Поднимался локальный SOCKS5 прокси на 127.0.0.1:9050 и ControlPort на 127.0.0.1:9051.
3. Активировался постоянный Hidden Service v3 в каталоге context.filesDir/tor_hs/ с маппингом порта (HiddenServicePort 8080 127.0.0.1:8080).
4. Генерируемый адрес из context.filesDir/tor_hs/hostname сохранялся в StateFlow и отображался в UI в статусе сети как «Ваш Onion-адрес».
5. Добавь обработку корректной перезагрузки демона при смене сетевого интерфейса (Wi-Fi <-> Мобильная сеть).
6. Проверь работоспособность запуском ./gradlew testDebugUnitTest.
```

---

## ПРОМПТ 3: Интеграция Xray Core и Anti-Censorship VLESS-Reality

```text
Проверь файлы V2RayEmbeddedController.kt, AntiCensorshipConfigBuilder.kt и FoxrayVpnService.kt:
1. Настрой распаковку бинарника xray из app/src/main/assets/bin в context.filesDir с назначением прав chmod 755.
2. Настрой автоматический запуск процесса ProcessBuilder(xrayPath, "run", "-c", configPath) с конфигурацией:
   - Входящий порт: SOCKS5 на 127.0.0.1:10808 и HTTP на 127.0.0.1:10809.
   - Исходящие протоколы: VLESS-Reality, Shadowsocks, а также маршрут через локальный Tor SOCKS5 (127.0.0.1:9050) для onion-доменов.
3. В FoxrayVpnService.kt при вызове builder.establish() передай дескриптор TUN в V2Ray/Tun2Socks, обеспечив полноценный захват системного трафика.
4. Обеспечь запуск сервиса как Foreground Service с persistent-уведомлением, чтобы Android OS не выгружала процесс при выключенном экране.
5. Проверь компиляцию: ./gradlew assembleDebug.
```

---

## ПРОМПТ 4: Полноценное подключение SimpleX Chat (Клирнет + Onion)

```text
Настрой SimpleXSmpClient.kt, SimpleXProtocol.kt и SimpleXEmbeddedController.kt на боевую работу с официальной сетью SimpleX:
1. Сделай возможность подключения через локальный Tor SOCKS5 прокси (127.0.0.1:9050) или прямой TLS к публичным серверам SimpleX:
   - Клирнет: smp://smp10.simplex.im, smp11.simplex.im, smp12.simplex.im
   - Onion: smp://6hzrpn2eicwql57ds7d5us4rsr6j62s3q56i2wf67k3xnhp55c2npkqd.onion
   - XFTP (передача файлов): xftp://s757c3272nkyoxx4g7y445v37v56a22g4y77f2y64f33k2l5j2w5fsqd.onion
2. Реализуй генерацию стандартных инвайтов формата: https://simplex.chat/contact#/?smp=... с QR-кодом для обмена между пользователями.
3. При вводе или сканировании ссылки инвайта собеседника парси URL, отправляй команду соединения в SMP-очередь через OkHttp WebSocket и сохраняй контакт в зашифрованную Room Database (SQLCipher).
4. Проверь отправку и прием тестового текстового сообщения между двумя инстансами.
```

---

## ПРОМПТ 5: Сквозное E2EE шифрование (Double Ratchet) и защита от анализа трафика

```text
Свяжи DoubleRatchetVault.kt и TorP2PManager.kt с экраном секретного чата и онлайн-игры:
1. При установке соединения двух устройств (по onion-адресу или SMP-каналу) выполни Диффи-Хеллман обмен ключами (ECDH secp256r1) и инициализируй цепочки ключей Double Ratchet с HMAC-SHA256 KDF.
2. Реализуй симметричное шифрование каждого сообщения алгоритмом AES-256-GCM с уникальным IV.
3. Включи фиксированное выравнивание пакетов до 16 КБ (padding случайными байтами) для защиты от анализа длины сообщений DPI-системами.
4. Подключи MentalDiceEngine.kt для игры в нарды через p2p-канал: валидация бросков костей через криптографическую схему Commit-Reveal (HMAC-SHA256).
5. Собери финальный релизный APK без отладочных логов: ./gradlew assembleRelease.
```

---

## ПРОМПТ 6 (ФИНАЛЬНЫЙ): Верификация и стресс-тестирование

```text
Проведи финальную проверку собранного приложения:
1. Запусти полный набор юнит-тестов: ./gradlew testDebugUnitTest.
2. Убедись, что отсутствуют предупреждения о жестко зашитых паролях и имитационных заглушках.
3. Проверь размер итогового APK и убедись, что нативные библиотеки (.so) и бинарники упакованы для архитектур arm64-v8a и x86_64.
4. Выведи инструкцию по установке APK через adb install.
```
