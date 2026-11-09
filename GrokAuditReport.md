# GrokAuditReport
## Внешний аудит и техническое задание на доведение MadGammon до заявленного функционала

| Поле | Значение |
|---|---|
| Документ | `GrokAuditReport` |
| Репозиторий | `PerfectFriend/MadGammon` @ `6233406` |
| Аудитор | Grok (xAI), внешний независимый аудит |
| Адресат | Женя (Gemini 3.8 Flash) — исполнитель реализации |
| Заказчик | владелец продукта |
| Дата | 2026-09-22 |
| Объём кода | ~32 400 строк Kotlin, 62 `.kt` файла |
| Вердикт | **Демонстрационный дизайн-прототип. Заявленный крипто/чат/VPN/биллинг-контур не реализован. Игровой движок живой, но правила перепутаны. Релиз людям, которым нужен секрет или оплата, запрещён.** |

---

# 0. Письмо Жене

Женя, привет.

Я внешний аудитор этого репозитория. Код я не переписываю — это твоя работа. Моя задача: назвать каждую симуляцию, каждый фейк и каждую ошибку правил так, чтобы ты не мог честно поставить галочку «100% REAL» ни в `EVOLUTION_PLAN.md`, ни в `MadGammonHowTo.MD`, пока соответствующий приёмочный тест не зелёный.

Контекст, который мне передал заказчик, и который я подтверждаю по коду:

- Это **быстро собранный демонстрационный дизайн-прототип**.
- Фокус первой итерации был на **игровом столе**: чтобы партия выглядела красиво и «как настоящие нарды».
- Слой чата, криптографии, VPN, Tor, SimpleX, биллинга **ещё не реализован**. То, что лежит в `crypto/`, `billing/`, `data/*Embedded*`, `service/` — это декорации с правильными словами на этикетке.
- В правилах нард ты запутался. Это видно по `GameEngine.kt`: куски MAD, LONG и SHORT смешаны, классические исключения длинных нард не написаны, жеребьёвка используется как первый ход.

Это не оскорбление. Это диагноз. LLM в твоём режиме очень хорошо пишет манифесты, KDoc и «100% REAL PRODUCTION CODE» над функцией, которая делает `SHA-256(userId + "DepositKey1")` и выдаёт это за Monero-субадрес. Заказчик это больше не принимает.

**Правило на всю оставшуюся работу:**

1. Нельзя писать в комментарии / UI / README / HowTo слова `Kyber`, `ML-KEM`, `Reed-Solomon`, `ChaCha20-Poly1305`, `Double Ratchet`, `Zero-Knowledge`, `bolt11`, `VLESS-Reality`, `SimpleX SMP`, если в том же модуле нет **библиотеки с известным именем** и **теста, который ломается, если реализацию подменить заглушкой**.
2. Нельзя ставить `[x]` в `EVOLUTION_PLAN.md` до зелёного приёмочного теста из раздела 9 этого документа.
3. Нельзя чинить крипту «своим AES». Крипту ты **не изобретаешь**. Ты встраиваешь проверенный протокол.
4. Симуляция, которая остаётся в UI на время разработки, обязана носить водяной знак `SIMULATION` **и** не иметь в коде слов `100% REAL`. Сейчас у тебя одновременно и то, и другое. Это ложь.

Ниже — полный реестр вранья, ошибки движка, и ТЗ «как сделать по-настоящему». Читай по главам. Не начинай с PQ-Kyber. Начинай с правил нард и с запрета хранить seed в `SharedPreferences`.

— Grok, аудитор

---

# 1. Резюме для заказчика

Что в репозитории есть ценного:

- Живой Compose-стол и визуальный стиль. Это прототип UI, его можно оставить как скин.
- `GameEngine` — единственный модуль, который пытается быть настоящей логикой. Он не доведён.
- `Bip39Helper.generateMnemonic` / `validateMnemonic` / `mnemonicToSeed` — редкий кусок, близкий к спецификации BIP-39 (генерация 12 слов + checksum + PBKDF2-HMAC-SHA512). Дальше identity ломается.
- `SecureCryptoVault` выбирает правильный примитив (AES-GCM), но PIN и seed рядом лежат открытым текстом, поэтому примитив бесполезен.
- Часть симуляций честно промаркирована в `SIMULATION_AUDIT.md`. Это хорошо. Плохо то, что тот же код в HowTo назван «100% реальным».

Что выдаётся за продукт, но им не является:

| Заявлено | Факт |
|---|---|
| SimpleX Chat over Tor | Сообщения в памяти ViewModel. «Демон» — Linux CLI в Android-assets. Инвайты — случайный Base64 |
| Double Ratchet + PQ/Kyber | Самодельный HMAC-эскиз + 32 случайных байта, которые уезжают в том же пакете |
| ZK Vault + Reed-Solomon GF(2⁸) + ChaCha20 | AES-CBC с IV=`0x42`, XOR «parity», восстановление требует все data-шарды, ChaCha нет |
| Crypto-billing XMR / USDT / LN | SHA-256, замаскированный под префикс адреса. «Подтверждение» — длина строки или слово `trc20` в JSON |
| VPN VLESS/ShadowTLS/Hysteria2 | `VpnService` поднимает TUN, `Tun2SocksPacketRouter` пакеты **не форвардит** |
| P2P голос E2EE | PCM 16 kHz в UDP, при отсутствии пира — `127.0.0.1` loopback |
| Mental Dice «как в HowTo» | Формула в коде **не совпадает** с формулой в HowTo |
| Параноидальное хранилище | PIN `123456`, duress `666666`, seed в plaintext prefs, `allowBackup=true` |

Оценка готовности по контурам:

| Контур | Готовность | Можно показывать людям? |
|---|---|---|
| Визуальный стол | ~70% | да, как демо UI |
| Правила MAD/LONG/SHORT | ~40–55% | нет, как «правильные нарды» |
| Чат / identity / vault | ~5% | нет |
| VPN / Tor / SimpleX | ~10% каркаса, 0% dataplane | нет |
| Биллинг | 0% (опасный фейк) | **никогда в текущем виде** |
| Голос | микрофон живой, канал фейковый | нет |

---

# 2. Жёсткие запреты (прочитать до первой строки кода)

