# ЗАДАЧА 4: Интеграция сертифицированного провайдера BouncyCastle PQC (NIST FIPS 203 ML-KEM + X25519)

## Контекст задачи
В проекте уже реализована рабочая полиномиальная арифметика на решётках Ring-LWE в `HybridPostQuantumEngine.kt` и полноценный Double Ratchet с HKDF-SHA256 и `skippedMessageKeys` в `DoubleRatchetVault.kt`.
Для соответствия финальному стандарту **NIST FIPS 203 (ML-KEM-1024 / Kyber)** и использования кривой **Curve25519 (`X25519`)** добавь криптопровайдер `BouncyCastle` (`bcprov-jdk18on`), сохранив обратную совместимость со всеми существующими методами и тестами.

---

## Инструкции к выполнению

### Шаг 1. Подключение зависимости `bcprov-jdk18on`
1. В `gradle/libs.versions.toml` и `app/build.gradle.kts` добавь зависимость:
   ```kotlin
   implementation("org.bouncycastle:bcprov-jdk18on:1.79")
   ```
2. В `app/build.gradle.kts` внутри `packaging { resources { excludes += ... } }` добавь исключение дублирующихся метаданных подписи BouncyCastle:
   ```kotlin
   excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
   excludes += "META-INF/BCKEY.DSA"
   excludes += "META-INF/BCKEY.SF"
   ```

### Шаг 2. Регистрация провайдера в `MyApplication.kt`
1. Открой `app/src/main/java/com/example/application/MyApplication.kt`.
2. В `onCreate()` зарегистрируй `BouncyCastleProvider` и `BouncyCastlePQCProvider`:
   ```kotlin
   import org.bouncycastle.jce.provider.BouncyCastleProvider
   import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
   import java.security.Security

   Security.removeProvider("BC")
   Security.addProvider(BouncyCastleProvider())
   if (Security.getProvider("BCPQC") == null) {
       Security.addProvider(BouncyCastlePQCProvider())
   }
   ```

### Шаг 3. Поддержка `X25519` в `DoubleRatchetVault.kt`
1. В `app/src/main/java/com/example/crypto/DoubleRatchetVault.kt` обнови `generateDhKeyPair()` и `dhAgree()` так, чтобы при наличии провайдера `"BC"` использовался алгоритм `"X25519"` (Curve25519), а при его отсутствии в JVM unit-тестах автоматически срабатывал существующий фоллбэк на `"EC"` (`secp256r1`) + `"ECDH"`.

### Шаг 4. Поддержка NIST FIPS 203 `ML-KEM` в `HybridPostQuantumEngine.kt`
1. В `app/src/main/java/com/example/crypto/HybridPostQuantumEngine.kt` оставь существующую Ring-LWE реализацию (`generateLatticeKeyPair`, `encapsulateLattice`, `decapsulateLattice`) и дополнительно добавь метод генерации и инкапсуляции через `ML-KEM-1024` (`org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec.kyber1024`), если провайдер `"BCPQC"` активен в рантайме.
