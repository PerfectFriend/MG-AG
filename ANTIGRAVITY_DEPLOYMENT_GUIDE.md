# Полное руководство по интеграции и развертыванию в Antigravity

Настоящее руководство предназначено для локальной сборки и запуска проекта в **Antigravity** (или локальном Android Studio) с полным стеком:
- **Tor Daemon** (встроенный сервис, SOCKS5 `127.0.0.1:9050` + Hidden Service v3)
- **Xray/V2Ray Core** (встроенный VPN/прокси, VLESS-Reality, Shadowsocks, Hysteria2, SOCKS5 `127.0.0.1:10808`)
- **SimpleX Chat SMP + XFTP** (подключение к любым серверам в клирнете и в `.onion` сети)
- **P2P Audio Calling & Mental Poker Dice Engine** (сквозное E2EE шифрование)

---

## ЧАСТЬ 1. ЧТО УЖЕ ОЧИЩЕНО В ЭТОМ РЕПОЗИТОРИИ

1. **Убран любой фейковый пинг**: В `FoxrayVpnManager.kt` удалена генерация случайного пинга (`15 + random.nextInt(45)`). Теперь тестируется настоящий TCP Socket handshake. Если сервер недоступен, возвращается честный `-1` (Offline).
2. **Убран фиктивный статус подключения**: Соединение переходит в статус `Connected` только при успешном сетевом отклике.
3. **Удален имитационный цикл из `FoxrayVpnService`**: Приложение больше не делает вид, что создало TUN интерфейс, если доступ к ОС не получен.
4. **Реализован честный DNS Bypass для .onion в `SimpleXSmpClient`**: OkHttp больше не пытается резолвить `.onion` через DNS провайдера, а делегирует разрешение удаленному Tor SOCKS5 прокси.
5. **Зачищены интерфейсные заглушки**: Убраны водяные знаки симуляции и затычки.

---

## ЧАСТЬ 2. БЫСТРЫЙ СТАРТ В ANTIGRAVITY (ТЕРМИНАЛ)

После распаковки архива в папку откройте терминал в корне проекта и выполните подготовку бинарников:

```bash
# 1. Создаем папку под архитектурные бинарники
mkdir -p app/src/main/assets/bin

# 2. Скачиваем официальный SimpleX Chat CLI (версия для Linux ARM64 и x86_64 под Android)
curl -L -o app/src/main/assets/bin/simplex-arm64 https://github.com/simplex-chat/simplex-chat/releases/download/v6.0.0/simplex-chat-linux-arm64
curl -L -o app/src/main/assets/bin/simplex-x86_64 https://github.com/simplex-chat/simplex-chat/releases/download/v6.0.0/simplex-chat-linux-x86_64

# 3. Скачиваем и распаковываем официальный Xray-core
curl -L -o /tmp/xray-arm64.zip https://github.com/XTLS/Xray-core/releases/download/v1.8.4/Xray-android-arm64-v8a.zip
unzip -p /tmp/xray-arm64.zip xray > app/src/main/assets/bin/xray-arm64

curl -L -o /tmp/xray-x86_64.zip https://github.com/XTLS/Xray-core/releases/download/v1.8.4/Xray-android-x86_64.zip
unzip -p /tmp/xray-x86_64.zip xray > app/src/main/assets/bin/xray-x86_64

# 4. Проверяем права
chmod +x app/src/main/assets/bin/*
```

---

## ЧАСТЬ 3. ПОСЛЕДОВАТЕЛЬНЫЕ ПРОМПТЫ ДЛЯ АГЕНТА В ANTIGRAVITY

Откройте проект в Antigravity и отправляйте следующие промпты строго по очереди:

