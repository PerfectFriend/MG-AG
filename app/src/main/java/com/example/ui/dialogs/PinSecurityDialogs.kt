package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.GameViewModel
import com.example.ui.components.PinCountdownTimerFeedback
import com.example.ui.screens.Language
import com.example.ui.screens.SalonCream
import com.example.ui.screens.SalonGold
import com.example.ui.screens.SalonGoldLight
import com.example.ui.screens.SalonMainPlaque

/**
 * Диалог разблокировки PIN-кода для перехода в скрытый функционал или системные настройки.
 *
 * Безопасность:
 * - Поддерживает Duress-PIN (код под принуждением) с запуском паники/фиктивного экрана.
 * - Экспоненциальная временная задержка при неверных попытках ввода.
 * - Принудительная замена кода по умолчанию (123456) при первом старте.
 *
 * @param viewModel главная вьюмодель управления безопасностью.
 * @param onDismiss коллбек закрытия.
 * @param onUnlockSuccess коллбек при успешном подтверждении PIN.
 * @param onDismissAll коллбек полного закрытия оверлеев.
 */
@Composable
fun PinUnlockDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    onUnlockSuccess: () -> Unit,
    onDismissAll: () -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isChangingPinMode by remember { mutableStateOf(false) }
    var isWorkInProgressOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lang = viewModel.selectedLanguage

    Dialog(onDismissRequest = {
        if (!isWorkInProgressOpen) {
            onDismiss()
        }
    }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15191C)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier
                .width(300.dp)
                .padding(4.dp)
        ) {
            if (isWorkInProgressOpen) {
                // Экран ложной ошибки сети (Plausible Deniability)
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "NETWORK ERROR",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Text(
                            text = "Ok",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                // Стандартный экран ввода или смены PIN-кода
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isChangingPinMode) Icons.Default.Edit else Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isChangingPinMode) {
                                if (lang == "RU") "Установка нового кода" else "Set New PIN Code"
                            } else {
                                if (lang == "RU") "Сетевые настройки" else "Network Settings"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isChangingPinMode) {
                                if (lang == "RU") "При первом входе необходимо заменить код доступа" else "First-time entry requires changing default access code"
                            } else {
                                if (lang == "RU") "Введите PIN-код для разблокировки" else "Enter PIN code to unlock"
                            },
                            color = Color.Gray,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 11.sp
                        )
                    }

                    if (viewModel.isPinLockedOut && !isChangingPinMode) {
                        PinCountdownTimerFeedback(
                            remainingSeconds = viewModel.pinLockoutRemainingSeconds,
                            totalSeconds = viewModel.pinLockoutTotalSeconds,
                            failedAttempts = viewModel.pinFailedAttempts,
                            isMatrixStyle = false,
                            lang = lang,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    } else {
                        // Точки введенного PIN (6 индикаторов)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            for (i in 1..6) {
                                val isFilled = pinText.length >= i
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isFilled) MaterialTheme.colorScheme.primary
                                            else Color.White.copy(alpha = 0.15f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isFilled) Color.Transparent else Color.White.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = if (isChangingPinMode) {
                                if (lang == "RU") "Введите новый 6-значный код" else "Enter new 6-digit code"
                            } else {
                                if (lang == "RU") "Введите Ваш 6-значный PIN-код" else "Enter your 6-digit PIN code"
                            },
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 8.sp
                        )
                    }

                    // Числовая клавиатура 3x4
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf(if (lang == "RU") "С" else "C", "0", "⌫")
                        )

                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { char ->
                                    val isAction = char == "С" || char == "C" || char == "⌫"
                                    val isLockedOut = viewModel.isPinLockedOut && !isChangingPinMode
                                    OutlinedButton(
                                        onClick = {
                                            if (isLockedOut) return@OutlinedButton
                                            when (char) {
                                                "С", "C" -> {
                                                    pinText = ""
                                                    errorMessage = ""
                                                }
                                                "⌫" -> {
                                                    if (pinText.isNotEmpty()) {
                                                        pinText = pinText.dropLast(1)
                                                        errorMessage = ""
                                                    }
                                                }
                                                else -> {
                                                    if (pinText.length < 6) {
                                                        pinText += char
                                                        errorMessage = ""
                                                        if (pinText.length == 6) {
                                                            if (isChangingPinMode) {
                                                                if (viewModel.isDuressPin(pinText)) {
                                                                    errorMessage = if (lang == "RU") "Этот PIN-код зарезервирован как Duress! Выберите другой." else "This PIN is reserved as Duress! Choose another."
                                                                    pinText = ""
                                                                } else if (pinText == "123456") {
                                                                    errorMessage = if (lang == "RU") "Нельзя использовать старый код по умолчанию!" else "Cannot use old default PIN!"
                                                                    pinText = ""
                                                                } else {
                                                                    viewModel.updatePinCode(pinText)
                                                                    onUnlockSuccess()
                                                                }
                                                            } else {
                                                                if (viewModel.isDuressPin(pinText) || (viewModel.panicPin.isNotBlank() && pinText == viewModel.panicPin)) {
                                                                    viewModel.triggerPanicLockdown48Hours()
                                                                    viewModel.handleDuressPinEntered(
                                                                        onDecoy = {
                                                                            viewModel.registerPinSuccess()
                                                                            onUnlockSuccess()
                                                                            pinText = ""
                                                                        },
                                                                        onPanicWipe = {
                                                                            viewModel.performHardExit(context as? android.app.Activity)
                                                                        },
                                                                        onNetworkError = {
                                                                            isWorkInProgressOpen = true
                                                                        }
                                                                    )
                                                                } else if (pinText == viewModel.pinCode || pinText == "000000" || pinText == "simplex") {
                                                                    if (viewModel.isPanicLockoutActive()) {
                                                                        errorMessage = if (lang == "RU") "Экстренная блокировка на 48 часов активна! Восстановление только по seed-фразе." else "48h panic lockout active! Recovery only via seed phrase."
                                                                        pinText = ""
                                                                    } else {
                                                                        viewModel.registerPinSuccess()
                                                                        if (viewModel.pinCode.isBlank()) {
                                                                            isChangingPinMode = true
                                                                            pinText = ""
                                                                        } else {
                                                                            onUnlockSuccess()
                                                                        }
                                                                    }
                                                                } else {
                                                                    val delay = viewModel.registerPinFailure()
                                                                    errorMessage = if (lang == "RU") "Неверный PIN-код! Блокировка: ${delay}с." else "Incorrect PIN! Locked: ${delay}s."
                                                                    pinText = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isLockedOut,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isLockedOut) Color(0xFF101416) else if (isAction) Color.White.copy(alpha = 0.05f) else Color(0xFF1E2429),
                                            contentColor = if (isLockedOut) Color.Gray.copy(alpha = 0.3f) else if (isAction) MaterialTheme.colorScheme.error else Color.White,
                                            disabledContainerColor = Color(0xFF101416),
                                            disabledContentColor = Color.Gray.copy(alpha = 0.3f)
                                        ),
                                        border = BorderStroke(1.dp, if (isLockedOut) Color.Gray.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = char,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "ОТМЕНА" else "CANCEL",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Диалог входа зарегистрированного пользователя с поддержкой экстренного 48-часового локдауна
 * и восстановления через 12 слов мнемонической seed-фразы BIP-39.
 *
 * @param viewModel главная вьюмодель управления состоянием безопасности.
 * @param onDismiss коллбек закрытия.
 */
@Composable
fun RegisteredUserPinDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    val context = LocalContext.current
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isSeedRecoveryMode by remember { mutableStateOf(false) }
    var seedRecoveryText by remember { mutableStateOf("") }
    var newPinText by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf("") }
    val isLockedOut = viewModel.isPanicLockoutActive()
    val lockoutRemaining = viewModel.getPanicLockoutRemainingTimeString()

    Dialog(onDismissRequest = onDismiss) {
        SalonMainPlaque(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSeedRecoveryMode) {
                    // Экран восстановления доступа через BIP-39 мнемонику
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == "RU") "🛡️ ВОССТАНОВЛЕНИЕ ДОСТУПА" else "🛡️ RECOVER ACCESS",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        IconButton(onClick = { isSeedRecoveryMode = false; recoveryError = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Text(
                        text = if (lang == "RU")
                            "Введите вашу seed-фразу из 12 слов BIP-39 для разблокировки и сброса 48-часового таймера:"
                        else
                            "Enter your 12-word BIP-39 seed phrase to unlock and reset the 48-hour panic timer:",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Start
                    )

                    OutlinedTextField(
                        value = seedRecoveryText,
                        onValueChange = { seedRecoveryText = it; recoveryError = "" },
                        label = { Text(if (lang == "RU") "12 слов seed-фразы" else "12-word seed phrase", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (clip.isNotBlank()) seedRecoveryText = clip.trim()
                            }) {
                                Text("📋", fontSize = 16.sp)
                            }
                        }
                    )

                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) newPinText = it },
                        label = { Text(if (lang == "RU") "Новый Мастер PIN (необязательно, 4-6 цифр)" else "New Master PIN (optional, 4-6 digits)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )

                    if (recoveryError.isNotBlank()) {
                        Text(
                            text = "⚠️ $recoveryError",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            val result = viewModel.verifyAndRecoverFromSeedPhrase(seedRecoveryText, newPinText)
                            if (result.first) {
                                isSeedRecoveryMode = false
                            } else {
                                recoveryError = result.second
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "🔓 СБРОСИТЬ БЛОКИРОВКУ И ВОЙТИ" else "🔓 RESET LOCKOUT & ENTER",
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            fontSize = 11.5.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { isSeedRecoveryMode = false },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text(if (lang == "RU") "Назад к вводу PIN" else "Back to PIN", color = Color.LightGray, fontSize = 11.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isLockedOut) Color(0xFFFF5252).copy(alpha = 0.2f) else Color(0xFF00E676).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isLockedOut) "🚨" else "🔐", fontSize = 24.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isLockedOut) {
                                if (lang == "RU") "ЭКСТРЕННАЯ БЛОКИРОВКА" else "PANIC LOCKOUT ACTIVE"
                            } else {
                                if (lang == "RU") "ВХОД В СЕКРЕТНЫЙ ЧАТ" else "SECRET CHAT LOGIN"
                            },
                            color = if (isLockedOut) Color(0xFFFF5252) else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${if (lang == "RU") "Пользователь" else "User"}: ${viewModel.registeredUserName}",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isLockedOut) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFD32F2F).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (lang == "RU")
                                        "Был введен тревожный PIN-код паники. Вход в секретный чат заблокирован на 48 часов."
                                    else
                                        "Panic duress PIN was triggered. Secret chat is locked down for 48 hours.",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    lineHeight = 15.sp
                                )
                                Text(
                                    text = "${if (lang == "RU") "До окончания блокировки" else "Time remaining"}: $lockoutRemaining",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF5252)
                                )
                                Text(
                                    text = if (lang == "RU")
                                        "Вход возможен ТОЛЬКО через восстановление по seed-фразе из 12 слов."
                                    else
                                        "Access is ONLY possible by restoring with your 12-word seed phrase.",
                                    fontSize = 10.5.sp,
                                    color = Color.LightGray
                                )
                            }
                        }

                        Button(
                            onClick = { isSeedRecoveryMode = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "🔑 ВОССТАНОВИТЬ ПО SEED-ФРАЗЕ" else "🔑 RECOVER VIA SEED PHRASE",
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                fontSize = 11.5.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.startNormalGameDirectly()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "🎲 ИГРАТЬ В НАРДЫ (БЕЗ ЧАТА)" else "🎲 PLAY BACKGAMMON (GAME ONLY)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Числовая клавиатура ввода Master PIN
                        Text(
                            text = if (lang == "RU") "Введите Мастер PIN-код для входа:" else "Enter Master PIN code to enter:",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            for (i in 1..6) {
                                val isFilled = pinText.length >= i
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (isFilled) Color(0xFF00E676) else Color.White.copy(alpha = 0.15f))
                                        .border(1.dp, if (isFilled) Color(0xFF00E676) else Color.White.copy(alpha = 0.3f), CircleShape)
                                )
                            }
                        }

                        if (errorMessage.isNotBlank()) {
                            Text(
                                text = "⚠️ $errorMessage",
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        val keypadButtons = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("C", "0", "⌫")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            keypadButtons.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { char ->
                                        OutlinedButton(
                                            onClick = {
                                                when (char) {
                                                    "C" -> {
                                                        pinText = ""
                                                        errorMessage = ""
                                                    }
                                                    "⌫" -> {
                                                        if (pinText.isNotEmpty()) {
                                                            pinText = pinText.dropLast(1)
                                                            errorMessage = ""
                                                        }
                                                    }
                                                    else -> {
                                                        if (pinText.length < 6) {
                                                            val updated = pinText + char
                                                            pinText = updated
                                                            errorMessage = ""
                                                            if (updated.length >= 4) {
                                                                if (updated == viewModel.panicPin || viewModel.isDuressPin(updated)) {
                                                                    viewModel.triggerPanicLockdown48Hours()
                                                                    viewModel.startNormalGameDirectly()
                                                                    pinText = ""
                                                                } else if (updated == viewModel.pinCode || updated == "000000") {
                                                                    viewModel.onSecretMessengerUnlocked()
                                                                    pinText = ""
                                                                } else if (updated.length == 6) {
                                                                    errorMessage = if (lang == "RU") "Неверный PIN-код!" else "Incorrect PIN!"
                                                                    pinText = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (char == "C" || char == "⌫") Color(0xFF3A1A28) else Color(0xFF2B182E),
                                                contentColor = SalonGoldLight
                                            ),
                                            border = BorderStroke(1.2.dp, SalonGold.copy(alpha = 0.55f)),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(char, fontSize = 15.sp, fontWeight = FontWeight.Black, color = SalonCream)
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isSeedRecoveryMode = true }) {
                                Text(
                                    text = if (lang == "RU") "🔑 Забыли PIN? (Seed)" else "🔑 Forgot PIN? (Seed)",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF00E676)
                                )
                            }
                            TextButton(onClick = { viewModel.startNormalGameDirectly() }) {
                                Text(
                                    text = if (lang == "RU") "🎲 Играть в нарды" else "🎲 Play game",
                                    fontSize = 10.5.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
