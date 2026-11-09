package com.example.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameMode
import com.example.ui.GameViewModel
import com.example.ui.dialogs.localizedLabel
import com.example.ui.screens.Language

// Dashboard Palette
private val DashboardBg = Color(0xFF0A0F18)
private val CardBg = Color(0xFF121926)
private val CardBorder = Color(0xFF1F2C42)
private val AccentGold = Color(0xFFE5B53B)
private val AccentCyan = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00FF66)
private val TextDim = Color(0xFF8B949E)

/**
 * Полноэкранный альбомный дашборд настроек без необходимости вертикального скролла.
 * Открывается прямо из игрового стола без смены ориентации экрана.
 */
@Composable
fun SettingsFullScreenDashboard(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    BackHandler {
        onDismiss()
    }

    val lang = viewModel.selectedLanguage
    var activeTab by remember { mutableIntStateOf(0) }

    val tabTitles = listOf(
        if (lang == "RU") "ИГРА & ПРАВИЛА" else "GAME & RULES",
        if (lang == "RU") "ОФОРМЛЕНИЕ" else "VISUALS",
        if (lang == "RU") "ЗВУК & РАДИО" else "SOUND & RADIO",
        if (lang == "RU") "БЕЗОПАСНОСТЬ" else "SECURITY",
        if (lang == "RU") "ПРОФИЛЬ & ЯЗЫК" else "PROFILE & LANG"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("settings_fullscreen_dashboard")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ========================================================
            // HEADER: Title, Tabs & Close Button (Height ~38dp)
            // ========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (lang == "RU") "НАСТРОЙКИ" else "SETTINGS",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentGold
                    )
                }

                // 5 Tabs
                Row(
                    modifier = Modifier
                        .background(Color(0xFF070B12), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = activeTab == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) AccentGold else Color.Transparent)
                                .clickable { activeTab = index }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                .testTag("settings_tab_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isSelected) Color.Black else TextDim
                            )
                        }
                    }
                }

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF1E2838), CircleShape)
                        .testTag("settings_dashboard_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Dashboard",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ========================================================
            // BODY: 3-COLUMN NO-SCROLL LAYOUT FITTING FULLSCREEN
            // ========================================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> TabGameAndRules(viewModel = viewModel, lang = lang)
                    1 -> TabVisualsAndThemes(viewModel = viewModel, lang = lang)
                    2 -> TabSoundAndRadio(viewModel = viewModel, lang = lang)
                    3 -> TabSecurityAndNetwork(viewModel = viewModel, lang = lang)
                    4 -> TabProfileAndLanguage(viewModel = viewModel, lang = lang)
                }
            }
        }
    }
}