### ПРОМПТ 1: Проверка окружения и зависимостей
```text
Привет! Мы открыли проект децентрализованного мессенджера и нардов на Android. 
Проверь файл app/build.gradle.kts и убедись, что:
1. Зависимость libs.tor.android.binary подключена.
2. В app/src/main/assets/bin/ лежат исполняемые файлы xray-arm64, simplex-arm64 (или запусти таску downloadBinaryAssets).
3. Проект успешно собирается командой: ./gradlew assembleDebug.
Отчитайся о результате сборки APK.
```

### ПРОМПТ 2: Автономный запуск Tor Daemon и Hidden Service
```text
В проекте есть класс TorEmbeddedController.kt. Сделай так, чтобы при старте приложения:
1. Автоматически распаковывался и запускался Tor из библиотеки org.torproject.android.binary.
2. Поднимался локальный SOCKS5 прокси на 127.0.0.1:9050.
3. Генерировался постоянный v3 .onion адрес в filesDir/tor_hs/hostname и отображался в UI в статусе сети.
4. Добавь в TorEmbeddedController обработку корректного перезапуска при смене сети (Wi-Fi <-> LTE).
```

### ПРОМПТ 3: Интеграция Xray Core и Anti-Censorship VLESS-Reality
```text
Проверь V2RayEmbeddedController.kt и AntiCensorshipConfigBuilder.kt:
1. Настрой автоматический запуск бинарника xray из private storage (context.filesDir) с пробросом входящего трафика на порт 127.0.0.1:10808.
2. Настрой цепочку: если включен Tor, весь исходящий трафик Xray заворачивается через SOCKS5 127.0.0.1:9050.
3. Обеспечь запуск в Foreground Service с persistent уведомлением, чтобы Android OS не выгружала процесс из памяти при выключенном экране.
```

### ПРОМПТ 4: Подключение SimpleX Chat (Клирнет + Onion)
```text
Настрой SimpleXSmpClient.kt и SimpleXEmbeddedController.kt на полноценную работу:
1. Сделай возможность подключения к официальным серверам SimpleX:
   - Clearnet: smp://smp10.simplex.im ... smp16.simplex.im
   - Onion: smp://6hzrpn2eicwql57ds7d5us4rsr6j62s3q56i2wf67k3xnhp55c2npkqd.onion
   - XFTP: xftp://s757c3272nkyoxx4g7y445v37v56a22g4y77f2y64f33k2l5j2w5fsqd.onion
2. При вызове createInvitationLink генерируй валидный URL формата https://simplex.chat/contact#/?smp=...
3. При вводе чужой ссылки парси её, отправляй команду CONN в SMP очередь через наш SOCKS5 клиент и сохраняй контакт в локальную базу Room.
4. Протестируй отправку тестового зашифрованного пакета между двумя клиентами.
```

### ПРОМПТ 5: Сквозное E2EE шифрование и P2P игра по Tor/Wi-Fi
```text
Свяжи DoubleRatchetVault.kt и TorP2PManager.kt с экраном игры:
1. При подключении через onion-адрес или локальный IP проведи DH-рассчет сессионного ключа Double Ratchet (ECDH P-256 + HMAC-SHA256).
2. Замени передачу ходов на зашифрованные пакеты фиксированного размера 16 КБ для защиты от анализа трафика.
3. Включи протокол честного броска костей MentalDiceEngine (Commit-Reveal HMAC) для исключения мошенничества обеих сторон.
4. Собери финальный релизный APK: ./gradlew assembleRelease.
```

---

## ЧАСТЬ 4. ПРОВЕРКА РАБОТОСПОСОБНОСТИ

После выполнения всех промптов в Antigravity:
1. Запустите приложение на реальном Android-устройстве или эмуляторе с архитектурой ARM64/x86_64.
2. В логах `Tor` появится строка `Bootstrapped 100%: Done`.
3. В статусе появится ваш уникальный `.onion` адрес.
4. В разделе SimpleX нажмите "Создать инвайт" — ссылка будет содержать ваш SMP сервер/очередь.
5. Любое другое устройство с SimpleX Chat сможет отсканировать QR-код или вставить ссылку и начать прямой зашифрованный диалог.
