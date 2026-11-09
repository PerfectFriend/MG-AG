# ЗАДАЧА 1: Обход SELinux W^X на Android 10–15 и упаковка `libtor.so` и `libxray.so` в `jniLibs`

## Контекст проблемы
В Android при `targetSdk >= 29` (Android 10–15) политика безопасности SELinux (`W^X` — Write XOR Execute) запрещает вызов `execve()` для бинарных файлов, распакованных в доступные на запись директории приложения (`context.filesDir` или `context.getDir("bin")`), выбрасывая ошибку `java.io.IOException: Cannot run program ... error=13, Permission denied`.

Единственный разрешённый системой Android способ запуска нативных процессов (`ProcessBuilder`) — упаковать исполняемые ELF-файлы под видом нативных библиотек `lib*.so` в `app/src/main/jniLibs/<abi>/` и включить `useLegacyPackaging = true`. Тогда установщик Android (`PackageManager`) распакует их в системную директорию `context.applicationInfo.nativeLibraryDir` с SELinux-контекстом `apk_data_file` и правами `r-xp`.

---

## Инструкции к выполнению

### Шаг 1. Настройка `app/build.gradle.kts`
1. Открой `app/build.gradle.kts`.
2. Внутри блока `android { ... }` добавь (или обнови) блок `packaging`:
   ```kotlin
   packaging {
       jniLibs {
           useLegacyPackaging = true
       }
   }
   ```
3. Удали или отключи старую задачу `downloadBinaryAssets`, которая скачивала `xray` в `src/main/assets/bin`, чтобы сборка полагалась на `jniLibs`.

### Шаг 2. Добавление нативных бинарников в `jniLibs`
1. Создай директории:
   - `app/src/main/jniLibs/arm64-v8a/`
   - `app/src/main/jniLibs/x86_64/`
2. Скачай или собери актуальные релизные ELF-бинарники под Android (`arm64-v8a` и `x86_64`):
   - **Tor Daemon** (из `tor-android` / `Tor Expert Bundle` или GuardianProject `tor-android`):
     - Положи в `app/src/main/jniLibs/arm64-v8a/libtor.so`
     - Положи в `app/src/main/jniLibs/x86_64/libtor.so`
   - **Xray-core Daemon** (из `XTLS/Xray-core` под `android-arm64-v8a` и `android-amd64`):
     - Переименуй исполняемый файл `xray` в `libxray.so`
     - Положи в `app/src/main/jniLibs/arm64-v8a/libxray.so`
     - Положи в `app/src/main/jniLibs/x86_64/libxray.so`
3. Убедись, что файлы являются настоящими ELF-бинарниками (`file libtor.so` -> `ELF 64-bit LSB shared object / pie executable`).

### Шаг 3. Обновление `TorEmbeddedController.kt`
1. Открой `app/src/main/java/com/example/network/TorEmbeddedController.kt`.
2. В методе `startTor(...)` измени поиск бинарника Tor так, чтобы в первую очередь проверялся `nativeLibraryDir`:
   ```kotlin
   val nativeTor = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
   val fileTor: File? = if (nativeTor.exists() && nativeTor.canExecute()) {
       nativeTor
   } else {
       val torResourceInstaller = TorResourceInstaller(context, context.filesDir)
       torResourceInstaller.installResources()
   }
   ```
3. В список аргументов запуска `cmd` добавь:
   ```kotlin
   "--ControlPort", "127.0.0.1:9051",
   "--CookieAuthentication", "1"
   ```
4. Оставь чтение `hostname` из `hiddenServiceDir` для получения 56-символьного адреса `.onion` v3.

### Шаг 4. Обновление `V2RayEmbeddedController.kt`
1. Открой `app/src/main/java/com/example/network/V2RayEmbeddedController.kt`.
2. В методе `installBinary()` сначала проверяй наличие `File(context.applicationInfo.nativeLibraryDir, "libxray.so")`:
   ```kotlin
   val nativeXray = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
   if (nativeXray.exists() && nativeXray.canExecute()) {
       onStatusUpdate("Xray native binary ready in nativeLibraryDir (${nativeXray.length() / 1024} KB)")
       return nativeXray
   }
   ```
3. В методе `startXray(...)` запускай найденный файл (`nativeXray` или fallback) с параметрами `listOf(binFile.absolutePath, "run", "-c", configFile.absolutePath)`.

### Шаг 5. Проверка
Запусти `./gradlew assembleDebug` и проверь через `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "lib/"`, что `libtor.so` и `libxray.so` присутствуют в APK и не сжаты (либо корректно помечены для распаковки).