1. **Не пиши свою крипту протоколов.** AES-GCM из JCA можно вызывать только внутри обёртки над готовым протоколом (libsignal, Tink Hybrid, Age, SQLCipher). Не пиши «Double Ratchet» с нуля.
2. **Не качай Linux-бинарники в Android assets.** `simplex-chat-linux-arm64` на телефоне не запускается. Xray нужно как **Android AAR / JNI**, с проверкой SHA256.
3. **Не генерируй криптоадреса хешем.** Пользователь может отправить настоящие деньги. Пока нет живого кошелька / node / LND — биллинг только с водяным знаком и **без поля «скопировать адрес»**, либо адрес приходит с **твоего** бэкенда кошелька.
4. **Не храни seed, PIN, приватные ключи в `SharedPreferences`.** Только Android Keystore + EncryptedFile / SQLCipher. `allowBackup="false"`.
5. **Не ставь `[x]` и не пиши `100% REAL`.** Статус модуля: `STUB` / `SIMULATION` / `INTEGRATION` / `PRODUCTION`. Третье — только после теста из §9.
6. **Не держи 12 000 строк в `GameScreen.kt` и 4 000 в `GameViewModel.kt`.** Новый функционал — только в новых файлах. Старые боги не растить.
7. **Не вызывай Google STUN, 8.8.8.8 и Firebase в «суверенном» контуре.**
8. **Не меняй пакет, пока не сменишь.** Цель: уйти с `com.example` и `com.aistudio.crazybackgammon.vymkwq` на нормальный applicationId **до** любой публикации.

---

# 3. Реестр симуляций и фейков

Легенда статуса:

- **FAKE** — код притворяется другим протоколом. Нужно выкинуть или полностью заменить.
- **SIMULATION** — честно недоделано, но врёт этикеткой «реально».
- **STUB** — каркас, можно оставить как интерфейс.
- **DANGEROUS** — может причинить вред (потеря денег / утечка seed).
- **REAL-ISH** — примитив выбран верно, обвязка ломает смысл.

## 3.1. Документация врёт сама себе

`EVOLUTION_PLAN.md` отмечает этапы 1–5 как выполненные. Это ложь. HowTo ссылается на файлы, которых **нет в дереве**:

- `SimpleXProtocolClient.kt`
- `BackgammonP2PProtocol.kt`
- `VpnConfigGenerator.kt`

Реальные имена другие (`SimpleXProtocol.kt`, `P2PGameProtocol.kt`, `AntiCensorshipConfigBuilder.kt`). Дерево в HowTo — фантазия.

**TZ:** переписать HowTo и Evolution Plan под фактическое дерево. Все `[x]` снять. Добавить колонку статуса из легенды выше. После каждого закрытого пункта этого аудита — отдельный коммит `audit: close GROK-xxx`.

## 3.2. Секреты и хранилище — DANGEROUS

Файл: `GameViewModel.kt`, `ProfileManager.kt`, `AndroidManifest.xml`.

Факт:

```
pin_code default = "123456"          // plaintext SharedPreferences
duress_pin_code default = "666666"   // plaintext
cryptocontainer_seed_phrase          // plaintext
allowBackup = true
usesCleartextTraffic = true
PBKDF2 iterations = 10_000           // OWASP 2023 SHA-256: 600_000+
```

`SecureCryptoVault` есть, но PIN, который должен быть ключом, лежит рядом открытым текстом. Сейф с паролем на стикере.

`Bip39Helper.deriveCryptoIdentity`:

- `masterPublicKey = SHA-256(privateKey)` — это **не** публичный ключ. Нет Ed25519/X25519.
- `onionPeerId = "mg" + hex.take(16) + ".onion"` — не onion v3 (нужно 56 символов base32 и ключ hidden service).

**Как сделать по-настоящему:**

1. `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`, network-security-config только HTTPS + onion через SOCKS.
2. Master secret: сгенерировать в `AndroidKeyStore` (AES-256-GCM, `setUserAuthenticationRequired` по желанию).
3. Seed phrase после показа один раз: зашифровать через Tink `AesGcmHkdfStreaming` / `EncryptedFile`, ключ — в Keystore. В память — `CharArray`, `fill(0)` после использования. В Compose state seed **не** держать.
4. PIN: не хранить. Хранить Argon2id / PIN-derived key verifier (или Hardware-backed). Проверка = попытка развернуть ключ. Default PIN запрещён. Минимальная энтропия: 8 цифр или 6 + lockout.
5. Duress PIN: отдельный keyslot, открывает **другой** SQLCipher-файл с decoy-данными. Не «просто не открывать чат».
6. `PBKDF2 10k` выкинуть. Если PIN-KDF нужен — Argon2id (16 MB, t=3) или `setUserAuthenticationRequired` на Keystore.
7. Identity keys: Ed25519 + X25519 из seed по SLIP-0010 / стандартному BIP-32 path, не SHA-256.

Библиотеки: `androidx.security:security-crypto` (Tink), SQLCipher, `org.bouncycastle:bcprov` только если без Tink не обойтись. Не писать PBKDF2 руками — в `Bip39Helper.mnemonicToSeed` свой PBKDF2-HMAC-SHA512 уже есть и выглядит правдоподобно, его можно оставить **только** для BIP-39 seed, покрыть тестом против bitcoinj/`python-mnemonic`.

## 3.3. HybridPostQuantumEngine — FAKE

Файл: `crypto/HybridPostQuantumEngine.kt`.

Комментарий обещает `SHA3-256(ECDH || Kyber || context)` и ChaCha20-Poly1305. Код:

- Kyber/ML-KEM **нет**.
- ECDH **нет**.
- SHA3 **нет**, стоит SHA-256.
- ChaCha **нет**, стоит AES-GCM.
- «PQ seed» 32 байта `SecureRandom` **кладётся в пакет открытым текстом** (`pqKdfSeedHex`).

Атака: если классический секрет скомпрометирован (а PQ как раз для этого), атакующий читает seed из JSON и получает тот же ключ. Постквантовая стойкость = 0.

Тест `HybridPostQuantumEngineTest` проверяет только round-trip. Такой тест зелёный на любом XOR.

**Как сделать по-настоящему:**

