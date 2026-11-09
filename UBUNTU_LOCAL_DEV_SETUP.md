# ПОЛНОЕ РУКОВОДСТВО: НАСТРОЙКА ЛОКАЛЬНОГО ОКРУЖЕНИЯ UBUNTU ДЛЯ АВТОНОМНОЙ РАЗРАБОТКИ GEMINI

Данное руководство описывает пошаговый процесс развертывания полноценной среды разработки Android на локальном компьютере с **Ubuntu 22.04 / 24.04 LTS**, а также подключение и настройку автономного AI-агента **Gemini**, способного самостоятельно выполнять терминальные команды, компилировать нативный код (C/C++/Go/NDK), запускать эмулятор и реализовывать реальный функционал вместо заглушек.

---

## ЧАСТЬ 1. ПОДГОТОВКА СИСТЕМЫ И БАЗОВЫХ УТИЛИТ UBUNTU

Откройте терминал на вашей Ubuntu-машине и выполните обновление пакетов и установку базового системного инструментария:

```bash
sudo apt update && sudo apt upgrade -y

# Установка компиляторов, сборочных утилит и сетевых библиотек
sudo apt install -y \
    build-essential \
    curl \
    wget \
    git \
    unzip \
    zip \
    tar \
    pkg-config \
    cmake \
    ninja-build \
    python3 \
    python3-pip \
    python3-venv \
    jq \
    libssl-dev \
    libffi-dev \
    libusb-1.0-0 \
    qemu-kvm \
    libvirt-daemon-system \
    libvirt-clients \
    bridge-utils
```

### Настройка прав виртуализации KVM (для Android Emulator)
```bash
sudo adduser $USER kvm
sudo chown $USER /dev/kvm
```

---

## ЧАСТЬ 2. УСТАНОВКА JAVA (OPENJDK 17) И GO (ДЛЯ TUN2SOCKS / XRAY)

Android Gradle Plugin и NDK требуют Java 17, а компиляция сетевых модулей Xray/Tun2socks требует актуальной версии Go.

### 1. Установка OpenJDK 17
```bash
sudo apt install -y openjdk-17-jdk openjdk-17-jre
java -version
```

### 2. Установка современного компилятора Go (1.22+)
```bash
cd /tmp
wget https://go.dev/dl/go1.22.6.linux-amd64.tar.gz
sudo rm -rf /usr/local/go
sudo tar -C /usr/local -xzf go1.22.6.linux-amd64.tar.gz
rm go1.22.6.linux-amd64.tar.gz
```

### 3. Установка Rust, cargo-ndk и UniFFI (для нативного ядра MadGammon Core)
```bash
# 1. Установка официального тулчейна Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
source $HOME/.cargo/env

# 2. Добавление кросс-компиляционных таргетов Android NDK
rustup target add aarch64-linux-android x86_64-linux-android

# 3. Установка сборочных плагинов для NDK и генератора биндингов UniFFI
cargo install cargo-ndk
cargo install uniffi-bindgen --version 0.28.0

# 4. Сборка ядра libmadgammon_core.so и генерация Kotlin биндингов
./scripts/build_rust_core.sh
```

---

## ЧАСТЬ 3. УСТАНОВКА И НАСТРОЙКА ANDROID SDK, NDK И COMMAND-LINE TOOLS

Для полноценной работы агента без графического интерфейса устанавливается headless Android SDK:

### 1. Создание директории SDK
```bash
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
```

### 2. Загрузка Command-Line Tools
```bash
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip cmdline-tools.zip
mkdir -p latest
mv cmdline-tools/* latest/ 2>/dev/null || true
rm cmdline-tools.zip
```

### 3. Настройка переменных окружения (`~/.bashrc`)
Добавьте в конец файла `~/.bashrc`:
```bash
cat << 'EOF' >> ~/.bashrc

# Go Environment
export PATH=$PATH:/usr/local/go/bin
export GOPATH=$HOME/go
export PATH=$PATH:$GOPATH/bin

# Java Environment
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$PATH:$JAVA_HOME/bin

# Android SDK & NDK Environment
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator
EOF

source ~/.bashrc
```

### 4. Установка платформ, NDK и инструментов сборки через `sdkmanager`
Примите лицензии и установите необходимые компоненты:

```bash
# Принятие всех лицензий
yes | sdkmanager --licenses

# Установка Platform 36, 34, Build-Tools, CMake и NDK
sdkmanager "platform-tools" \
           "platforms;android-36" \
           "platforms;android-34" \
           "build-tools;36.0.0" \
           "build-tools;34.0.0" \
           "cmake;3.22.1" \
           "ndk;26.1.10909125"

# Установка образа эмулятора x86_64 для локального тестирования (опционально)
sdkmanager "system-images;android-34;google_apis;x86_64" "emulator"
```

---

## ЧАСТЬ 4. РАЗВЕРТЫВАНИЕ ПРОЕКТА MADGAMMON

Клонируйте или перенесите репозиторий с проектом:

```bash
cd ~
# Клонируйте проект или скопируйте папку
# git clone <URL_РЕПОЗИТОРИЯ> MadGammon
cd MadGammon

# Создание рабочего файла .env с вашим Gemini API ключом
cat << 'EOF' > .env
GEMINI_API_KEY=Ваш_Настоящий_API_Ключ_Google_Gemini
EOF

# Проверка первой компиляции через Gradle
chmod +x gradlew 2>/dev/null || true
gradle assembleDebug
```

---

