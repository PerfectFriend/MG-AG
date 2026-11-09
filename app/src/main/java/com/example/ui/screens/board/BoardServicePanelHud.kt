package com.example.ui.screens.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameMode
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.GameViewModel

/**
 * Правая сервисная панель управления (HUD) игрового стола нард «Шеш-Беш».
 *
 * Архитектура модуля:
 * - Верхняя инструментальная полоса: Отмена хода (Undo), Куб удвоения (Даве), Быстрая смена скина доски, Настройки.
 * - Индикатор текущего хода и оставшихся значений кубиков с визуальной фишкой игрока.
 * - Предупреждающий баннер выбитых фишек на баре (для коротких нард).
 * - Табло сброшенных фишек из дома (Белые vs Черные / Бот).
 * - Контекстный блок действия: кнопка старта матча при начальной готовности, либо кнопки броска кубиков / выноса фишки.
 * - Модуль онлайн-радиоприемника со сменой станций и управлением воспроизведением.
 *
 * @param viewModel главная вьюмодель игры.
 * @param onDoublingClick обратный вызов для открытия модального окна куба удвоения ставки.
 * @param onSettingsClick обратный вызов для открытия параметров и конфигурации игры.
 * @param onRadioLanguageClick обратный вызов для открытия диалога выбора радиостанций.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardServicePanelHud(
    viewModel: GameViewModel,
    onDoublingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRadioLanguageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = viewModel.selectedLanguage

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Верхняя панель быстрых действий: [Отмена] [Даве xN] [Скин] [Настройки]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка отмены последнего хода (только стрелочка хода назад)
            Button(
                onClick = { viewModel.undoMove() },
                enabled = viewModel.canUndo,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.canUndo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (viewModel.canUndo) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("undo_move_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = if (lang == "RU") "Отмена хода" else "Undo Move",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Кнопка вызова куба удвоения ставки (Даве) - отлично, не меняем
            Button(
                onClick = onDoublingClick,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.stakeMultiplier > 1) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (viewModel.stakeMultiplier > 1) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("doubling_cube_button")
            ) {
                Text(
                    text = "🎲 x${viewModel.stakeMultiplier}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }

            // Быстрый переключатель темы оформления доски (только крупная палитра)
            Button(
                onClick = { viewModel.cycleNextTheme() },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("quick_theme_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = if (lang == "RU") "Сменить тему" else "Change Theme",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Кнопка перехода в окно настроек (шестеренка крупнее)
            Button(
                onClick = onSettingsClick,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = if (lang == "RU") "Настройки" else "Settings",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Кнопка возврата из игрового стола в вертикальное меню (иконка выхода крупнее)
            Button(
                onClick = { viewModel.exitGameToMenu() },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("exit_to_menu_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = if (lang == "RU") "В главное меню" else "To Main Menu",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Индикатор хода и кубиков
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        ) {
                            CheckerPiece(
                                player = viewModel.activePlayer,
                                themeId = viewModel.selectedTheme,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column {
                            Text(
                                text = if (lang == "RU") {
                                    "ХОД: " + (if (viewModel.activePlayer == Player.WHITE) "БЕЛЫЕ (ВЫ)" else "ЧЕРНЫЕ (БОТ)")
                                } else {
                                    "TURN: " + (if (viewModel.activePlayer == Player.WHITE) "WHITE (YOU)" else "BLACK (BOT)")
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val statusDesc = when (viewModel.gameStatus) {
                                GameStatus.BEFORE_ROLL -> if (lang == "RU") "Ожидание броска" else "Waiting for roll"
                                GameStatus.PLAYER_MOVE -> if (lang == "RU") "Осталось ходов: ${viewModel.remainingDice.size}" else "Moves left: ${viewModel.remainingDice.size}"
                                GameStatus.GAME_OVER -> if (lang == "RU") "Игра окончена" else "Game Over"
                                else -> ""
                            }
                            if (statusDesc.isNotEmpty()) {
                                Text(
                                    text = statusDesc,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Плашки выпавших значений
                    if (viewModel.gameStatus == GameStatus.PLAYER_MOVE && viewModel.remainingDice.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (die in viewModel.remainingDice) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$die",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Предупреждение о фишках на баре для коротких нард
                if (viewModel.selectedGameMode == GameMode.SHORT) {
                    val activeBar = viewModel.barState[viewModel.activePlayer] ?: 0
                    if (activeBar > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE65100).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFFE65100), RoundedCornerShape(4.dp))
                                .clickable(enabled = viewModel.activePlayer == Player.WHITE) {
                                    viewModel.handleBarClicked()
                                }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "RU") "⚠️ НА БАРЕ: $activeBar ФИШ. (ВЫБЕЙТЕСЬ)" else "⚠️ ON BAR: $activeBar (ENTER)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
        }

        // Табло сброшенных фишек
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚪ " + (if (lang == "RU") "ВЫ" else "YOU") + ": ${viewModel.borneOffCount[Player.WHITE] ?: 0}/15",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                VerticalDivider(
                    modifier = Modifier.height(16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = "⚫ " + (if (lang == "RU") "БОТ" else "BOT") + ": ${viewModel.borneOffCount[Player.BLACK] ?: 0}/15",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Экран старта или текущего хода
        if (!viewModel.isMatchStarted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.5.dp, Color(0xFFD3A373).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "ГОТОВ К ИГРЕ! 🎲" else "READY TO PLAY! 🎲",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        
                        val modeLabel = when (viewModel.selectedWelcomeMode) {
                            0 -> if (lang == "RU") "Бот-зануда" else "Dull Bot Opponent"
                            1 -> if (lang == "RU") "Один экран" else "P2P Single Screen"
                            2 -> if (lang == "RU") "Безопасный онлайн" else "Secure Online Play"
                            else -> "PvP"
                        }
                        
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (lang == "RU") "РЕЖИМ:" else "MODE:", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(modeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = viewModel.userName,
                                onValueChange = { viewModel.userName = it },
                                label = { Text(if (lang == "RU") "Имя игрока" else "Player Name", fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("hud_player_name_input"),
                                shape = RoundedCornerShape(6.dp)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔒", fontSize = 9.sp)
                                Text(
                                    text = if (lang == "RU") "Stealth: автономный запуск без сети" else "Stealth: offline safe mode",
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = { viewModel.onStartGameBtnPressed() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("onstart_game_btn"),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "НАЧАТЬ ИГРУ 🎲" else "START GAME 🎲",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        } else {
            val canBearOff = remember(viewModel.selectedPointIndex, viewModel.reachablePaths) {
                viewModel.reachablePaths.any { it.finalTo >= 24 }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Кнопка броска кубиков
                if (viewModel.gameStatus == GameStatus.BEFORE_ROLL && viewModel.activePlayer == Player.WHITE && !viewModel.isRollingDice) {
                    Button(
                        onClick = { viewModel.rollDice() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sidebar_roll_dice_btn"),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == "RU") "БРОСИТЬ КУБИКИ" else "ROLL DICE",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                // Кнопка выноса фишки
                if (canBearOff) {
                    Button(
                        onClick = { viewModel.handleBearOffClicked() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD3A373)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sidebar_bear_off_btn"),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == "RU") "ВЫБРОСИТЬ ФИШКУ" else "BEAR OFF CHIP",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }

                // Компактная панель радио в HUD: одна большая кнопка Play/Stop с длительным нажатием для настроек
                val radio = viewModel.radioManager
                val isBroadcasting = radio.isPlaying || radio.isPlayingTelegram || radio.isPlayingLocal
                val currentTrackName = radio.currentChannel?.name ?: (if (lang == "RU") "Кибер-Радио" else "Cyber Radio")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = {
                                if (isBroadcasting) {
                                    radio.stop()
                                } else {
                                    val channelToPlay = radio.currentChannel ?: radio.channels.firstOrNull()
                                    if (channelToPlay != null) radio.play(channelToPlay)
                                }
                            },
                            onLongClick = {
                                onRadioLanguageClick()
                            }
                        )
                        .border(
                            1.dp,
                            if (isBroadcasting) Color(0xFF00FF66) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .testTag("hud_compact_radio_button"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBroadcasting) Color(0xFF0A2216) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (isBroadcasting) Color(0xFF00FF66) else MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBroadcasting) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = if (isBroadcasting) "Stop Radio" else "Play Radio",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBroadcasting) (if (lang == "RU") "■ СТОП РАДИО" else "■ STOP RADIO") else (if (lang == "RU") "▶ ВКЛ РАДИО" else "▶ PLAY RADIO"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isBroadcasting) Color(0xFF00FF66) else Color.White
                                )
                                Text(
                                    text = if (isBroadcasting) "🟢 $currentTrackName" else (if (lang == "RU") "Удерживайте для настроек" else "Hold for settings"),
                                    fontSize = 8.5.sp,
                                    color = if (isBroadcasting) Color(0xFF98EE99) else Color(0xFF8B949E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onRadioLanguageClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Radio Settings",
                                tint = if (isBroadcasting) Color(0xFF00FF66) else Color(0xFF8B949E),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
