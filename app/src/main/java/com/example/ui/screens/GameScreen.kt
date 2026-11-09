package com.example.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GameOutcomeType
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.GameViewModel
import com.example.ui.dialogs.*
import com.example.ui.screens.board.BackgammonBoardContainer
import com.example.ui.screens.board.BoardServicePanelHud
import com.example.ui.screens.chat.SimpleXFullScreenChat
import com.example.ui.screens.settings.SettingsFullScreenDashboard
import com.example.ui.screens.welcome.RomanticBackgammonSplashScreen
import com.example.ui.screens.welcome.WelcomeScreenLayout

/**
 * Главный экран и центральный координатор приложения «Шеш-Беш» (Длинные и Короткие нарды).
 *
 * Архитектурные функции модуля:
 * - Управление жизненным циклом и сменой ориентации экрана (альбомная для стола, портретная для чата и онбординга).
 * - Маршрутизация полноэкранных состояний: Заставка (Splash), Онбординг, Режим легенды (Matrix No Game), Салонное меню.
 * - Компоновка игрового пространства: доска нард слева (weight 4.0) и сервисная панель HUD справа (weight 1.5).
 * - Презентация модальных окон: Настройки, Куб удвоения (Даве), Логи партий, Защитный PIN, SimpleX чат, Сетевые мосты Tor/VPN,
 *   Статистика, Правила, Таблица лидеров и финальное 4-уровневое резюме победы (Обычная / Марс / Кокс / Домашний Марс).
 *
 * @param viewModel главная вьюмодель управления состоянием матча и подсистемами.
 */
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val context = LocalContext.current
    val lang = viewModel.selectedLanguage

    var showPinDialog by remember { mutableStateOf(false) }
    var isSimpleXFullScreenChatOpen by remember { mutableStateOf(false) }
    var chatInitialTabSegment by remember { mutableStateOf(0) }
    var showRadioLanguageDialog by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDoublingDialog by remember { mutableStateOf(false) }
    var showResignConfirmDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showAboutRulesDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showLeaderboardDialog by remember { mutableStateOf(false) }
    var showRadioDialog by remember { mutableStateOf(false) }
    var showOnlineMatchmakingDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isCurrentLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isGameModeActive = !viewModel.showSplashScreen &&
            viewModel.isUserRegistered &&
            !viewModel.showOnboardingRegistration &&
            !viewModel.isNoGameModeEnabled &&
            !viewModel.showWelcomeScreen &&
            !isSimpleXFullScreenChatOpen &&
            !viewModel.isSimpleXFullScreenChatOpen &&
            !viewModel.showRegisteredUserPinPrompt &&
            !viewModel.isPanicLockoutActive() &&
            !viewModel.isCryptoWalletFullScreenOpen &&
            !viewModel.isRadioFullScreenOpen &&
            !showPinDialog

    // Системная ориентация экрана: ГОРИЗОНТАЛЬНАЯ строго в режиме игры на доске, ВЕРТИКАЛЬНАЯ во всех остальных экранах
    LaunchedEffect(isGameModeActive) {
        val activity = context as? Activity
        if (isGameModeActive) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val logsListState = rememberLazyListState()
    LaunchedEffect(viewModel.turnLogs.size) {
        if (viewModel.turnLogs.isNotEmpty()) {
            logsListState.animateScrollToItem(viewModel.turnLogs.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        if (viewModel.showSplashScreen) {
            RomanticBackgammonSplashScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.showSplashScreen = false }
            )
        } else if (viewModel.isOnionBridgeAdvancedConfigOpen) {
            com.example.ui.screens.bridge.OnionBridgeAdvancedConfigScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.isOnionBridgeAdvancedConfigOpen = false }
            )
        } else if (viewModel.isRadioFullScreenOpen) {
            com.example.ui.screens.radio.FuturisticCyberpunkRadioScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.isRadioFullScreenOpen = false }
            )
        } else if (viewModel.isSettingsDashboardOpen) {
            SettingsFullScreenDashboard(
                viewModel = viewModel,
                onDismiss = { viewModel.isSettingsDashboardOpen = false }
            )
        } else if (!viewModel.isUserRegistered || viewModel.showOnboardingRegistration) {
            OnboardingRegistrationScreen(
                viewModel = viewModel,
                onComplete = { viewModel.showOnboardingRegistration = false }
            )
        } else if (viewModel.isNoGameModeEnabled) {
            MatrixNoGameScreen(viewModel = viewModel)
        } else if (viewModel.showWelcomeScreen) {
            WelcomeScreenLayout(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
        } else if (!isCurrentLandscape) {
            // Предупреждение о необходимости переворота экрана
            SalonWallpaperBackdrop {
                SalonMainPlaque(modifier = Modifier.widthIn(max = 420.dp).padding(24.dp)) {
                    CircularProgressIndicator(color = SalonGoldLight)
                    Text(
                        text = if (lang == "RU") "🎲 Подготовка игрового салона..." else "🎲 Preparing gaming salon...",
                        color = SalonGoldLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (lang == "RU") "Пожалуйста, поверните устройство в альбомный режим" else "Please rotate your device to landscape mode",
                        color = SalonCream,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Основной рабочий экран игрового стола
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Левая колонка: Игровая доска нард
                Box(
                    modifier = Modifier
                        .weight(4.0f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    BackgammonBoardContainer(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Правая колонка: Сервисная панель управления HUD
                BoardServicePanelHud(
                    viewModel = viewModel,
                    onDoublingClick = { showDoublingDialog = true },
                    onSettingsClick = { viewModel.isSettingsDashboardOpen = true },
                    onRadioLanguageClick = { showRadioLanguageDialog = true },
                    modifier = Modifier.weight(1.5f)
                )
            }
        }
    }

    // --- Модальные диалоги и системные слои ---

    if (showLogsDialog) {
        LogsDialog(
            logs = viewModel.turnLogs,
            onDismiss = { showLogsDialog = false }
        )
    }

    if (showRadioLanguageDialog) {
        Dialog(onDismissRequest = { showRadioLanguageDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .heightIn(max = 300.dp)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (lang == "RU") "Язык вещания & Станции" else "Broadcasting Stations",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val filteredChannels = viewModel.radioManager.channels.filter { channel ->
                        val langLower = lang.lowercase()
                        channel.isCustom || channel.id.startsWith("${langLower}_")
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredChannels) { channel ->
                                val isCurrent = viewModel.radioManager.currentChannel?.id == channel.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable {
                                            viewModel.radioManager.play(channel)
                                            showRadioLanguageDialog = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = channel.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { showRadioLanguageDialog = false },
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Text(if (lang == "RU") "Закрыть" else "Close", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (viewModel.showTalkieErrorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showTalkieErrorDialog = false },
            title = { Text(if (lang == "RU") "ОШИБКА ПОДКЛЮЧЕНИЯ" else "CONNECTION FAILURE", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp) },
            text = { Text(if (lang == "RU") "ERROR 1488: Сбой сквозного шифрования SimpleX/Tor пакетов. VPN туннель разорван удаленным узлом." else "ERROR 1488: E2EE handshaking node breakdown. VPN tunnel reset by peer.", fontSize = 12.sp) },
            confirmButton = {
                Button(onClick = { viewModel.showTalkieErrorDialog = false }) {
                    Text("OK", fontSize = 11.sp)
                }
            }
        )
    }

    if (viewModel.showContactListDialog) {
        Dialog(onDismissRequest = { viewModel.showContactListDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lang == "RU") "Контакты SimpleX" else "SimpleX Contacts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(180.dp).fillMaxWidth()) {
                        items(viewModel.simplexContacts) { contact ->
                            val isSelected = viewModel.talkieSelectedContact?.handle == contact.handle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        viewModel.talkieSelectedContact = contact
                                        viewModel.showContactListDialog = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(contact.handle, fontSize = 8.5.sp, color = Color.Gray)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.showContactListDialog = false },
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text(if (lang == "RU") "Закрыть" else "Close", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (viewModel.showDisconnectOptionsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDisconnectOptionsDialog = false },
            title = { Text(if (lang == "RU") "Сессия SimpleX: Активна" else "SimpleX Session: Active", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = { Text(if (lang == "RU") "Ваши аудиозаписи сохранены локально. Выберите действие:" else "Your E2EE recordings are stored locally. Make your choice:", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.showDisconnectOptionsDialog = false
                        viewModel.talkieState = GameViewModel.TalkieState.OFF
                    }
                ) {
                    Text(if (lang == "RU") "Отключиться" else "Disconnect", fontSize = 11.sp)
                }
            },
            dismissButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.showDisconnectOptionsDialog = false
                        viewModel.simplexVoiceMessages.clear()
                        viewModel.talkieState = GameViewModel.TalkieState.OFF
                    }
                ) {
                    Text(if (lang == "RU") "Стереть и Выйти" else "Clear & Disconnect", fontSize = 11.sp, color = Color.White)
                }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showDoublingDialog) {
        DoublingDialog(
            viewModel = viewModel,
            onDismiss = { showDoublingDialog = false },
            onResignClick = {
                showDoublingDialog = false
                showResignConfirmDialog = true
            }
        )
    }

    if (showResignConfirmDialog) {
        ResignConfirmDialog(
            viewModel = viewModel,
            onDismiss = { showResignConfirmDialog = false },
            onConfirm = {
                showResignConfirmDialog = false
                viewModel.resignMatch()
            }
        )
    }

    if (viewModel.isDoublingOffered && viewModel.doublingProposer != viewModel.humanPlayerColor) {
        IncomingDoublingDialog(
            viewModel = viewModel,
            onAccept = { viewModel.acceptDoubling() },
            onDecline = { viewModel.declineDoubling() }
        )
    }

    if (viewModel.showRegisteredUserPinPrompt) {
        RegisteredUserPinDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.startNormalGameDirectly() }
        )
    }

    if (viewModel.isSimpleXFullScreenChatOpen || isSimpleXFullScreenChatOpen) {
        SimpleXFullScreenChat(
            viewModel = viewModel,
            initialTabSegment = chatInitialTabSegment,
            onDismiss = {
                isSimpleXFullScreenChatOpen = false
                viewModel.isSimpleXFullScreenChatOpen = false
            }
        )
    }

    if (viewModel.showBridgeActivationDialog) {
        BridgeActivationDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.showBridgeActivationDialog = false }
        )
    }

    if (showRadioDialog) {
        RadioDialog(
            viewModel = viewModel,
            onDismiss = { showRadioDialog = false }
        )
    }

    if (showStatsDialog) {
        StatsDialog(
            viewModel = viewModel,
            onDismiss = { showStatsDialog = false }
        )
    }

    if (showAboutRulesDialog) {
        RulesDialog(lang = viewModel.selectedLanguage, onDismiss = { showAboutRulesDialog = false })
    }

    if (showLeaderboardDialog) {
        LeaderboardDialog(lang = viewModel.selectedLanguage, onDismiss = { showLeaderboardDialog = false })
    }

    if (showOnlineMatchmakingDialog) {
        Dialog(onDismissRequest = { if (!viewModel.isMatchmaking) showOnlineMatchmakingDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Language.get("online_search_opponent", lang),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (viewModel.isMatchmaking) {
                            Surface(
                                color = Color(0xFF00E676),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    " SEARCHING ",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.isMatchmaking) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(viewModel.matchmakingLogs) { logLine ->
                                Text(
                                    text = logLine,
                                    color = if (logLine.contains("🎉") || logLine.contains("🏁")) Color(0xFF00FF66) else Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!viewModel.isMatchmaking) {
                            Button(
                                onClick = { viewModel.startOnlineMatchmaking() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text(if (lang == "RU") "БОТ ТРЕНИРОВКА" else "BOT PRACTICE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = {
                                    showOnlineMatchmakingDialog = false
                                    viewModel.showBridgeActivationDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text("TOR P2P", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { showOnlineMatchmakingDialog = false },
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text(Language.get("close", lang), fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                text = "Инициализация безопасной Tor/SMP сессии...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // Финальное диалоговое окно завершения матча (Обычная / Марс / Кокс / Домашний Марс)
    viewModel.winner?.let { winnerPlayer ->
        val outcome = viewModel.gameEndResult ?: viewModel.calculateGameOutcome(winnerPlayer)
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isHumanWinner = winnerPlayer == viewModel.humanPlayerColor
                    Text(
                        text = if (isHumanWinner) {
                            if (lang == "RU") "🏆 ПОБЕДА!" else "🏆 VICTORY!"
                        } else {
                            if (lang == "RU") "ПАРТИЯ ЗАВЕРШЕНА" else "GAME OVER"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isHumanWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val outcomeBadgeColor = when (outcome.outcomeType) {
                        GameOutcomeType.HOME_MARS -> Color(0xFFFFD700)
                        GameOutcomeType.COKE -> Color(0xFFFF6F00)
                        GameOutcomeType.MARS -> MaterialTheme.colorScheme.primary
                        GameOutcomeType.SIMPLE -> MaterialTheme.colorScheme.tertiary
                        GameOutcomeType.RESIGNATION -> MaterialTheme.colorScheme.secondary
                    }
                    val outcomeBadgeIcon = when (outcome.outcomeType) {
                        GameOutcomeType.HOME_MARS -> "👑"
                        GameOutcomeType.COKE -> "⚡"
                        GameOutcomeType.MARS -> "🌟"
                        GameOutcomeType.SIMPLE -> "🏅"
                        GameOutcomeType.RESIGNATION -> "🏳️"
                    }
                    val outcomeTitle = if (lang == "RU") outcome.outcomeType.titleRu else outcome.outcomeType.titleEn

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = outcomeBadgeColor.copy(alpha = 0.15f)),
                        border = BorderStroke(1.5.dp, outcomeBadgeColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$outcomeBadgeIcon $outcomeTitle",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = outcomeBadgeColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "RU") {
                                    "Базовые очки: ${outcome.basePoints} • Ставка: x${outcome.stakeMultiplier} • ИТОГО: ${outcome.totalPoints} ${if (outcome.totalPoints == 1) "очко" else if (outcome.totalPoints in 2..4) "очка" else "очков"}"
                                } else {
                                    "Base: ${outcome.basePoints} pts • Stake: x${outcome.stakeMultiplier} • TOTAL: ${outcome.totalPoints} pts"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (lang == "RU") outcome.outcomeType.descriptionRu else outcome.outcomeType.descriptionEn,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val winnerName = if (winnerPlayer == Player.WHITE) {
                        val gotColor = viewModel.humanPlayerColor.localizedLabel(lang)
                        if (lang == "RU") "Игрок ($gotColor)" else "Player ($gotColor)"
                    } else {
                        val gotColor = viewModel.botPlayerColor.localizedLabel(lang)
                        if (lang == "RU") "Бот ($gotColor)" else "Bot ($gotColor)"
                    }
                    Text(
                        text = "$winnerName ${Language.get("winner", lang)}!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    val whiteCount = viewModel.borneOffCount[if (viewModel.humanPlayerColor == Player.WHITE) Player.WHITE else Player.BLACK] ?: 0
                    val blackCount = viewModel.borneOffCount[if (viewModel.humanPlayerColor == Player.BLACK) Player.WHITE else Player.BLACK] ?: 0
                    Text(
                        text = if (lang == "RU") "Выброшено фишек: Белые $whiteCount/15 • Черные $blackCount/15" else "Borne off: White $whiteCount/15 • Black $blackCount/15",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.startNewGame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("modal_restart_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Play again")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (lang == "RU") "Играть снова" else "Play Again", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