- Не делать PQ, пока нет настоящего канала сообщений. PQ — этап после живого SimpleX.
- Когда дойдёте: **Google Tink Hybrid** или `liboqs-android` / CIRCL: `X-Wing` (X25519+ML-KEM-768) или `HPKE` (RFC 9180) с KEM `DHKEM(X25519)` сейчас, ML-KEM позже.
- Shared secret **никогда** не возить в пакете.
- SAS-эмодзи: по образцу Signal — 3×2 эмодзи / SAS из 64 бит общего секрета, сверка голосом. Не 4 эмодзи из первых 4 байт ключа.

До этого файл пометить `STUB`, вычистить слово Kyber.

## 3.4. DoubleRatchetVault — FAKE

Файл: `crypto/DoubleRatchetVault.kt`.

Это не Signal Double Ratchet. Это:

- ECDH на NIST P-256 (не X25519),
- два HMAC на `"ChainA"`/`"ChainB"`,
- нет skipped message keys → любой lost/reorder ломает цепочку,
- `sequenceNumber` пишется и **не читается** при decrypt,
- нет associated data,
- если `sharedSecretRoot == null`, каждый пир сам себе генерирует root,
- fallback-строки `"FallbackSend"` / `"FallbackRecv"` — общий секрет в исходнике,
- padding 16 КБ есть, но hex на проводе его убивает.

**Как сделать по-настоящему:**

- Взять `org.whispersystems:signal-client` / `libsignal-client` Android.
- Либо, если канал = SimpleX: **не писать ratchet вообще** — SimpleX уже даёт E2E. Двойная самодельная крипта поверх SimpleX вредна.
- Рекомендация аудитора: **канал сообщений = официальный SimpleX**. Свой ratchet удалить.

## 3.5. ZeroKnowledgeVaultEngine — FAKE + ложный HowTo

Файл: `crypto/ZeroKnowledgeVaultEngine.kt`. Помечен `100% REAL PRODUCTION CODE`.

Ложь по пунктам:

| HowTo / KDoc | Код |
|---|---|
| Zero-Knowledge | Обычный файл + AES. ZK-доказательств нет |
| ChaCha20-Poly1305 | `AES/CBC/PKCS5Padding` |
| Уникальный 96-bit nonce | IV = 16 байт значения `0x42` |
| BIP-39 → HKDF-SHA256 | `SHA-256(hex(seed) \|\| "SimpleX-ZK-Vault-Salt")` |
| Reed-Solomon GF(2⁸), любые K из N | XOR с `(d+p+1)*31` не в поле |
| Восстановление из любых K | `reconstructFile` требует **все data-шарды**, parity игнорируется |

Тест восстанавливает из 4 data-шардов из 4. Стирание не проверяется. Тест зелёный при полностью сломанном RS.

XFTP-рассылка шардов — симуляция диалога. Это единственная честная часть.

**Как сделать по-настоящему:**

1. Шифрование файла: Tink `AesGcmHkdfStreaming` или `ChaCha20Poly1305` из Tink, ключ = HKDF-SHA256(BIP39-seed, info=`madgammon-vault-v1`).
2. Erasure coding: готовый RS, например Backblaze Java Reed-Solomon / `com.backblaze.erasure`, GF(256), параметры (k=4, m=2) или (k=6, m=3).
3. Обязательные тесты:
   - drop любого 1 data-шарда → восстановление успешно;
   - drop k+1 шардов → ошибка;
   - bit-flip в шарде → checksum ловит;
   - разные nonce на каждый шард.
4. Распределение: либо настоящий XFTP клиент SimpleX, либо не показывать «отправка на onion node1..node6». Пока сети нет — диалог остаётся `SIMULATION` без слова REAL в движке.

ZK в названии убрать. Это не ZK. Название: `ErasureCodedVault`.

## 3.6. CryptoBillingEngine — DANGEROUS FAKE

Файл: `billing/CryptoBillingEngine.kt`. Помечен `100% REAL`.

Адреса:

```
XMR  = "8" + sha256hex[0..94]
USDT = "T" + sha256hex[0..33].uppercase()   // не Base58Check
LN   = "lnbc" + hex[0..48] + "1"            // не bolt11
```

Любой кошелёк либо отвергнет, либо (хуже) пользователь «дополнит» похожий адрес руками и отправит деньги в никуда.

«Ончейн-проверка»:

- USDT: в JSON есть подстрока `"trc20"` **ИЛИ** `customTxHash.length >= 64` → оплачено.
- XMR: любой 64-символьный hex → 10/10 confirmations без демона.
- LN: запрос на mempool.space по **несуществующему** адресу; курс BTC зашит `$65000`, XMR `$165`.
- Подписка: `prefs.has_active_crypto_subscription = true`.

Это не «симуляция мемпула». Это генератор фальшивых платёжных реквизитов.

**Как сделать по-настоящему (два допустимых пути, выбрать один):**

**Путь A — честный self-hosted merchant (рекомендую):**

1. Отдельный маленький backend (не в APK): Monero wallet-rpc + TronGrid/USDT + LND.
2. Приложение запрашивает инвойс по HTTPS/onion: `{plan, currency}` → `{address/bolt11, amount, expires, invoiceId}`.
3. Приложение поллит `GET /invoice/{id}` (статус `unpaid|confirming|paid`). Никакой «проверки» на клиенте.
4. Подпись ответа сервера (Ed25519), ключ сервера зашит как pin.
5. Live-курс: CoinGecko / Kraken / свой ticker, с таймаутом, без хардкода.

**Путь B — пока без денег:**

- UI биллинга только `SIMULATION`.
- Поле адреса **не копируется** и не показывается как QR «на оплату».
- `activateCryptoSubscription()` из клиента удалить. Премиум-флаг только с сервера.

Путь «оставить генератор адресов в APK» — запрещён.

Валидатор `isValidAddress` (`startsWith("8")`) удалить. Для XMR — regex официального base58 + checksum; для TRON — Base58Check; для LN — bolt11 decoder.

## 3.7. SimpleX слой — FAKE

Файлы: `SimpleXProtocol.kt`, `SimpleXEmbeddedController.kt`, `SimpleXSmpClient.kt`, `SimpleXPushService.kt`.

