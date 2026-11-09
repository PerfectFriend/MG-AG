package com.example.ui.screens.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameMode
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.GameViewModel

/**
 * Главный контейнер визуализации игрового поля нардов «Шеш-Беш».
 *
 * Архитектура поля:
 * 1. **Левая половина (Left Half Board)**:
 *    - Верх: Пункты 13–18 (индексы 12..17, острие вниз).
 *    - Низ: Пункты 12–7 (индексы 11..6, острие вверх).
 * 2. **Центральный бар (Central Dynamic Bar)**:
 *    - В коротких нардах (Short): хранит выбитые фишки Белых и Черных с возможностью ввода обратно в дом соперника.
 *    - В длинных/бешеных нардах: декоративный шарнирный разделитель из благородного металла.
 * 3. **Правая половина (Right Half Board)**:
 *    - Верх: Пункты 19–24 (индексы 18..23, дом Черных).
 *    - Низ: Пункты 6–1 (индексы 5..0, дом Белых).
 * 4. **Интерактивные оверлеи**:
 *    - Фаза жеребьевки: отображение падающих зариков и статуса жеребьевки.
 *    - Фаза игры: отображение костей текущего игрока на соответствующей половине доски.
 *
 * @param viewModel главная вьюмодель игры.
 * @param modifier модификатор верстки контейнера.
 */
