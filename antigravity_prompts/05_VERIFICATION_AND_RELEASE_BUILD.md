# ЗАДАЧА 5: Финальная верификация, прогон тестов и сборка боевого Beta APK

После выполнения задач `01` — `04` выполни полную проверку проекта:

## Шаг 1. Написание и запуск Unit-тестов криптографии и сетевых протоколов
1. Проверь или создай unit-тест в `app/src/test/java/com/example/SovereignBetaVerificationTest.kt`, который тестирует:
   - **`ZeroKnowledgeVaultEngine`**: кодирование массива данных на 6 осколков (4 Data + 2 Parity в $GF(2^8)$), удаление осколков `#1` и `#3` (основные данные!) и 100% восстановление исходного файла через матрицу Гаусса-Жордана `reconstructFromShards` + проверку SHA-256.
   - **`DoubleRatchetVault`**: инициализацию сессии Алисы и Боба, отправку 5 сообщений, доставку их в обратном порядке (`msg #4`, затем `msg #2`, затем `msg #0`) и проверку, что `skippedMessageKeys` расшифровывает все пакеты без потери синхронизации.
   - **`HybridPostQuantumEngine`**: генерацию ключей на решётке Ring-LWE $R_q = \mathbb{Z}_{3329}[X]/(X^{256}+1)$, инкапсуляцию и декапсуляцию 256-битного секрета и совпадение SAS эмодзи-отпечатка.
   - **`SimpleXCallManager`**: шифрование PCM-аудиофрейма методом `encryptAudioFrame` (`AES-256-GCM`), успешную расшифровку `decryptAudioFrame` и отклонение повторно отправленного пакета (anti-replay).
   - **`P2PGameProtocol`**: детерминированное вычисление `computeBoardStateHash` для стартовой позиции длинных и коротких нард.
2. Запусти тесты командой:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Убедись, что все тесты проходят со статусом `PASSED`.

## Шаг 2. Сборка и проверка содержимого APK
1. Запусти сборку:
   ```bash
   ./gradlew assembleDebug
   ```
2. Проверь содержимое собранного APK (`app/build/outputs/apk/debug/app-debug.apk`):
   - Внутри `lib/arm64-v8a/` должны лежать:
     - `libtor.so`
     - `libxray.so`
     - `libsimplex.so`
     - `libhev-socks5-tunnel.so`
   - В `AndroidManifest.xml` (сгенерированном в APK) атрибут `android:extractNativeLibs` у `<application>` должен быть равен `"true"`.

## Шаг 3. Отчёт о готовности
Выведи краткий итоговый отчёт с путём к готовому файлу `app-debug.apk` и списком прошедших проверок.