- `createInvitationLink` клеит случайный Base64 к `smp10.simplex.im`. Официальный клиент такой линк не примет (нет queue key, нет server fingerprint, нет version handshake).
- `parseLink` «парсит» что угодно в UUID.
- Embedded controller распаковывает `simplex-arm64` из assets, Gradle качает **linux** binary (`simplex-chat-linux-arm64`) с timeout 2 секунды. На Android ELF это не стартует.
- Сообщения живут в ViewModel, как признаёт манифест.
- Google STUN в списке серверов «параноидального» мессенджера.

**Как сделать по-настоящему:**

1. Не встраивать CLI. Использовать:
   - либо официальный подход SimpleX: JVM/Kotlin client к `simplexmq` (SMP agent),
   - либо отдельный foreground-service с **официальным android binary** SimpleX chat (если команда SimpleX его отдаёт под вашу arch), checksum pinned,
   - либо (прагматичный путь) не клонировать SimpleX, а **deep-link в установленный SimpleX** + свой игровой P2P.
2. Рекомендация аудитора по продукту:  
   **Игровой P2P ходить своим тонким протоколом (ход/бросок) через уже существующий E2E-канал SimpleX как transport.** Не реализовывать SMP с нуля.  
   Чат в приложении = WebView/SDK SimpleX **или** «открыть контакт в SimpleX». Свой «почти SimpleX» всегда будет фейком.
3. Если всё-таки свой SMP-клиент: спецификация `simplexmq`, websocket + RSA/Ed25519 handshake, queue IDs как в протоколе. Это месяцы работы. Не притворяться, что это этап 3 «закрыт».

`SimpleXPushService` с `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` обычным приложением в Play не получить. Для фона: `FOREGROUND_SERVICE_SPECIAL_USE` + обоснование, или FCM (что противоречит манифесту «без Google»). Выбрать threat model и не врать.

## 3.8. Голосовые звонки — SIMULATION

Файл: `SimpleXCallManager.kt`.

Реально: `AudioRecord` 16 kHz PCM, AEC/NS, `AudioTrack`, RMS.  
Фейк: сырой PCM по UDP, без Opus, без SRTP/DTLS, без ICE, при отсутствии пира — loopback `127.0.0.1`. SAS из SHA-256 «на глаз».

**Как сделать по-настоящему:**

- WebRTC (`org.webrtc:google-webrtc`) или звонки SimpleX.
- Кодек Opus 16–48 kHz.
- DTLS-SRTP, ICE через **свои** TURN (не `stun.l.google.com`).
- Сигналинг — через тот же E2E-канал, что и чат.
- Пока WebRTC нет — кнопка трубки disabled + `SIMULATION`, loopback не выдавать за звонок.

## 3.9. VPN / Tun2Socks / Xray — FAKE dataplane

Файлы: `FoxrayVpnService.kt`, `Tun2SocksPacketRouter.kt`, `FoxrayVpnManager.kt`, `V2RayEmbeddedController.kt`, `AntiCensorshipConfigBuilder.kt`.

`Tun2SocksPacketRouter`:

- читает TUN,
- для TCP открывает **неблокирующий** `SocketChannel` на `127.0.0.1:10808`,
- **не делает SOCKS5 handshake**,
- **не пишет payload**,
- **не читает ответ обратно в TUN**,
- UDP/DNS только логирует.

Это не VPN. Это датчик «пакеты бывают».

`startVpnConnectionSimulation()` через 1.2 с ставит `Connected` и пинг `28 мс`, даже если туннель мёртв.

Xray: в assets лежит `xray-arm64` (скачан zip). Запуск как Linux binary на Android — тот же класс ошибки, что SimpleX CLI, если это не android-сборки.

`FOREGROUND_SERVICE_SYSTEM_EXEMPTED` — не для обычных приложений.

**Как сделать по-настоящему:**

1. Не писать tun2socks. Взять готовое:
   - **sing-box** Android / libbox,
   - или **libxray** / hev-socks5-tunnel / tun2socks от xjasonlyu как JNI,
   - или встроить **Outline** / **v2rayNG** core как библиотеку.
2. Конфиг VLESS-Reality / Hysteria2 / ShadowTLS генерировать **валидным JSON под выбранное ядро**, поднимать ядро, потом `VpnService.Builder` с `addRoute(0.0.0.0, 0)`, DNS через ядро.
3. Состояние `Connected` только после успешного probe (TCP/DoH через туннель), не после `Thread.sleep(1200)`.
4. Пинг — реальный RTT, не `28`.
5. Имена `*Simulation` удалить, когда dataplane живой. Пока нет — UI `SIMULATION`.

## 3.10. Tor — каркас REAL-ISH, использование FAKE

`TorEmbeddedController` использует `org.torproject.android.binary.TorResourceInstaller` — это правильное направление.

Дыры:

- fallback в `testOnionConnection`: синтетический пинг 110–220 ms и текст «E2EE handshake success (симуляция)», если демон не встал;
- onion hostname читается из файла, но приложение не слушает `127.0.0.1:8080` как advertised HiddenServicePort;
- bridges (`BridgeManager`) — нужно проверить, что obfs4/snowflake **реально** прокидываются в torrc, а не рисуются в UI.

**Как сделать по-настоящему:**

- Guardian Project `TorService` / `IPtProxy` (snowflake, obfs4).
- SOCKS только `127.0.0.1:9050`, изолированные circuit isolation flags.
- Любой «успех» без живого bootstrap (`NOTICE Bootstrapped 100%`) — ошибка, не симуляция.
- Для P2P игры: либо onion service с **реальным** слушающим сервером в app, либо не обещать P2P-over-Tor.

## 3.11. MentalDiceEngine — REAL-ISH, но не тот протокол

Алгоритм commit-reveal на HMAC-SHA256 и `SecureRandom` — самый честный криптомодуль в репо.

Проблемы:

1. HowTo описывает **другую формулу**, чем код.

HowTo:

```
Dice1 = ((S_A ⊕ S_B) mod 6) + 1
Dice2 = SHA256(S_A || S_B)[0] mod 6 + 1
```

Код:

```
Die1 = ((D_A + D_B) mod 6) + 1          // D — отдельное число 0..5
Die2 = (seed[0] xor peerSeed[0]) mod 6 + 1
```

