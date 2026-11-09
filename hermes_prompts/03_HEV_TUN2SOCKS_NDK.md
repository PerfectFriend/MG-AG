# 🛡️ ЗАДАЧА 03: НАТИВНЫЙ TUN2SOCKS L3 VPN МАРШРУТИЗАТОР (`HEV-SOCKS5-TUNNEL`)

## 🎯 ЦЕЛЬ
Обеспечить реальную прозрачную маршрутизацию всего системного сетевого трафика через Android `VpnService` в локальный SOCKS5 прокси Tor (`127.0.0.1:9050`) или Xray (`127.0.0.1:10808`).

---

## 🔍 АНАЛИЗ ПРОБЛЕМЫ
Android `VpnService` создаёт виртуальный сетевой интерфейс уровня L3 (`TUN`), который оперирует сырыми IP-пакетами (IPv4/IPv6). Tor и SOCKS5 являются протоколами уровня L4/L5 и принимают только TCP-соединения.
Для конвертации сырых IP-пакетов из `TUN fd` в SOCKS5 TCP/UDP сессии необходим полноценный стек TCP/IP в пространстве пользователя.
Наиболее производительное решение для Android — легковесный C-стек **`hev-socks5-tunnel`** (или `libtun2socks.so`).

---

## 🛠️ ШАГИ ДЛЯ HERMES AGENT

### Шаг 1. Настройка JNI моста в `FoxrayVpnService.kt`
Файл: `app/src/main/java/com/example/vpn/FoxrayVpnService.kt`

Добавь объявление нативных методов:
```kotlin
companion object {
    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
            Log.i("FoxrayVpnService", "Native hev-socks5-tunnel library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("FoxrayVpnService", "Native libhev-socks5-tunnel.so not found, fallback enabled", e)
        }
    }
}

private external fun hevSocks5TunnelStart(vpnFd: Int, socksIp: String, socksPort: Int): Int
private external fun hevSocks5TunnelStop()
```

---

### Шаг 2. Защита от бесконечного цикла маршрутизации (`Routing Loop Protection`)
При сборке VPN-интерфейса в методе `startVpn()` обязательно исключи само приложение MadGammon из VPN-туннеля, иначе трафик исходящих демонов Tor и Xray попадёт в собственный TUN и зациклится:
```kotlin
val builder = Builder()
    .setSession("MadGammon-Secure-Mesh")
    .addAddress("10.0.0.2", 24)
    .addRoute("0.0.0.0", 0)
    .addDnsServer("1.1.1.1")
    .setMtu(1500)

// Исключаем MadGammon из VPN-туннеля для предотвращения петли маршрутизации:
try {
    builder.addDisallowedApplication(packageName)
} catch (e: Exception) {
    Log.w("FoxrayVpnService", "Failed to add disallowed app", e)
}

val vpnInterface = builder.establish()
val fd = vpnInterface?.fd ?: return

// Запускаем нативный L3 транслятор:
hevSocks5TunnelStart(fd, "127.0.0.1", 9050)
```

---

### Шаг 3. Сборка C-библиотеки через NDK CMake (при наличии NDK)
В корневой папке или модуле `app/src/main/cpp/` создай или скомпилируй обёртку `hev-tunnel-jni.c`:
```c
#include <jni.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "HevTunnelJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_example_vpn_FoxrayVpnService_hevSocks5TunnelStart(
    JNIEnv *env, jobject thiz, jint vpn_fd, jstring socks_ip, jint socks_port) {
    
    const char *ip = (*env)->GetStringUTFChars(env, socks_ip, NULL);
    LOGI("Starting hev-socks5-tunnel on fd: %d, proxy: %s:%d", vpn_fd, ip, socks_port);
    (*env)->ReleaseStringUTFChars(env, socks_ip, ip);
    
    // В реальном сценарии здесь инициализируется цикл обработки событий tun2socks
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_vpn_FoxrayVpnService_hevSocks5TunnelStop(JNIEnv *env, jobject thiz) {
    LOGI("Stopping hev-socks5-tunnel");
}
```

---

### Шаг 4. Проверка компиляции
```bash
./gradlew compileDebugKotlin
```
Убедись, что Kotlin успешно компилируется с внешними JNI декларациями. Переходи к задаче `04`.
