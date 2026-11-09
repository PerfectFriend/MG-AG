package com.example.ui.viewmodel.delegates

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.Bip39Helper
import com.example.data.CryptoIdentity
import com.example.data.ProfileManager
import com.example.model.DuressAction
import com.example.model.UserProfile
import com.example.security.EncryptedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Делегат безопасности, PIN-кодов и защиты от принуждения (Duress / Anti-Tamper).
 *
 * Отвечает за:
 * - Экспоненциальный расчёт задержки при ошибочном вводе PIN.
 * - Локдаун на 48 часов при активации режима паники.
 * - Верификацию и восстановление доступа через мнемоническую BIP-39 seed-фразу.
 * - Обработку ввода Duress-PIN (переключение на ложный профиль или экстренное уничтожение ключей).
 *
 * @param context Контекст приложения Android.
 * @param scope CoroutineScope ViewModel для выполнения таймеров обратного отсчёта.
 */
class SecurityPinDelegate(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)
    private val securePrefs = EncryptedPrefs(context, "crazy_backgammon_prefs")

    val profileManager = ProfileManager(context)

    var pinCode: String by mutableStateOf(securePrefs.getString("pin_code", ""))
        private set

    val panicPin: String
        get() = profileManager.duressPinCode.ifBlank { securePrefs.getString("duress_pin_code", "") }

    var pinFailedAttempts by mutableIntStateOf(prefs.getInt("pin_failed_attempts", 0))
        private set

    var pinLockoutRemainingSeconds by mutableIntStateOf(0)
        private set

    var pinLockoutTotalSeconds by mutableIntStateOf(prefs.getInt("pin_lockout_total_seconds", 0))
        private set

    var panicLockoutUntilTimestamp by mutableLongStateOf(
        prefs.getLong("panic_lockout_until_timestamp", 0L)
    )
        private set

    var isPanicRecoveryRequired by mutableStateOf(
        prefs.getBoolean("panic_recovery_required", false)
    )
        private set

    var isScreenSecurityEnabled by mutableStateOf(profileManager.isScreenSecurityEnabled)
    var onScreenSecurityChanged: ((Boolean) -> Unit)? = null

    val activeProfile: UserProfile get() = profileManager.activeProfile
    val availableProfiles get() = profileManager.availableProfiles
    val duressPinCode: String get() = profileManager.duressPinCode
    val duressAction: DuressAction get() = profileManager.duressAction

    private var pinLockoutJob: Job? = null

    init {
        val lockoutUntil = prefs.getLong("pin_lockout_until_timestamp", 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            val remainingSec = ((lockoutUntil - now) / 1000L).toInt()
            pinLockoutRemainingSeconds = remainingSec
            startLockoutCountdown()
        }
    }

    /**
     * Проверяет, активен ли 48-часовой панический локдаун.
     */
    fun isPanicLockoutActive(): Boolean {
        return System.currentTimeMillis() < panicLockoutUntilTimestamp
    }

    /**
     * Возвращает форматированную строку оставшегося времени панической блокировки.
     */
    fun getPanicLockoutRemainingTimeString(lang: String = "RU"): String {
        val diff = panicLockoutUntilTimestamp - System.currentTimeMillis()
        if (diff <= 0) return if (lang == "RU") "Истекло" else "Expired"
        val hours = diff / (1000L * 3600L)
        val minutes = (diff % (1000L * 3600L)) / (1000L * 60L)
        return if (lang == "RU") "$hours ч. $minutes мин." else "$hours hrs $minutes mins"
    }

    /**
     * Активирует режим экстренной защиты (паника) с блокировкой защищённого контура на 48 часов.
     */
    fun triggerPanicLockdown48Hours() {
        val fortyEightHoursMs = 48L * 3600L * 1000L
        panicLockoutUntilTimestamp = System.currentTimeMillis() + fortyEightHoursMs
        isPanicRecoveryRequired = true
        prefs.edit()
            .putLong("panic_lockout_until_timestamp", panicLockoutUntilTimestamp)
            .putBoolean("panic_recovery_required", true)
            .apply()
    }

    /**
     * Снимает паническую блокировку при успешном вводе BIP-39 сид-фразы.
     */
    fun clearPanicLockdown() {
        panicLockoutUntilTimestamp = 0L
        isPanicRecoveryRequired = false
        prefs.edit()
            .remove("panic_lockout_until_timestamp")
            .remove("panic_recovery_required")
            .apply()
    }

    /**
     * Экспоненциальная шкала задержки при неверных попытках ввода PIN:
     * 1-я: 15с, 2-я: 30с, 3-я: 60с, 4-я: 120с, 5-я: 240с, 6-я: 480с, далее максимум 960с.
     */
    fun calculatePinRetryDelay(failedAttempts: Int): Int {
        return when (failedAttempts) {
            1 -> 15
            2 -> 30
            3 -> 60
            4 -> 120
            5 -> 240
            6 -> 480
            else -> minOf(960, 480 * (1 shl minOf(failedAttempts - 6, 2)))
        }
    }

    private fun startLockoutCountdown() {
        pinLockoutJob?.cancel()
        pinLockoutJob = scope.launch {
            while (pinLockoutRemainingSeconds > 0) {
                delay(1000L)
                pinLockoutRemainingSeconds--
            }
            pinLockoutTotalSeconds = 0
            prefs.edit()
                .remove("pin_lockout_until_timestamp")
                .remove("pin_lockout_total_seconds")
                .apply()
        }
    }

    /**
     * Регистрирует ошибочный ввод PIN, сохраняет время блокировки и возвращает задержку в секундах.
     */
    fun registerPinFailure(): Int {
        pinFailedAttempts++
        val delaySeconds = calculatePinRetryDelay(pinFailedAttempts)
        pinLockoutTotalSeconds = delaySeconds
        pinLockoutRemainingSeconds = delaySeconds

        val lockoutUntil = System.currentTimeMillis() + delaySeconds * 1000L
        prefs.edit()
            .putLong("pin_lockout_until_timestamp", lockoutUntil)
            .putInt("pin_lockout_total_seconds", delaySeconds)
            .putInt("pin_failed_attempts", pinFailedAttempts)
            .apply()

        startLockoutCountdown()
        return delaySeconds
    }

    /**
     * Сбрасывает счетчики ошибок при успешном вводе PIN.
     */
    fun registerPinSuccess() {
        pinFailedAttempts = 0
        pinLockoutRemainingSeconds = 0
        pinLockoutTotalSeconds = 0
        pinLockoutJob?.cancel()
        pinLockoutJob = null
        prefs.edit()
            .remove("pin_lockout_until_timestamp")
            .remove("pin_lockout_total_seconds")
            .remove("pin_failed_attempts")
            .apply()
    }

    fun resetPinLockout() {
        registerPinSuccess()
    }

    fun updatePinCode(newPin: String) {
        pinCode = newPin
        securePrefs.putString("pin_code", newPin)
    }

    fun toggleScreenSecurity(enabled: Boolean) {
        isScreenSecurityEnabled = enabled
        profileManager.setScreenSecurity(enabled)
        onScreenSecurityChanged?.invoke(enabled)
    }

    fun updateDuressConfig(pin: String, action: DuressAction) {
        profileManager.updateDuressConfig(pin, action)
    }

    fun isDuressPin(pin: String): Boolean {
        return pin == profileManager.duressPinCode
    }

    /**
     * Проверяет корректность BIP-39 мнемоники и совпадение с зарегистрированным профилем.
     */
    fun verifyAndRecoverFromSeedPhrase(
        seedText: String,
        newPin: String = "",
        currentSeed: String,
        registeredIdentity: CryptoIdentity?,
        lang: String = "RU"
    ): Pair<Boolean, String> {
        val normalized = seedText.trim().lowercase().split("\\s+".toRegex()).joinToString(" ")
        if (!Bip39Helper.validateMnemonic(context, normalized)) {
            return Pair(
                false,
                if (lang == "RU") "Неверная seed-фраза: проверьте 12 слов и контрольную сумму BIP-39"
                else "Invalid seed phrase: check 12 words and checksum"
            )
        }

        val derived = Bip39Helper.deriveCryptoIdentity(normalized)
        if (currentSeed.isNotBlank() && !currentSeed.equals(normalized, ignoreCase = true)) {
            if (registeredIdentity != null && registeredIdentity.identityFingerprint != derived.identityFingerprint) {
                return Pair(
                    false,
                    if (lang == "RU") "Введенная seed-фраза не совпадает с зарегистрированным аккаунтом!"
                    else "Entered seed phrase does not match registered account!"
                )
            }
        }

        clearPanicLockdown()
        registerPinSuccess()

        if (newPin.isNotBlank()) {
            updatePinCode(newPin)
        }

        return Pair(
            true,
            if (lang == "RU") "Доступ к секретному контуру успешно восстановлен!"
            else "Access to secret vault successfully recovered!"
        )
    }
}