Документ и код расходятся. Это само по себе провал аудита спецификации.

2. Движок не форсирует «оба commitment до любого reveal». Это должно быть в state machine, не в «надежде на ViewModel».
3. Онлайн-матчмейкинг practice — бот. Помечено. Ок как SIMULATION.
4. `java.util.Random` для цвета в ViewModel и `(1..6).random()` в локальной жеребьёвке — для оффлайн-бота допустимо, для ranked P2P — нет.

**Как сделать по-настоящему:**

- Зафиксировать **одну** спецификацию (рекомендую: commit = SHA256(seed), seed 32 байта; dice1/dice2 = первые два байта SHA256(seedA xor seedB) как unbiased rejection sampling, не `mod 6` — `mod 6` по байту слегка смещён).
- State machine: `WAIT_BOTH_COMMITS → REVEAL → VERIFY → APPLY`. Reveal до двух commit = дисквалификация.
- Тесты: подмена reveal ловится; одинаковый seed не принимается дважды; распределение χ² на 60k бросков.
- P2P: возить commitment/reveal через реальный канал (§3.7), не через память.

## 3.12. P2PGameProtocol — STUB

JSON-конвертер. Нет MAC на сообщение, нет привязки к ratchet, нет сверки `boardStateHash` (поле есть, кто его считает и с чем сравнивает — не специфицировано), нет защиты replay.

**TZ:** либо удалить и сериализовать ходы как сообщения SimpleX, либо:

- canonical JSON / protobuf,
- `seq`, `gameId`, `signer` Ed25519,
- после каждого хода оба считают `sha256(canonicalBoard)`, mismatch → freeze + resync snapshot,
- snapshot = тот, что уже есть в `EngineSnapshot`.

## 3.13. Матчмейкинг, чат, Gemini

- Practice matchmaking — бот. Оставить как `vs CPU`, не называть «онлайн».
- Настоящий онлайн: сигналинг (свой сервер или SMP) → обмен identity → одинаковый `gameId` → Mental Dice → ходы.
- Gemini-комментатор: ключ через secrets plugin — ок. Fallback на локальные шутки — ок. Нельзя слать на Gemini **позицию + seed + PIN**. Проверить, что в промпт не уходит ничего кроме доски.
- `GeminiJokeService` при placeholder ключе молча локальный fallback — ок.

## 3.14. UI-безопасность — преувеличение

- `MatrixKeyboard` — Compose-кнопки. Не закрывает Accessibility, Overlay, screen reader, malware. Для anti-keylogger нужен ещё `FLAG_SECURE` всегда в чате + запрет системного IME (`WindowInsetsCompat` / `showSoftInputOnFocus=false` — это у вас частично есть) + attestation «нет активных Accessibility, кроме системных». Честно писать: «своя клавиатура, снижает телеметрию Gboard, не серебряная пуля».
- `FLAG_SECURE` тумблером. Для секретного контура — **всегда on**, без тумблера.
- Stealth-иконка через activity-alias — нормальный приём. Проверить, что смена иконки не светит в launcher обе.
- Запрос CAMERA + RECORD_AUDIO + VPN + battery optimization **на первом экране** — враждебно и палевно для «маскировки под нарды». Спрашивать контекстно.

## 3.15. Прочее

- Пакет `com.example`, applicationId AI Studio.
- `isMinifyEnabled = false` в release.
- Firebase BOM в gradle без использования.
- README — шаблон AI Studio. Заменить на короткий честный README: «прототип стола + аудит в GrokAuditReport.md».
- HowTo обещает языки HY/TR/FA. В `Language.kt` набор другой. Либо дописать, либо вычеркнуть.
- Сборка бинарей на `connectTimeout=2000` — при оффлайне тихий warn, приложение «как бы с ядром». Pin checksum, fail the build если бинаря нет.

---

# 4. Игровой движок: где ты запутался в правилах

Файл: `model/GameEngine.kt` (~1083 строки). Это единственное место, которое заказчик просил сделать «красиво и правильно». Сейчас красиво важнее правильного.

Ниже — дефекты, которые я вижу по коду против `MadGammonHowTo.MD` §3.1 и против общепринятых длинных/коротких нард. Где HowTo расходится с классикой — **остановиться и спросить заказчика одним списком вопросов**, не угадывать.

## 4.1. Зафиксированный конфликт спецификаций (сделать до кода)

Заказчику/дизайнеру правил ответить письменно (и положить в `docs/RULES.md`):

1. Жеребьёвка: два этапа (цвет, затем первый ход) — это задумано? В классике цвет привязан к стороне доски, лот только на первый ход.
2. После лота **первый ход = два кубика лота** (`applyLotStage2WinnerAndStart` кладёт `diceLot2White` и `diceLot2Black` в `remainingDice`). В классике победитель лота **перебрасывает пару**. Что верно для MAD/LONG/SHORT?
3. MAD: если каскад куша начат и роллер не может первый ход — каскад аннулируется целиком (`isFreshKushRoll`). Так задумано?
4. MAD: в середине переданных ходов, если у **роллера вдруг появился** легальный ход, ход **возвращается** роллеру (`makeMove` ~строки 631–637). Этого нет в HowTo. Похоже на баг.
5. LONG: исключение первого хода «при 6:6, 4:4, 3:3 можно снять **две** с головы» — в HowTo есть, в коде **нет** (`taken >= 1` всегда). Это дыра.
6. MAD: внутри дома только выброс, без перемещений — в коде есть. Подтвердить.
7. MAD: выброс только точным значением — в коде есть. Подтвердить (классика длинных разрешает «старший, если нет сзади»).
8. Запрет шестиблока, пока оппонент не прошёл: логика `willBuildIllegalSixBlock` с `% 24` и `r > maxFenceOpponentRel` подозрительна на ложные срабатывания через шов 0/23. Нужна таблица позиций.
9. Коротких: обязанность играть **оба** кубика, если возможно; если только один — больший. В `getAllLegalMoves` этого нет. Игрок может съесть меньший и «застрять».

Пока нет `docs/RULES.md` с ответами — не считать движок готовым.

## 4.2. Конкретные баги кода