## ЧАСТЬ 5. НАСТРОЙКА АВТОНОМНОГО AI-АГЕНТА С ПОЛНЫМ ДОСТУПОМ (GEMINI CLI AGENT)

Чтобы нейросеть Gemini могла **сама выполнять команды в Ubuntu-терминале, писать код, компилировать бинарники и запускать тесты**, используются CLI-агенты с поддержкой прямого исполнения shell-команд (Tool Use / Function Calling).

Наиболее мощные и проверенные решения для автономной разработки:

### ВАРИАНТ А. Aider (Рекомендуемый терминальный агент с поддержкой Gemini 1.5 Pro / Flash)

Aider — популярный агент с автономным доступом к git, терминалу и файлам.

1. **Установка Aider:**
```bash
pip install -U aider-chat
```

2. **Создание запускающего скрипта автономии (`run_agent.sh`):**
```bash
cat << 'EOF' > ~/run_gemini_agent.sh
#!/bin/bash
export GEMINI_API_KEY="ВАШ_КЛЮЧ_GEMINI_API"

cd ~/MadGammon

# Запуск агента в режиме полной автономии:
# --yes-always: разрешает агенту автоматически применять правки и выполнять команды без подтверждения
# --auto-commits: делает git-коммиты после каждого успешного шага
aider --model gemini/gemini-1.5-pro-latest \
      --auto-commits \
      --yes-always \
      --edit-format diff
EOF

chmod +x ~/run_gemini_agent.sh
```

---

### ВАРИАНТ Б. Goose (Open-source автономный агент от Block)

Goose умеет запускать bash-команды на хосте, управлять файлами и собирать проекты.

1. **Установка Goose:**
```bash
curl -fsSL https://github.com/block/goose/releases/download/stable/download_cli.sh | bash
```

2. **Настройка провайдера Gemini:**
```bash
goose configure
# Выберите провайдер: Google Gemini
# Введите ваш GEMINI_API_KEY
```

3. **Запуск агента в директории проекта:**
```bash
cd ~/MadGammon
goose session
```

---

## ЧАСТЬ 6. ТЕХНИЧЕСКИЕ ИНСТРУКЦИИ ДЛЯ GEMINI ПО РЕАЛИЗАЦИИ ИМИТАЦИЙ

После запуска агента отправьте ему следующий структурированный промпт. В нем даны конкретные инструкции по замене каждого симулированного блока на реальную имплементацию.

### Задачи для автономного агента:

#### 1. Реализация реального сетевого TUN2SOCKS (вместо заглушки в `FoxrayVpnService`)
* **Что сделать:** 
  Использовать библиотеку **`xjasonlyu/tun2socks`** на Go. Скомпилировать ее под Android (arm64-v8a и x86_64) через NDK / `go build` в виде разделяемой библиотеки `libtun2socks.so` или автономного бинарного файла.
* **Интеграция:** 
  В `FoxrayVpnService.kt` взять системный файловый дескриптор `vpnInterface.fd` и передать его в `tun2socks` с флагом `--tun-fd <FD> --proxy socks5://127.0.0.1:10808`. Это обеспечит 100% перехват всего интернет-трафика системы в ядро Xray.

#### 2. Реальные метрики трафика VPN (вместо случайных чисел в `FoxrayVpnManager`)
* **Что сделать:** 
  Заменить `startStatsSimulation()` на вызовы системного Android API `android.net.TrafficStats`:
  ```kotlin
  val rxBytes = TrafficStats.getTotalRxBytes()
  val txBytes = TrafficStats.getTotalTxBytes()
  ```
  Или считывать реальную статистику переданных байт прямо из встроенного gRPC/API порта Xray-core (`127.0.0.1:10085 StatsService`).

#### 3. Реальный матчмейкинг SimpleX / WebSocket
* **Что сделать:** 
  * Развернуть в проекте легковесный сервер ретрансляции на Go или Node.js (`server/relay.js`), умеющий работать поверх WebSocket или Tor Onion.
  * Заменить псевдо-подключение `SimpleX_Peer_***` в `GameViewModel.kt` на подключение к этому релею, обеспечивая реальный обмен ходами между двумя смартфонами.

#### 4. Реальное офлайн-распознавание речи в Walkie-Talkie (вместо заглушки текста)
* **Что сделать:** 
  Подключить легкую офлайн-библиотеку **Vosk Android SDK** (`org.alphacephei:vosk-android:0.3.47`) с компактной моделью `vosk-model-small-ru` (около 40 МБ). Голосовые сообщения рации будут автоматически и конфиденциально транскрибироваться в текст на самом устройстве без отправки в облако.

---

## ЧАСТЬ 7. КОНТРОЛЬНЫЙ ЧЕК-ЛИСТ ГОТОВНОСТИ К РЕЛИЗУ

Выполните в терминале Ubuntu перед финальной сборкой релизного APK:

1. **Проверка всех бинарников NDK:**
   ```bash
   file app/src/main/assets/bin/*
   ```
2. **Проверка тестов:**
   ```bash
   gradle testDebugUnitTest
   ```
3. **Сборка релизного неподписанного APK:**
   ```bash
   gradle assembleRelease
   ```
4. **Проверка установленных инструментов:**
   ```bash
   adb devices
   ```
   *(Подключите Android-смартфон по USB с включенной отладкой или запустите эмулятор `emulator -avd test_device`, чтобы Gemini мог автоматически устанавливать и проверять APK через `adb install app/build/outputs/apk/debug/app-debug.apk`).*