// ============================================================================
// TAB 0: GAME & RULES (3 Columns: Mode, AI/Bot, Undo/Rules)
// ============================================================================
@Composable
private fun TabGameAndRules(viewModel: GameViewModel, lang: String) {
    var botDifficulty by remember { mutableStateOf("NORMAL") }
    var turnTimerLimit by remember { mutableIntStateOf(30) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Col 1: Mode & Stake
        DashboardCard(title = if (lang == "RU") "Режим нард & Ставка" else "Game Mode & Stakes", modifier = Modifier.weight(1f)) {
            val modes = listOf(
                GameMode.MAD to (if (lang == "RU") "Бешеные" else "Mad"),
                GameMode.LONG to (if (lang == "RU") "Длинные" else "Long"),
                GameMode.SHORT to (if (lang == "RU") "Короткие" else "Short")
            )
            modes.forEach { (mode, name) ->
                val isSel = viewModel.selectedGameMode == mode
                DashboardSelectableRow(label = name, isSelected = isSel) {
                    viewModel.selectedGameMode = mode
                    viewModel.startNewGame(mode)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1422), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (lang == "RU") "Куб удвоения (Даве)" else "Doubling Stake",
                    fontSize = 10.sp,
                    color = Color.White
                )
                Surface(
                    color = if (viewModel.stakeMultiplier > 1) Color(0xFFD32F2F) else Color(0xFF1E3A5F),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "x${viewModel.stakeMultiplier}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Col 2: Opponent & Automation
        DashboardCard(title = if (lang == "RU") "ИИ-Бот & Поведение" else "AI Opponent & Automations", modifier = Modifier.weight(1f)) {
            DashboardToggleRow(
                label = if (lang == "RU") "Играть против бота" else "Play vs AI Bot",
                checked = viewModel.isBotOpponentEnabled,
                onCheckedChange = { viewModel.isBotOpponentEnabled = it }
            )
            DashboardToggleRow(
                label = if (lang == "RU") "Автобросок костей" else "Auto-roll dice",
                checked = viewModel.isAutoRollEnabled,
                onCheckedChange = { viewModel.isAutoRollEnabled = it }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lang == "RU") "Сложность ИИ:" else "AI Level:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("EASY" to "Легкий", "NORMAL" to "Норма", "MASTER" to "Мастер").forEach { (code, label) ->
                    val isSel = botDifficulty == code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) AccentGold else Color(0xFF0D1422))
                            .clickable { botDifficulty = code }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "RU") label else code,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.Black else TextDim
                        )
                    }
                }
            }
        }

        // Col 3: Fairplay & Undo
        DashboardCard(title = if (lang == "RU") "Правила & Отмена ходов" else "Rules & Move Undo", modifier = Modifier.weight(1f)) {
            DashboardToggleRow(
                label = if (lang == "RU") "Отмена хода (Игрок 1)" else "Allow Undo (P1)",
                checked = viewModel.allowUndoPlayer1,
                onCheckedChange = { viewModel.updateAllowUndoPlayer1(it) }
            )
            DashboardToggleRow(
                label = if (lang == "RU") "Согласие (Игрок 2)" else "Opponent Consent (P2)",
                checked = viewModel.allowUndoPlayer2,
                onCheckedChange = { viewModel.updateAllowUndoPlayer2(it) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = if (viewModel.isUndoAllowed) Color(0xFF1B4D2B) else Color(0xFF5A1E24),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (viewModel.isUndoAllowed) Icons.Default.Check else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (viewModel.isUndoAllowed) AccentGreen else Color(0xFFFF5252),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (viewModel.isUndoAllowed) {
                            if (lang == "RU") "Отмена хода активна" else "Move undo enabled"
                        } else {
                            if (lang == "RU") "Отмена заблокирована" else "Undo disabled by rules"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 1: VISUALS & THEMES (3 Columns: Themes, Checkers, Animations)
// ============================================================================
@Composable
private fun TabVisualsAndThemes(viewModel: GameViewModel, lang: String) {
    var highlightMoves by remember { mutableStateOf(true) }
    var dicePhysics3D by remember { mutableStateOf(true) }
    var smoothSliding by remember { mutableStateOf(true) }
    var hapticFeedback by remember { mutableStateOf(true) }

    val themes = listOf(
        Triple("wood", Color(0xFF8D5B33), if (lang == "RU") "Классическое дерево" else "Classic Wood"),
        Triple("marine", Color(0xFF00ACC1), if (lang == "RU") "Морской бриз" else "Marine Breeze"),
        Triple("malachite", Color(0xFF2E7D32), if (lang == "RU") "Уральский малахит" else "Ural Malachite"),
        Triple("cosmic", Color(0xFF9C27B0), if (lang == "RU") "Глубокий космос" else "Deep Cosmic"),
        Triple("psychedelic", Color(0xFFFF5722), if (lang == "RU") "Психоделика" else "Cyber Psychedelic")
    )

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Col 1: Themes
        DashboardCard(title = if (lang == "RU") "Скины доски" else "Board Skins", modifier = Modifier.weight(1.1f)) {
            themes.forEach { (id, color, name) ->
                val isSel = viewModel.selectedTheme.lowercase() == id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSel) Color(0xFF1E2838) else Color.Transparent)
                        .clickable { viewModel.updateTheme(id) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, if (isSel) AccentGold else Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = name,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) AccentGold else Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSel) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentGold, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // Col 2: Checkers & Lights
        DashboardCard(title = if (lang == "RU") "Фишки & Подсветка" else "Checkers & Highlights", modifier = Modifier.weight(1f)) {
            DashboardToggleRow(
                label = if (lang == "RU") "Подсветка возможных ходов" else "Highlight legal moves",
                checked = highlightMoves,
                onCheckedChange = { highlightMoves = it }
            )
            DashboardToggleRow(
                label = if (lang == "RU") "3D физика вращения костей" else "3D Dice physics",
                checked = dicePhysics3D,
                onCheckedChange = { dicePhysics3D = it }
            )
            DashboardToggleRow(
                label = if (lang == "RU") "Плавное скольжение фишек" else "Smooth checker slide",
                checked = smoothSliding,
                onCheckedChange = { smoothSliding = it }
            )
        }

        // Col 3: Polish & Haptics
        DashboardCard(title = if (lang == "RU") "Эффекты & Отклик" else "Effects & Haptics", modifier = Modifier.weight(1f)) {
            DashboardToggleRow(
                label = if (lang == "RU") "Тактильный виброотклик" else "Haptic feedback",
                checked = hapticFeedback,
                onCheckedChange = { hapticFeedback = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Color(0xFF0F1E2E),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = if (lang == "RU") "Киберпанк HUD" else "Cyberpunk HUD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    Text(
                        text = if (lang == "RU") "Сверхвысокая частота обновления и неоновый контраст активны." else "High-refresh neon matrix styling active.",
                        fontSize = 8.5.sp,
                        color = TextDim
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 2: SOUND & RADIO (3 Columns: Effects, Radio Player, Station / Telegram)
// ============================================================================
@Composable
private fun TabSoundAndRadio(viewModel: GameViewModel, lang: String) {
    var diceSounds by remember { mutableStateOf(true) }
    var checkerSounds by remember { mutableStateOf(true) }
    val radio = viewModel.radioManager

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Col 1: Sound Effects
        DashboardCard(title = if (lang == "RU") "Звуковые эффекты" else "Sound FX", modifier = Modifier.weight(1f)) {
            DashboardToggleRow(
                label = if (lang == "RU") "Звук переката костей" else "Dice roll sound",
                checked = diceSounds,
                onCheckedChange = { diceSounds = it }
            )
            DashboardToggleRow(
                label = if (lang == "RU") "Удар фишки о доску" else "Checker drop sound",
                checked = checkerSounds,
                onCheckedChange = { checkerSounds = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            var isAudioMuted by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lang == "RU") "Заглушить всё" else "Mute Radio",
                    fontSize = 10.sp,
                    color = Color.White
                )
                Switch(
                    checked = isAudioMuted,
                    onCheckedChange = {
                        isAudioMuted = it
                        if (it) radio.stop()
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // Col 2: Online Radio Player
        DashboardCard(title = if (lang == "RU") "Кибер-Радио Станции" else "Cyber-Radio Stations", modifier = Modifier.weight(1f)) {
            val currentCh = radio.currentChannel
            val isBroadcasting = radio.isPlaying || radio.isPlayingTelegram || radio.isPlayingLocal
            Text(
                text = currentCh?.name ?: (if (lang == "RU") "Остановлено" else "Stopped"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        if (isBroadcasting) {
                            radio.stop()
                        } else {
                            currentCh?.let { radio.play(it) } ?: radio.playRandomChannel()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Icon(
                        imageVector = if (isBroadcasting) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isBroadcasting) (if (lang == "RU") "Стоп" else "Stop") else (if (lang == "RU") "Старт" else "Play"), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { radio.playRandomChannel() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838), contentColor = Color.White),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (lang == "RU") "След." else "Next", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isBroadcasting) "● ВЕЩАНИЕ АКТИВНО (128 KBPS)" else "○ РАДИО В РЕЖИМЕ ОЖИДАНИЯ",
                fontSize = 8.sp,
                color = if (isBroadcasting) AccentGreen else TextDim,
                fontWeight = FontWeight.Bold
            )
        }

        // Col 3: Fullscreen Deck & Telegram
        DashboardCard(title = if (lang == "RU") "Telegram & Дэк Радио" else "Telegram & Cyber Deck", modifier = Modifier.weight(1f)) {
            Text(
                text = if (lang == "RU") "Канал: ${radio.currentTelegramChannel}" else "Channel: ${radio.currentTelegramChannel}",
                fontSize = 9.5.sp,
                color = AccentGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    viewModel.isSettingsDashboardOpen = false
                    viewModel.isRadioFullScreenOpen = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF), contentColor = Color.Black),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (lang == "RU") "ОТКРЫТЬ ДЭК РАДИО" else "OPEN RADIO DECK",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ============================================================================
// TAB 3: SECURITY & NETWORK (3 Columns: Stealth, Tor/VPN, Panic Clear)
// ============================================================================
@Composable
private fun TabSecurityAndNetwork(viewModel: GameViewModel, lang: String) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Col 1: Stealth & Cryptocontainer
        DashboardCard(title = if (lang == "RU") "Стелс & Шифрование" else "Stealth & Encryption", modifier = Modifier.weight(1f)) {
            DashboardToggleRow(
                label = if (lang == "RU") "Режим маскировки (Стелс)" else "Stealth Camouflage",
                checked = viewModel.isStealthModeActive,
                onCheckedChange = { viewModel.isStealthModeActive = it }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1722), RoundedCornerShape(4.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (viewModel.isCryptocontainerMounted) {
                        if (lang == "RU") "Контейнер: Смонтирован" else "Vault: Mounted"
                    } else {
                        if (lang == "RU") "Контейнер: Заблокирован" else "Vault: Locked"
                    },
                    fontSize = 9.5.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Col 2: Network Routing
        DashboardCard(title = if (lang == "RU") "Сетевые шлюзы Tor/VPN" else "Tor/VPN Gateways", modifier = Modifier.weight(1f)) {
            DashboardToggleRow(
                label = if (lang == "RU") "Маршрутизация устройства" else "Full Device Routing",
                checked = viewModel.isFullDeviceRoutingEnabled,
                onCheckedChange = { viewModel.updateFullDeviceRouting(it) }
            )
            DashboardToggleRow(
                label = if (lang == "RU") "Постоянный фоновый мост" else "Persistent Background Bridge",
                checked = viewModel.isBackgroundBridgePersistent,
                onCheckedChange = { viewModel.updateBackgroundBridgePersistent(it) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    viewModel.isSettingsDashboardOpen = false
                    viewModel.isOnionBridgeAdvancedConfigOpen = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(28.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (lang == "RU") "⚙️ ЦЕПИ VPN+TOR & .ONION" else "⚙️ VPN+TOR CHAINS & ONION",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Col 3: Panic Wipe & Exit
        DashboardCard(title = if (lang == "RU") "Экстренная очистка" else "Emergency Wipe", modifier = Modifier.weight(1f)) {
            Text(
                text = if (lang == "RU") "Немедленно завершает все P2P/Tor соединения и закрывает процесс приложения." else "Terminates all P2P/Tor sessions and kills application process.",
                fontSize = 8.5.sp,
                color = TextDim
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.radioManager.stop()
                    viewModel.vpnManager.stopVpnConnectionSimulation()
                    viewModel.setTorEnabledState(false)
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (lang == "RU") "ВЫХОД & ОЧИСТКА" else "PANIC EXIT & WIPE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ============================================================================
// TAB 4: PROFILE & LANGUAGE (3 Columns: Language, Profile, Stats Reset)
// ============================================================================
@Composable
private fun TabProfileAndLanguage(viewModel: GameViewModel, lang: String) {
    val languages = listOf(
        "RU" to "🇷🇺 RU",
        "EN" to "🇬🇧 EN",
        "DE" to "🇩🇪 DE",
        "FR" to "🇫🇷 FR",
        "ES" to "🇪🇸 ES",
        "TR" to "🇹🇷 TR",
        "IT" to "🇮🇹 IT",
        "PT" to "🇵🇹 PT",
        "AR" to "🇸🇦 AR",
        "ZH" to "🇨🇳 ZH"
    )
    val matchHistory by viewModel.matchHistory.collectAsState()

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Col 1: Language
        DashboardCard(title = if (lang == "RU") "Язык интерфейса" else "App Language", modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                languages.take(5).forEach { (code, label) ->
                    val isSel = viewModel.selectedLanguage == code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) AccentGold else Color(0xFF0F1722))
                            .clickable { viewModel.updateLanguage(code) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                languages.drop(5).forEach { (code, label) ->
                    val isSel = viewModel.selectedLanguage == code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) AccentGold else Color(0xFF0F1722))
                            .clickable { viewModel.updateLanguage(code) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                    }
                }
            }
        }

        // Col 2: Player Identity
        DashboardCard(title = if (lang == "RU") "Профиль игрока" else "Player Identity", modifier = Modifier.weight(1f)) {
            Text(
                text = if (lang == "RU") "Позывной:" else "Callsign:",
                fontSize = 9.sp,
                color = TextDim
            )
            Text(
                text = viewModel.registeredUserName.ifBlank { if (lang == "RU") "Игрок Салона" else "Salon Master" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = AccentGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1722), RoundedCornerShape(4.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lang == "RU") "Текущий цвет фишек:" else "Current Checkers:",
                    fontSize = 9.sp,
                    color = TextDim
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = viewModel.humanPlayerColor.localizedLabel(lang),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Col 3: Statistics & Reset
        DashboardCard(title = if (lang == "RU") "Статистика партий" else "Match Records", modifier = Modifier.weight(1f)) {
            Text(
                text = if (lang == "RU") "Всего сыграно партий: ${matchHistory.size}" else "Total matches played: ${matchHistory.size}",
                fontSize = 9.5.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.clearStatsHistory() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E2723), contentColor = Color(0xFFFFCC80)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(30.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (lang == "RU") "СБРОСИТЬ СТАТИСТИКУ" else "CLEAR STATS",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================================================
// REUSABLE DASHBOARD CARDS & ROWS
// ============================================================================
@Composable
private fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = AccentGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = CardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun DashboardSelectableRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) AccentGold.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (isSelected) AccentGold else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) AccentGold else Color.White
        )
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AccentGold, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun DashboardToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(22.dp).padding(start = 2.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = Color(0xFF090D14)
            )
        )
    }
}