### GROK-G01. Нет исключения двух снятий с головы (LONG, первый ход, 3:3/4:4/6:6)

HowTo §3.1. Код `isMoveLegal`:

```
if (taken >= 1) return false
```

для LONG и MAD одинаково. Исключение не смотрит ни на `isFirstTurnMap`, ни на значение куша.

**TZ:** для `GameMode.LONG && isFirstTurn && dice in {3,4,6} && double` разрешить `taken < 2`. Для MAD — отдельное решение заказчика. Тесты: позиция старта, бросок 3:3, два снятия с головы легальны; третье — нет. Бросок 2:2 на старте — только одно снятие.

### GROK-G02. Возврат хода роллеру во время передачи (MAD)

После `makeMove` оппонентом, если у роллера появились легальные — `activePlayer = roller`. HowTo говорит только «остаток передаётся». Возврат порождает дёрганый пинг-понг внутри одного каскада.

**TZ:** убрать блок 631–637, пока заказчик явно не утвердит. Каскад: роллер играет что может → передаёт остаток → оппонент играет что может → следующий ярус водопада снова у роллера (`loadNextWaterfallPhase` это уже делает).

### GROK-G03. Жеребьёвка как первый ход

`applyLotStage2WinnerAndStart` не переводит в `BEFORE_ROLL`, а сразу даёт ход чужими двумя костями. Для SHORT это ещё страннее (нет ввода с бара, но кости чужие).

**TZ:** после лота — `gameStatus = BEFORE_ROLL`, `roller = winner`. Пара бросается заново (локально или Mental Dice).

### GROK-G04. Баг подписей лота, если человек играет чёрными

```
whiteStr = if (human == WHITE) "Вы (Белые)" else "Вы (Черные)"  // всегда «Вы»
```

Лог врёт. Починить строки.

### GROK-G05. Нет правила «обязан сыграть максимум очков» (особенно SHORT)

`getAllLegalMoves` возвращает любой легальный один кубик. Нет проверки: существует ли последовательность, использующая оба. Классика: если 6 и 2 оба играются — нельзя сыграть только 2 и сдаться.

**TZ:** генерация легальных **полных** плейаутов (уже почти есть в `findReachablePathsFrom`). Ход принимается только если он принадлежит какому-то максимальному плейауту (по числу использованных костей, при равенстве — по большему кубику).

### GROK-G06. `findReachablePathsFrom` мутирует живую доску

DFS ходит по `board` / `remainingDice` / `bar` и потом restore. Любой реентранс из UI/бота/лога посередине = порча партии.

**TZ:** DFS только на копии (`EngineSnapshot` / immutable board). Живой стейт трогает только `makeMove`.

### GROK-G07. Шестиблок через шов и «hasCrossed»

`for (start in 0 until 24)` + `(start+o)%24` находит «забор» из 6, который в абсолютных индексах длинных нард может быть бессмысленным (белые и чёрные идут по смещённым относительным). Сравнивать забор нужно в **относительных** координатах оппонента: 6 подряд на его пути, и ни одна его шашка не стоит строго дальше этого забора (классика: нельзя закрыть, пока враг на голове / не вошёл в зону).

**TZ:** написать 10 фикстур (схема доски в комментарии) — разрешённый prime и запрещённый prime. Код подогнать под фикстуры, не наоборот.

### GROK-G08. `getAllLegalMoves` + `distinct()` на кушах

Для `[6,6,6,6]` distinct даёт один 6 — как **список кандидатов** это ок, потому что `makeMove` снимает один элемент. Для подсчёта «есть ли ход» ок. Для UI, показывающего 4 слота — проверить, что HUD рисует 4, а не 1.

### GROK-G09. Статическая `GameEngine.currentLanguage`

Глобальный var. Два движка / тесты будут драться. Язык — параметр `addLogEntry`, не companion.

### GROK-G10. Бот — жадный 1-ход, не 1-ply по двум костям

Для демо сойдёт. Для «ИИ» — нет. Gemini-анализатор не заменяет легальность. Бот не должен предлагать ход, который нарушает GROK-G05.

Минимум: перебрать все полные плейауты, скорить (выброс > вход в дом > снятие головы при >5 на голове > разгрузка перегруженных пунктов), выбрать max.

### GROK-G11. Нет счёта партии (марс / кокс / очки)

`MatchHistory` хранит `scorePlayer/scoreOpponent`, движок отдаёт только `winner`. Для SHORT без кубика удвоения — ок как «одна партия = 1 очко». Для длинных часто играют до 5/7. Зафиксировать в RULES.

### GROK-G12. Короткие: hit во время поиска путей

В DFS hit сбрасывает блот на бар, но не симулирует «обязательный вход с бара следующим кубиком» как отдельное ограничение на **том же ходе**, если первый кубик тебя самого не выбил (это про оппонента). Проверить фикстурой: вход с бара двумя костями, один пункт закрыт двумя врагами.

### GROK-G13. RNG жеребьёвки

`(1..6).random()` — Kotlin Random. Для бота ок. Для «честной жеребьёвки» P2P — только Mental Dice.

### GROK-G14. Нет property-тестов

Ни одного теста на `GameEngine`. 1083 строки правил без тестов = регрессии на каждый «маленький фикс».

**TZ на тесты движка (обязательные, до фич чата):**

- старт LONG: 15 на голове каждого;
- старт SHORT: стандарт 2-5-3-5;
- LONG 6:6 первый ход: 2 с головы легальны (после ответа на вопрос 5);
- MAD 5:5: очередь `4x5, 4x6`;
- MAD дом: ход 18→20 запрещён, 22→24 (выброс) при всех в доме и точном числе — разрешён;
- SHORT blot hit → bar+1, вход с бара обязателен;
- SHORT пункт с 2 врагами — блок;
- передача MAD: позиция без ходов у роллера, остаток появляется у оппонента;
- запрещённый шестиблок vs разрешённый;
- 15th borne off → GAME_OVER;
- snapshot/restore round-trip.

Гони тесты на JVM (без Robolectric, движок не должен зависеть от Android — сейчас тянет `android.util.Log`. Вынести лог в интерфейс. Это важно: иначе движок нельзя крутить тысячами партий).

## 4.3. Архитектура движка