@Composable
fun BackgammonBoardContainer(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val themeSpecs = remember(viewModel.selectedTheme) { BoardThemeSpecs(viewModel.selectedTheme) }
    val isLotteryStage = viewModel.gameStatus == GameStatus.LOT_STAGE1 || viewModel.gameStatus == GameStatus.LOT_STAGE2
    val canNormalRoll = viewModel.gameStatus == GameStatus.BEFORE_ROLL && !(viewModel.isBotOpponentEnabled && viewModel.activePlayer == viewModel.botPlayerColor)

    Box(
        modifier = modifier
            .background(themeSpecs.boardBg, RoundedCornerShape(12.dp))
            .border(4.dp, themeSpecs.boardBorder, RoundedCornerShape(12.dp))
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .then(
                if (isLotteryStage || canNormalRoll) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isLotteryStage) {
                            val status = viewModel.gameStatus
                            if (status == GameStatus.LOT_STAGE1) {
                                if (viewModel.isOnlinePlayActive) {
                                    if (!viewModel.onlineLot1UserRolled && !viewModel.isOnline1UserRolling && !viewModel.isOnline1BotRolling) {
                                        viewModel.rollLot1OnlineLocal()
                                    } else if (viewModel.onlineLot1UserRolled && viewModel.onlineLot1BotRolled) {
                                        val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                                        if (!isTie) {
                                            viewModel.transitionToStage2()
                                        }
                                    }
                                } else {
                                    if (viewModel.diceLot1User == 0 && !viewModel.isRollingDice) {
                                        viewModel.rollLot1()
                                    } else if (viewModel.diceLot1User > 0 && viewModel.diceLot1Bot > 0 && !viewModel.isRollingDice) {
                                        val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                                        if (isTie) {
                                            viewModel.rollLot1()
                                        } else {
                                            viewModel.transitionToStage2()
                                        }
                                    }
                                }
                            } else if (status == GameStatus.LOT_STAGE2) {
                                if (viewModel.isOnlinePlayActive) {
                                    if (viewModel.localPlayerColor == Player.WHITE) {
                                        if (!viewModel.onlineLot2WhiteRolled && !viewModel.isOnline2WhiteRolling) {
                                            viewModel.rollLot2OnlineLocalWhite()
                                        } else if (viewModel.onlineLot2WhiteRolled && viewModel.onlineLot2BlackRolled) {
                                            val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                                            if (!isTie) {
                                                viewModel.applyLotStage2WinnerAndStart()
                                            }
                                        }
                                    } else {
                                        if (viewModel.onlineLot2WhiteRolled && !viewModel.onlineLot2BlackRolled && !viewModel.isOnline2BlackRolling) {
                                            viewModel.rollLot2OnlineLocalBlack()
                                        } else if (viewModel.onlineLot2WhiteRolled && viewModel.onlineLot2BlackRolled) {
                                            val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                                            if (!isTie) {
                                                viewModel.applyLotStage2WinnerAndStart()
                                            }
                                        }
                                    }
                                } else {
                                    if (viewModel.diceLot2White == 0 && !viewModel.isRollingDice) {
                                        viewModel.rollLot2()
                                    } else if (viewModel.diceLot2White > 0 && viewModel.diceLot2Black > 0 && !viewModel.isRollingDice) {
                                        val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                                        if (isTie) {
                                            viewModel.rollLot2()
                                        } else {
                                            viewModel.applyLotStage2WinnerAndStart()
                                        }
                                    }
                                }
                            }
                        } else {
                            viewModel.rollDice()
                        }
                    }
                } else Modifier
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Левая половина доски
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Пункты 13..18
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    for (i in 12..17) {
                        PointCell(
                            absoluteIndex = i,
                            pointingUp = false,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Пункты 12..7
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    for (i in downToRange(11, 6)) {
                        PointCell(
                            absoluteIndex = i,
                            pointingUp = true,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Центральный бар (Divider / Bar для Коротких нард)
            val isShortMode = viewModel.selectedGameMode == GameMode.SHORT
            val barWidth = if (isShortMode) 26.dp else 18.dp
            val whiteBarCount = viewModel.barState[Player.WHITE] ?: 0
            val blackBarCount = viewModel.barState[Player.BLACK] ?: 0
            val isBarSelected = viewModel.selectedPointIndex == -1

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .background(themeSpecs.boardBorder)
                    .border(
                        width = if (isBarSelected) 2.dp else 1.dp,
                        color = if (isBarSelected) Color(0xFFFFD700) else themeSpecs.boardBorder.copy(alpha = 0.5f)
                    )
                    .clickable(enabled = isShortMode && whiteBarCount > 0 && viewModel.activePlayer == Player.WHITE) {
                        viewModel.handleBarClicked()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isShortMode && (whiteBarCount > 0 || blackBarCount > 0)) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (blackBarCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                                    .border(1.dp, Color.Gray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$blackBarCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                        }

                        Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))

                        if (whiteBarCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(
                                        width = if (isBarSelected) 2.dp else 1.dp,
                                        color = if (isBarSelected) Color(0xFFFFD700) else Color.DarkGray,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$whiteBarCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        } else {
                            Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                        Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                        Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                    }
                }
            }

            // Правая половина доски
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Пункты 19..24
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    for (i in 18..23) {
                        PointCell(
                            absoluteIndex = i,
                            pointingUp = false,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Пункты 6..1
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    for (i in downToRange(5, 0)) {
                        PointCell(
                            absoluteIndex = i,
                            pointingUp = true,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (isLotteryStage) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val lotValueLeft = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) viewModel.diceLot1Bot else viewModel.diceLot2Black
                    val lotRollingLeft = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) {
                        viewModel.isRollingDice || viewModel.isOnline1BotRolling
                    } else {
                        viewModel.isRollingDice || viewModel.isOnline2BlackRolling
                    }

                    if (lotValueLeft > 0 || lotRollingLeft) {
                        LotteryDiceCube(
                            value = if (lotValueLeft > 0) lotValueLeft else 1,
                            isRolling = lotRollingLeft,
                            themeId = viewModel.selectedTheme,
                            isLeftPlaySide = true
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val lotValueRight = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) viewModel.diceLot1User else viewModel.diceLot2White
                    val lotRollingRight = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) {
                        viewModel.isRollingDice || viewModel.isOnline1UserRolling
                    } else {
                        viewModel.isRollingDice || viewModel.isOnline2WhiteRolling
                    }

                    if (lotValueRight > 0 || lotRollingRight) {
                        LotteryDiceCube(
                            value = if (lotValueRight > 0) lotValueRight else 1,
                            isRolling = lotRollingRight,
                            themeId = viewModel.selectedTheme,
                            isLeftPlaySide = false
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, start = 8.dp, end = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                LotteryStatusMessage(viewModel)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (viewModel.activePlayer == viewModel.botPlayerColor && viewModel.gameStatus != GameStatus.GAME_OVER) {
                        BoardDiceDisplay(viewModel)
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (viewModel.activePlayer == viewModel.humanPlayerColor && viewModel.gameStatus != GameStatus.GAME_OVER) {
                        BoardDiceDisplay(viewModel)
                    }
                }
            }
        }
    }
}
