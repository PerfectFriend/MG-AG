# 🔐 ЗАДАЧА 04: ПОДКЛЮЧЕНИЕ СЕРТИФИЦИРОВАННОГО BOUNCYCASTLE PQC (FIPS 203 ML-KEM-1024)

## 🎯 ЦЕЛЬ
Интегрировать стандартизированную NIST реализацию постквантовой криптографии **FIPS 203 ML-KEM-1024** (Kyber-1024) и гибридный протокол с **X25519** на базе библиотеки BouncyCastle.

---

## 🔍 АНАЛИЗ
В проекте MadGammon в файле `HybridPostQuantumEngine.kt` уже работает математическая модель Ring-LWE в кольце $R_q = \mathbb{Z}_{3329}[X]/(X^{256}+1)$.
Для сертификации и совместимости с мировыми PQC стандартами (Signal PQXDH, Apple iMessage PQ3) необходимо подключить официальный провайдер BouncyCastle (`bcprov-jdk18on`), сохранив имеющийся Kotlin Ring-LWE алгоритм в качестве автономного резервного механизма.

---

## 🛠️ ШАГИ ДЛЯ HERMES AGENT

### Шаг 1. Добавление зависимостей в `app/build.gradle.kts`
Открой `app/build.gradle.kts` и добавь в блок `dependencies`:
```kotlin
dependencies {
    // ...
    // Bouncy Castle Post-Quantum Cryptography (FIPS 203 ML-KEM / Kyber & X25519)
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
}
```

---

### Шаг 2. Инициализация провайдера BouncyCastle
В `MadGammonApp.kt` (или в `HybridPostQuantumEngine.kt`) добавь безопасную регистрацию провайдера:
```kotlin
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

fun initializeSecurityProviders() {
    try {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        Log.i("Security", "BouncyCastle PQC provider registered successfully")
    } catch (e: Exception) {
        Log.w("Security", "Could not register BC provider, fallback to internal engine", e)
    }
}
```

---

### Шаг 3. Инкапсуляция и декапсуляция ML-KEM-1024
В `HybridPostQuantumEngine.kt` реализуй метод генерации общей тайны по спецификации FIPS 203:
```kotlin
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKeyGenerationParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKeyPairGenerator
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMGenerator
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMExtractor
import java.security.SecureRandom

class BouncyCastlePqcAdapter {
    private val random = SecureRandom()

    fun generateKeyPair(): KyberKeyPair {
        val keyGen = KyberKeyPairGenerator()
        keyGen.init(KyberKeyGenerationParameters(random, KyberParameters.kyber1024))
        val pair = keyGen.generateKeyPair()
        return KyberKeyPair(pair.public, pair.private)
    }

    fun encapsulateSecret(publicKey: Any): Pair<ByteArray, ByteArray> {
        val kemGen = KyberKEMGenerator(random)
        val secretWithEnc = kemGen.generateEncapsulated(publicKey as org.bouncycastle.crypto.CipherParameters)
        return Pair(secretWithEnc.secret, secretWithEnc.encapsulation)
    }

    fun decapsulateSecret(privateKey: Any, encapsulation: ByteArray): ByteArray {
        val kemExt = KyberKEMExtractor(privateKey as org.bouncycastle.crypto.CipherParameters)
        return kemExt.extractSecret(encapsulation)
    }
}
```

---

### Шаг 4. Комбинирование с эллиптической криптографией X25519 (HKDF)
Полученные 32 байта квантового секрета $S_{PQC}$ и 32 байта классического диффи-хеллмана $S_{Classic}$ смешиваются через имеющийся в `HybridPostQuantumEngine.kt` HKDF-SHA256:
$$K_{Hybrid} = \text{HKDF-Extract}(S_{Classic} \parallel S_{PQC}, \text{Salt})$$

---

### Шаг 5. Проверка компиляции
```bash
./gradlew compileDebugKotlin
```
Убедись, что все зависимости успешно скачались и проект компилируется. Переходи к задаче `05`.