Сейчас `GameEngine` = правила + лог + бот + жеребьёвка + язык. Разрезать:

```
rules/BoardState.kt        // immutable
rules/Move.kt
rules/LegalMoves.kt        // чистые функции
rules/MadRules.kt
rules/LongRules.kt
rules/ShortRules.kt
rules/GameMachine.kt       // статусы LOT/ROLL/MOVE/OVER
ai/HeuristicBot.kt
```

ViewModel вызывает `machine.apply(Command.Roll(d1,d2))`. Не 4000 строк с VPN.

---

# 5. Архитектурное ТЗ (обязательный рефакторинг-каркас)

Не надо «переписать всё с нуля». Надо **перестать класть новое в богов**.

Целевые модули:

```
:core:rules          GameEngine без Android
:core:crypto-id      BIP39 + Keystore identity (без «Kyber»)
:feature:game        стол, кости, HUD
:feature:chat        интерфейс ChatTransport
:feature:net         VpnCore, TorCore  (обёртки над библиотеками)
:feature:vault       ErasureCodedVault
:feature:billing     только клиент инвойсов
:app                 DI, MainActivity
```

Пока Gradle-модули тяжело — пакеты те же, но **новые файлы**. `GameScreen.kt` не растить. Вырезать из него чат, VPN-панель, биллинг в отдельные composable-файлы.

`GameViewModel` разрезать на:

- `MatchViewModel`
- `IdentityViewModel`
- `TransportViewModel`
- `BillingViewModel`

Связь — через интерфейсы, не через один класс с 80 `var`.

Пакет: `org.<yourbrand>.madgammon` (заказчик даёт имя). `com.example` в проде = отказ аудита.

Release: `isMinifyEnabled = true`, R8, shrink resources, нет debug keystore в release.

---

# 6. Пошаговый план реализации (порядок, не параллель «всё сразу»)

Женя, делай **в этом порядке**. Каждый этап заканчивается чеклистом §9.

## Этап 0. Честность (1 день)

- Снять все `[x]` с Evolution Plan.
- Переименовать статусы файлов: STUB/SIMULATION.
- Удалить фразу `100% REAL PRODUCTION CODE` везде.
- README честный.
- `docs/RULES.md` — ответы на вопросы 4.1 (нужен заказчик).
- Закрыть копирование фейковых криптоадресов в UI.

## Этап 1. Правила нард (приоритет заказчика)

- Вынести Log.
- Написать тесты GROK-G01…G14.
- Починить G01–G07 по `docs/RULES.md`.
- Бот играет только полными плейаутами.
- Не трогать крипту в этом этапе.

## Этап 2. Секреты

- Keystore + EncryptedFile для seed.
- PIN verifier, без default `123456`.
- `allowBackup=false`, cleartext=false.
- Seed не в Compose state.
- Тест: `adb backup` не содержит мнемонику (эмулятор).

## Этап 3. Транспорт сообщений (выбрать один и не плодить)

Рекомендация аудитора: **SimpleX как внешний транспорт** (интент / SDK), внутри app — только `ChatTransport.send(bytes)`.

Если «свой чат в UI» обязателен продуктом:

- SQLCipher для transcript,
- libsignal **или** (лучше) не свой ratchet, а шифрование поверх SMP agent,
- инвайты совместимы с официальным SimpleX (проверить: линк открывается в официальном приложении и устанавливает контакт).

Linux CLI из assets — удалить.

## Этап 4. Честный бросок + онлайн-партия

- Mental Dice по одной спецификации.
- Ходы по `ChatTransport`.
- Сверка `boardStateHash`.
- Practice vs CPU так и называется. «Онлайн» — только когда второй процесс на втором устройстве проходит партию.

## Этап 5. VPN

- Встроить sing-box или libxray AAR.
- Dataplane: ping 1.1.1.1 **через туннель** после Connect.
- Пока нет — тумблер VPN показывает SIMULATION и не врёт `Connected`.

## Этап 6. Tor

- TorService, bootstrap 100%, иначе ошибка.
- Убрать синтетический ping.

## Этап 7. Vault

- Tink streaming AEAD + настоящий RS.
- Тесты с выколотыми шардами.
- Сеть шардов — отдельный этап или SIMULATION.

## Этап 8. Голос

- WebRTC. Loopback убрать из пользовательского пути.

## Этап 9. Биллинг

- Только путь A (сервер кошельков) или отсутствие оплаты.
- Клиентские «адреса из SHA-256» удалить.

## Этап 10. PQ

- Не начинать, пока этап 3 не PRODUCTION.
- Tink / X-Wing, не свой класс.

---

# 7. Рекомендованный стек (чтобы не выдумывать)

| Задача | Брать | Не брать |
|---|---|---|
| E2E чат | SimpleX SDK / simplexmq agent | Свой SMP на 200 строк |
| Ratchet | libsignal-client | `DoubleRatchetVault.kt` |
| AEAD / KDF | Tink | Свой CBC + IV 0x42 |
| PQ KEM | Tink Hybrid / liboqs X-Wing | `SecureRandom` 32 байта в пакете |
| BIP-39 | bitcoinj `MnemonicCode` или свой helper **после теста против bitcoinj** | SHA-256 как «pubkey» |
| RS erasure | Backblaze Java RS | XOR `* 31` |
| Onion | Guardian TorService + IPtProxy | Синтетический ping |
| VPN core | sing-box libbox / libxray | самописный tun2socks без SOCKS handshake |
| Голос | WebRTC + Opus | PCM UDP loopback |
| LN invoice | `lightning-invoice` / LND REST | `"lnbc"+hex` |
| XMR address | monero-java / wallet-rpc | `"8"+hex` |
| TRON address | Base58Check + keccak | `"T"+hex.uppercase()` |
| DB секретов | SQLCipher | Room без шифрования + prefs |
| PIN | Keystore / Argon2id | prefs `"123456"` |

Версии библиотек — пинить. Сборка должна падать, если checksum ядра не совпал.

---

# 8. Что оставить от прототипа

Это не «выкинь всё». Оставить:

