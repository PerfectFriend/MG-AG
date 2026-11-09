# 🪟 РУКОВОДСТВО ПО ПОРТИРОВАНИЮ СТЕКА MATRIXX / CRAZYGAMMON НА WINDOWS (DESKTOP)

Поскольку проект **MatrixX** (CRAZYGAMMON) написан на современном **Kotlin** и **Jetpack Compose**, его архитектура идеально подходит для миграции на десктопную версию для Windows с помощью **Compose Multiplatform (Jetpack Compose for Desktop)**.

Почти 90% сетевого кода — включая низкоуровневую работу с сокетами SOCKS5, P2P-сервер, многопоточность и парсинг игровых команд — написаны на **чистом Kotlin/JVM**. Это значит, что они будут работать на Windows вообще **без изменений**!

Ниже представлена пошаговая архитектурная инструкция для вашего разработчика (OpenCode), как собрать полноценный `.exe` клиент для Windows.

---

## 🏗️ 1. НАСТРОЙКА GRADLE ДЛЯ COMPOSE MULTIPLATFORM

Для превращения Android-проекта в кроссплатформенный необходимо добавить плагин Compose Multiplatform в ваш `build.gradle.kts`.

### Шаг 1. Подключение плагина в root `build.gradle.kts`
```kotlin
plugins {
    // Подключаем мультиплатформенный Compose
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.0" apply false
}
```

### Шаг 2. Конфигурация Desktop-модуля (или замена текущего)
В `app/build.gradle.kts` (или новом модуле `desktop`) настраивается цель JVM (Desktop):

```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm("desktop") {
        withJava()
    }
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                
                // Coroutines для P2P-потоков
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "CrazyGammon"
            packageVersion = "1.0.0"
            
            // Иконка для Windows (.ico)
            iconFile.set(project.file("launcher.ico"))
        }
    }
}
```

---

## 🔌 2. СОВМЕСТИМОСТЬ КОДА (ЧТО РАБОТАЕТ СРАЗУ)

Следующие модули используют стандартную библиотеку Java/Kotlin и **не требуют переписывания**:

1. **`TorProxyClient.kt`** — SOCKS5-хэндшейк на стандартных `java.net.Socket`. Будет работать на Windows идентично.
2. **`TorP2PManager.kt`** — `ServerSocket` для приема подключений и потоки чтения/записи (`TorP2PReaderThread`/`TorP2PWriterThread`). Работают на любой JVM-платформе.
3. **`GameViewModel` & Состояние игры** — Вся бизнес-логика игры, правила нард, генерация ходов и синхронизация кубиков полностью независимы от Android OS.
4. **UI Экраны** — Практически весь декларативный интерфейс (кнопки, игровая доска, чат, анимации) переносится на Desktop один-к-одному (заменяются только Android-специфичные импорты вроде `Modifier.testTag` или ресурсы).

---

## 🛠️ 3. АДАПТАЦИЯ ДЕМОНОВ ДЛЯ WINDOWS (ЧТО НАДО ЗАМЕНИТЬ)

Фоновые процессы (Tor, Xray, SimpleX CLI) на Android запускаются как Linux-бинарники. Для Windows вашему разработчику нужно сделать следующее:

### Шаг 1. Скачать EXE-версии демонов
1. **Tor**: Скачать официальный Windows Expert Bundle [с сайта Tor Project](https://www.torproject.org/download/tor/). Извлечь `tor.exe`.
2. **Xray (V2Ray)**: Скачать `Xray-windows-64.zip` из репозитория [XTLS/Xray-core](https://github.com/XTLS/Xray-core/releases). Извлечь `xray.exe`.
3. **SimpleX CLI**: Скачать `simplex-chat-windows-amd64.exe` из релизов [SimpleX Chat](https://github.com/simplex-chat/simplex-chat/releases). Переименовать в `simplex.exe`.

Поместите их в папку ресурсов вашего Desktop-проекта, например: `src/desktopMain/resources/bin/win64/`.

### Шаг 2. Адаптировать `TorEmbeddedController` под Windows
Вместо путей Android (где используется `context.filesDir`), на Windows нужно использовать системные временные папки или папку приложения:

```kotlin
// Пример адаптации запуска процесса на Windows (Kotlin JVM)
val appDataDir = File(System.getenv("APPDATA"), "CrazyGammon")
if (!appDataDir.exists()) appDataDir.mkdirs()

val torExe = extractBinary("bin/win64/tor.exe", appDataDir)
val torrc = File(appDataDir, "torrc").apply {
    writeText("""
        SocksPort 9050
        ControlPort 9051
        DataDirectory ${appDataDir.absolutePath}/tor_data
        HiddenServiceDir ${appDataDir.absolutePath}/tor_hs
        HiddenServicePort 80 127.0.0.1:8080
    """.trimIndent())
}

// Запуск процесса в Windows
val process = ProcessBuilder(torExe.absolutePath, "-f", torrc.absolutePath)
    .directory(appDataDir)
    .redirectErrorStream(true)
    .start()
```

---

## 📦 4. КОМАНДЫ СБОРКИ WINDOWS ВЕРСИИ

После переноса файлов в мультиплатформенный шаблон JetBrains Compose, сборка дистрибутива выполняется одной командой в терминале Windows:

* **Для тестирования (запуск в режиме разработки):**
  ```bash
  gradle run
  ```
* **Для сборки готового установщика (`.msi` или `.exe`):**
  ```bash
  gradle packageDistributionForCurrentOS
  ```
  *Готовый установщик появится в директории `build/compose/binaries/main/`.*

---

## 💡 РЕЗЮМЕ ДЛЯ РАЗРАБОТЧИКА
Кодовая база **MatrixX** уже спроектирована как **чистое P2P-решение**. Использование `ProcessBuilder` для запуска демонов и стандартных сокетов делает перенос приложения на Windows тривиальной задачей на 1-2 дня работы для любого Kotlin-разработчика. Все сетевые протоколы останутся полностью совместимыми — игрок на Windows сможет без проблем играть с игроком на Android напрямую по сети Tor!
