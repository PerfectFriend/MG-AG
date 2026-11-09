package com.example.ui.screens.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.GameViewModel
import com.example.ui.UserTier
import com.example.ui.components.DiceCube
import com.example.ui.dialogs.localizedLabel
import com.example.ui.screens.Language

/**
 * Индикатор количества выброшенных фишек из дома (Bear-off Score Indicator).
 *
 * @param label подпись («Белые сбросили» / «Черные сбросили»).
 * @param count количество фишек (0..15).
 * @param color цвет фишки.
 */
@Composable
fun ScoreIndicator(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
                .border(0.5.dp, Color.Gray, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: ",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$count/15",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Панель управления броском и статусом текущего хода в нардах «Шеш-Беш».
 *
 * Включает:
 * - Заголовок активного хода (Белые / Черные) с оповещением о перехвате.
 * - Счетчики фишек, выброшенных за пределы доски (15/15).
 * - Наглядные кубики текущего значения и оставшихся ходов.
 * - Кнопку ручного сброса (Bear-off) при попадании фишек в дом.
 *
 * @param viewModel главная вьюмодель игры.
 */
@Composable
fun DiceControlAndStatus(viewModel: GameViewModel) {
    val player = viewModel.activePlayer
    val lang = viewModel.selectedLanguage
    val theme = viewModel.selectedTheme
    val canRoll = viewModel.gameStatus == GameStatus.BEFORE_ROLL && !(viewModel.isBotOpponentEnabled && player == Player.BLACK)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val visualColor = if (player == Player.WHITE) viewModel.humanPlayerColor else viewModel.botPlayerColor
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (visualColor == Player.WHITE) Color(0xFFEFEDE8) else Color(0xFF252528)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .border(
                    width = 1.dp,
                    color = if (visualColor == Player.WHITE) Color.DarkGray.copy(alpha = 0.3f) else Color(0xFFB08D57),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (viewModel.gameStatus == GameStatus.GAME_OVER) Language.get("status_game_over", lang).uppercase() else "${Language.get("score_title", lang).uppercase()}: ${visualColor.localizedLabel(lang)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (visualColor == Player.WHITE) Color.Black else Color(0xFFEBE0D0)
                )

                if (viewModel.isOpponentPlayingTransferred && viewModel.gameStatus != GameStatus.GAME_OVER) {
                    Text(
                        text = Language.get("intercept_turn", lang),
                        color = Color.Red,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val whitePlayer = if (viewModel.humanPlayerColor == Player.WHITE) Player.WHITE else Player.BLACK
            val blackPlayer = if (viewModel.humanPlayerColor == Player.BLACK) Player.WHITE else Player.BLACK
            ScoreIndicator(label = Language.get("white_borne_off", lang), count = viewModel.borneOffCount[whitePlayer] ?: 0, color = Color(0xFFDCDAD4))
            ScoreIndicator(label = Language.get("black_borne_off", lang), count = viewModel.borneOffCount[blackPlayer] ?: 0, color = Color(0xFF333335))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.gameStatus == GameStatus.PLAYER_MOVE && viewModel.remainingDice.isNotEmpty()) {
                viewModel.remainingDice.forEach { valD ->
                    DiceCube(value = valD, isRolling = viewModel.isRollingDice, themeId = theme)
                    Spacer(modifier = Modifier.width(6.dp))
                }
            } else {
                DiceCube(value = viewModel.diceValue1, isRolling = viewModel.isRollingDice, themeId = theme)
                Spacer(modifier = Modifier.width(10.dp))
                DiceCube(value = viewModel.diceValue2, isRolling = viewModel.isRollingDice, themeId = theme)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val canBearOff = remember(viewModel.selectedPointIndex, viewModel.reachablePaths) {
            viewModel.reachablePaths.any { it.finalTo >= 24 }
        }

        if (canBearOff) {
            Button(
                onClick = { viewModel.handleBearOffClicked() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD3A373),
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("bear_off_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = "Exit", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(Language.get("bear_off", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (canRoll) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Language.get("roll_desc", lang).uppercase(),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        viewModel.gameStatus == GameStatus.GAME_OVER -> Language.get("status_game_over", lang)
                        viewModel.isRollingDice -> Language.get("status_rolling", lang)
                        viewModel.isBotOpponentEnabled && player == Player.BLACK -> Language.get("status_bot_turn", lang)
                        viewModel.isAutoPlayerMoveActive && player == Player.WHITE -> Language.get("status_auto_move", lang)
                        else -> Language.get("status_your_turn", lang)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Селектор одного из трех аудиорежимов матча: Рация, Радио, Тишина.
 *
 * @param viewModel главная вьюмодель игры.
 */
@Composable
fun MatchAudioModeSelector(viewModel: GameViewModel) {
    val lang = viewModel.selectedLanguage
    val currentMode = viewModel.matchAudioMode

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (lang == "RU") "АУДИОРЕЖИМ ИГРЫ (Один из трёх)" else "GAME AUDIO MODE (One of three)",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    GameViewModel.MatchAudioMode.WALKIE_TALKIE to (if (lang == "RU") "🎙️ Рация" else "🎙️ Talkie"),
                    GameViewModel.MatchAudioMode.RADIO to (if (lang == "RU") "📻 Радио" else "📻 Radio"),
                    GameViewModel.MatchAudioMode.SILENCE to (if (lang == "RU") "🔇 Тишина" else "🔇 Silence")
                ).forEach { (mode, label) ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable {
                                viewModel.updateMatchAudioMode(mode)
                            }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 8.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка встроенной рации SimpleX Walkie-Talkie для голосовой связи во время игры.
 *
 * @param viewModel главная вьюмодель игры.
 */
@Composable
fun SimplexWalkieTalkieCard(viewModel: GameViewModel) {
    val lang = viewModel.selectedLanguage
    val voiceMsgs = viewModel.simplexVoiceMessages
    val isRecording = viewModel.isRecordingVoice
    val recDuration = viewModel.recordingDurationSec

    val activeInboundMsg = voiceMsgs.firstOrNull { it.sender != "Вы" && it.sender != "You" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isRecording) Color.Red else (if (viewModel.simplexStatus == "CONNECTED") Color.Green else Color.Gray), CircleShape)
                    )
                    Text(
                        text = if (isRecording) {
                            if (lang == "RU") "ЭФИР 🔴" else "ON AIR 🔴"
                        } else {
                            if (lang == "RU") "SimpleX Рация 🎙️" else "SimpleX Walkie-Talkie 🎙️"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { viewModel.isWalkieTalkieMuted = !viewModel.isWalkieTalkieMuted },
                    modifier = Modifier.size(16.dp)
                ) {
                    Text(
                        text = if (viewModel.isWalkieTalkieMuted) "🔇" else "🔊",
                        fontSize = 11.sp
                    )
                }
            }

            if (viewModel.isWalkieTalkieMuted || viewModel.matchAudioMode != GameViewModel.MatchAudioMode.WALKIE_TALKIE) {
                val bannerText = when {
                    viewModel.matchAudioMode == GameViewModel.MatchAudioMode.RADIO ->
                        if (lang == "RU") "📻 Радио активно • Рация на удержании" else "📻 Radio Mode Active • Talkie on hold"
                    viewModel.matchAudioMode == GameViewModel.MatchAudioMode.SILENCE ->
                        if (lang == "RU") "🔇 Режим тишины • Все звуки отключены" else "🔇 Silent Mode • All audio muted"
                    else ->
                        if (lang == "RU") "🔇 Звук входящих сообщений рации выключен" else "🔇 Walkie-talkie audio is muted"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bannerText,
                        color = Color.Red,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (activeInboundMsg != null) {
                val isPlayingMsg = activeInboundMsg.isPlaying
                Button(
                    onClick = {
                        if (!isPlayingMsg) {
                            viewModel.playVoiceMessage(activeInboundMsg)
                        }
                    },
                    enabled = !isPlayingMsg,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        disabledContainerColor = Color.Red.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isPlayingMsg) {
                            if (lang == "RU") "🔊 ЭФИР: ИДЕТ ВОСПРОИЗВЕДЕНИЕ..." else "🔊 AIR: PLAYING..."
                        } else {
                            if (lang == "RU") "▶ ПРОСЛУШАТЬ [${activeInboundMsg.sender}] (6с)" else "▶ LISTEN [${activeInboundMsg.sender}] (6s)"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${activeInboundMsg.sender} (${activeInboundMsg.durationSec}s)",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (viewModel.isVoiceSavingEnabled) {
                                if (lang == "RU") "💾 Криптоконтейнер" else "💾 Cryptocontainer"
                            } else {
                                if (lang == "RU") "🔥 Автоудаление" else "🔥 Auto-destruction"
                            },
                            fontSize = 7.sp,
                            color = if (viewModel.isVoiceSavingEnabled) Color.Green else Color.Red
                        )
                    }
                    LinearProgressIndicator(
                        progress = { activeInboundMsg.playProgress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = if (isPlayingMsg) Color.Red else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "ОЖИДАНИЕ ПРИЕМА..." else "STANDBY (READY TO RX)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            Button(
                onClick = {
                    if (isRecording) {
                        viewModel.stopAndSendVoiceRecording()
                    } else {
                        viewModel.startVoiceRecording()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (isRecording) {
                        if (lang == "RU") "🔴 ЭФИР (00:${String.format("%02d", recDuration)})" else "🔴 ON AIR (00:${String.format("%02d", recDuration)})"
                    } else {
                        if (lang == "RU") "🎙️ ЗАПИСАТЬ СООБЩЕНИЕ" else "🎙️ RECORD VOICE MESSAGE"
                    },
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 9.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") {
                            if (viewModel.isAutoPlayVoiceEnabled) "▶ Автовоспроизведение входящих" else "⏸ Ручное воспроизведение"
                        } else {
                            if (viewModel.isAutoPlayVoiceEnabled) "▶ Auto-play Inbound" else "⏸ Manual Playback"
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = viewModel.isAutoPlayVoiceEnabled,
                        onCheckedChange = { viewModel.isAutoPlayVoiceEnabled = it },
                        modifier = Modifier.scale(0.6f).height(16.dp).width(30.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") {
                            if (viewModel.isVoiceSavingEnabled) "💾 Сохранять в криптоконтейнере" else "🔥 Автоудаление после прослушивания"
                        } else {
                            if (viewModel.isVoiceSavingEnabled) "💾 Save to Crypto-container" else "🔥 Auto-destruct post-play"
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = viewModel.isVoiceSavingEnabled,
                        onCheckedChange = { viewModel.isVoiceSavingEnabled = it },
                        modifier = Modifier.scale(0.6f).height(16.dp).width(30.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "👑 Тариф аккаунта (тест лимитов):" else "👑 Account Tier (limits test):",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        UserTier.values().forEach { tier ->
                            val isSelected = viewModel.currentUserTier == tier
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        0.5.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { viewModel.updateUserTier(tier) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tier.name,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Панель быстрого доступа к Радио и Настройкам игры.
 *
 * @param viewModel главная вьюмодель игры.
 * @param onRadioClick коллбек открытия диалога радио.
 * @param onSettingsClick коллбек открытия диалога настроек.
 */
@Composable
fun LargeQuickControlsRow(
    viewModel: GameViewModel,
    onRadioClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    val isRadioPlaying = viewModel.radioManager.isPlaying
    val activeStationName = viewModel.radioManager.activeTrackTitle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .weight(1.0f)
                .height(44.dp)
                .clickable { onRadioClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (isRadioPlaying) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                1.dp,
                if (isRadioPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        viewModel.radioManager.togglePlay()
                    },
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            if (isRadioPlaying) Color.Red else MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isRadioPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Radio",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lang == "RU") "📻 РАДИО-ЭФИР" else "📻 LIVE RADIO",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRadioPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRadioPlaying) activeStationName else (if (lang == "RU") "ВКЛЮЧИТЬ" else "CLICK TO PLAY"),
                        fontSize = 6.2.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1.0f)
                .height(44.dp)
                .clickable { onSettingsClick() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = if (lang == "RU") "НАСТРОЙКИ" else "SETTINGS",
                        fontSize = 7.2.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (lang == "RU") "УЗЛЫ И VPN" else "NODES & VPN",
                        fontSize = 6.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
