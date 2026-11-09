# 📦 ЗАДАЧА 01: ОБХОД SELINUX W^X И УПАКОВКА `libtor.so` И `libxray.so` В JNILIBS

## 🎯 ЦЕЛЬ
Обеспечить гарантированный запуск нативных процессов Tor и Xray-core на Android 10–15 (API 29–35) в соответствии с правилами SELinux `W^X` (Write XOR Execute).

---

## 🔍 АНАЛИЗ ПРОБЛЕМЫ
На Android с `targetSdk >= 29` вызов `Runtime.getRuntime().exec(...)` или `ProcessBuilder` для исполняемых файлов из `context.filesDir` блокируется ядром Linux с ошибкой:
`java.io.IOException: Cannot run program ... error=13, Permission denied`.

Единственный способ легального выполнения — размещение ELF-бинарников в директории нативных библиотек приложения:
`${context.applicationInfo.nativeLibraryDir}/lib<name>.so`.
При установке APK Android распаковывает библиотеки с контекстом `apk_data_file` и монтирует их с флагом исполнения (`r-xp`).

---

## 🛠️ ШАГИ ДЛЯ HERMES AGENT

### Шаг 1. Загрузка или сборка нативных ELF-бинарников
Выполни в терминале скрипт:
```bash
chmod +x ./scripts/fetch_native_binaries.sh
./scripts/fetch_native_binaries.sh
```
Если сеть недоступна, помести заранее подготовленные исполняемые файлы:
- `app/src/main/jniLibs/arm64-v8a/libtor.so`
- `app/src/main/jniLibs/arm64-v8a/libxray.so`
- `app/src/main/jniLibs/x86_64/libtor.so`
- `app/src/main/jniLibs/x86_64/libxray.so`

Проверь тип файлов через терминал:
```bash
file app/src/main/jniLibs/arm64-v8a/libtor.so
file app/src/main/jniLibs/arm64-v8a/libxray.so
```
Они должны определяться как `ELF 64-bit LSB pie executable` или `shared object`.

---

### Шаг 2. Настройка `app/build.gradle.kts`
Открой `app/build.gradle.kts` и убедись, что блок `packaging` содержит `useLegacyPackaging = true`:
```kotlin
android {
    // ...
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
```
Это предотвращает упаковку библиотек в несжатом виде (`extractNativeLibs = false`), заставляя Android распаковывать их в `nativeLibraryDir`.

---

### Шаг 3. Патч `TorEmbeddedController.kt`
Файл: `app/src/main/java/com/example/network/TorEmbeddedController.kt`

В методе `startTor(...)` реализуй приоритетный поиск бинарника из `nativeLibraryDir`:
```kotlin
val nativeTor = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
val binaryToRun = if (nativeTor.exists() && nativeTor.canExecute()) {
    nativeTor.absolutePath
} else {
    // Резервный поиск из внутренней директории
    val fallbackTor = File(context.filesDir, "tor/tor")
    if (fallbackTor.exists() && fallbackTor.canExecute()) fallbackTor.absolutePath else "tor"
}

val cmd = listOf(
    binaryToRun,
    "--DataDirectory", torDataDir.absolutePath,
    "--SocksPort", "127.0.0.1:$torSocksPort",
    "--ControlPort", "127.0.0.1:9051",
    "--CookieAuthentication", "1",
    "--HiddenServiceDir", hiddenServiceDir.absolutePath,
    "--HiddenServicePort", "$torHiddenPort 127.0.0.1:$torHiddenPort"
)

val processBuilder = ProcessBuilder(cmd)
processBuilder.directory(torDataDir)
processBuilder.redirectErrorStream(true)
val process = processBuilder.start()
```

---

### Шаг 4. Патч `V2RayEmbeddedController.kt`
Файл: `app/src/main/java/com/example/network/V2RayEmbeddedController.kt`

В методе проверки/установки бинарника Xray:
```kotlin
val nativeXray = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
val binaryToRun = if (nativeXray.exists() && nativeXray.canExecute()) {
    nativeXray.absolutePath
} else {
    val fallbackXray = File(context.getDir("bin", Context.MODE_PRIVATE), "xray")
    if (fallbackXray.exists()) fallbackXray.absolutePath else null
}

if (binaryToRun == null) {
    Log.w("V2RayEmbeddedController", "libxray.so not found in nativeLibraryDir")
}
```

---

### Шаг 5. Верификация компиляции
Запусти в терминале:
```bash
./gradlew compileDebugKotlin
```
Убедись, что компиляция проходит успешно без синтаксических ошибок. Переходи к задаче `02`.