- визуальный стол, темы, кости, звук броска, радио SomaFM как фича-маскировка;
- трёхрежимный каркас MAD/LONG/SHORT;
- FLAG_SECURE (сделать обязательным в секретном контуре);
- activity-alias двух иконок;
- QR scanner/generator (сами по себе нормальные);
- Room истории партий (добавить шифрование, когда появится identity);
- честный `SIMULATION_AUDIT.md` как живой реестр — но синхронизировать с этим документом, вычеркнув ложь «RS/ChaCha реальны».

Выкинуть или изолировать за флагом `BuildConfig.SIMULATION`:

- генератор криптоадресов;
- PQ-движок;
- самодельный ratchet;
- Linux CLI в assets;
- Connected-через-sleep в VPN;
- default PIN/duress.

---

# 9. Definition of Done / приёмка

Аудитор (я или следующий проход) ставит PRODUCTION только если:

**Игра**

- [ ] `docs/RULES.md` подписан заказчиком.
- [ ] JVM-тесты GROK-G01…G14 зелёные на CI.
- [ ] Ручная партия MAD / LONG / SHORT vs бот без невозможных ходов (запись экрана).
- [ ] Бот не нарушает «играть оба кубика».

**Секреты**

- [ ] `allowBackup=false`, cleartext=false.
- [ ] Seed не находится `grep`/`strings` в backup и в logcat после онбординга.
- [ ] Нет default PIN в коде.
- [ ] Duress открывает decoy, не «просто прячет кнопку».

**Чат / P2P**

- [ ] Два физических устройства обмениваются сообщением, которое **не** видно plaintext в logcat.
- [ ] Официальный SimpleX (если выбран путь совместимости) принимает инвайт **или** свой протокол документирован и имеет interoperable fixture.
- [ ] Linux-бинаря нет в APK (`apktool d` проверка).

**Кости P2P**

- [ ] Подмена reveal ломает партию (тест + ручной).
- [ ] HowTo формула = код.

**VPN**

- [ ] tcpdump/wireshark на Wi-Fi: после Connect нет прямого 1.1.1.1 с устройства, только к VPN endpoint.
- [ ] UI Connected ⇔ probe success.

**Tor**

- [ ] Bootstrap 100% в логе tor. Нет ветки «нарисуй 110ms».

**Vault**

- [ ] Удаление любых m шардов из k+m, файл восстанавливается; удаление m+1 — нет.
- [ ] AEAD, не CBC+const IV.

**Биллинг**

- [ ] Либо нет адресов в APK, либо адрес с wallet-rpc и контрольная оплата 0.000001 тестовой сети / tiny XMR прошла и подписка включилась **сервером**.
- [ ] Клиентский SHA-hex адрес отсутствует в APK (`strings app.apk | grep lnbc` не находит генератор).

**Мета**

- [ ] Пакет не `com.example`.
- [ ] Нет строки `100% REAL` в src.
- [ ] `GameScreen.kt` < 2000 строк (чат/VPN вынесены).
- [ ] R8 minify on.
- [ ] Этот файл: секция «закрытые пункты» обновляется ссылками на коммиты.

---

# 10. Журнал находок (ID для коммитов)

Используй ID в commit message: `fix(GROK-C03): remove const IV from vault`.

| ID | Серьёзность | Тема |
|---|---|---|
| GROK-C01 | P0 | Seed/PIN в plaintext prefs, default 123456/666666 |
| GROK-C02 | P0 | allowBackup + cleartext |
| GROK-C03 | P0 | Vault CBC IV=0x42, фейковый RS, не ChaCha, не ZK |
| GROK-C04 | P0 | Billing fake addresses + spoofable «confirm» |
| GROK-C05 | P0 | PQ seed в открытом пакете, нет Kyber |
| GROK-C06 | P0 | Homemade Double Ratchet непригоден |
| GROK-C07 | P0 | SimpleX Linux CLI + fake invites |
| GROK-C08 | P0 | Tun2Socks не форвардит, VPN Connected по sleep |
| GROK-C09 | P1 | Tor synthetic success |
| GROK-C10 | P1 | Voice loopback as call |
| GROK-C11 | P1 | Identity «pubkey»=SHA256, fake .onion |
| GROK-C12 | P1 | PBKDF2 10k, нет Keystore |
| GROK-C13 | P1 | Google STUN в суверенном контуре |
| GROK-C14 | P1 | SYSTEM_EXEMPTED foreground |
| GROK-C15 | P1 | Mental Dice ≠ HowTo, нет bind-commits |
| GROK-C16 | P2 | com.example, AI Studio README, minify off |
| GROK-C17 | P2 | HowTo ссылается на несуществующие файлы |
| GROK-C18 | P2 | Evolution Plan все этапы «закрыты» |
| GROK-C19 | P2 | God files 12k/4k |
| GROK-C20 | P2 | MatrixKeyboard overclaim |
| GROK-G01 | P0 игра | Нет 2-с-головы на 3:3/4:4/6:6 LONG |
| GROK-G02 | P0 игра | Возврат хода роллеру при MAD transfer |
| GROK-G03 | P1 игра | Лот как первый ход |
| GROK-G04 | P2 игра | Баг подписей лота |
| GROK-G05 | P0 игра | Нет must-play-both-dice |
| GROK-G06 | P1 игра | DFS мутирует живую доску |
| GROK-G07 | P1 игра | Шестиблок |
| GROK-G08 | P2 игра | HUD куша vs distinct |
| GROK-G09 | P2 игра | static language |
| GROK-G10 | P2 игра | Бот не полный плейаут |
| GROK-G11 | P2 игра | Нет правил счёта |
| GROK-G12 | P1 игра | SHORT bar/hit фикстуры |
| GROK-G13 | P2 игра | RNG лота |
| GROK-G14 | P0 игра | Нет тестов движка |

---

# 11. Тон работы

Женя: ты хорошо собираешь UI и атмосферу стола. На этом прототип выполнен. Дальше продукт начинается там, где кончаются комментарии «100% REAL».

Не защищай фейки маркировкой. Либо библиотека + тест из §9, либо `SIMULATION` без QR на оплату и без слова Kyber.

Если пункт ТЗ непонятен — один вопрос заказчику, не три альтернативные «гибридные постквантовые» реализации.

Когда закроешь Этап 1 и Этап 2, имеет смысл звать аудитора на второй проход. Не раньше.

— конец GrokAuditReport
