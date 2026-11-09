package com.example.ui.screens.board

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.GameViewModel
import com.example.ui.screens.Language

/**
 * Вспомогательная функция обратного диапазона для размещения пунктов доски.
 */
fun downToRange(start: Int, end: Int): IntArray {
    val size = start - end + 1
    val array = IntArray(size)
    var value = start
    for (i in 0 until size) {
        array[i] = value--
    }
    return array
}

/**
 * Плавающий статус-оверлей предстартовой жеребьевки на доске.
 *
 * @param viewModel главная вьюмодель игры.
 */
@Composable
fun LotteryStatusMessage(viewModel: GameViewModel) {
    val lang = viewModel.selectedLanguage
    val status = viewModel.gameStatus
    val isRolling = viewModel.isRollingDice || viewModel.isOnline1UserRolling || viewModel.isOnline1BotRolling || viewModel.isOnline2WhiteRolling || viewModel.isOnline2BlackRolling

    val titleText: String
    val descText: String

    if (status == GameStatus.LOT_STAGE1) {
        val rawTitle = Language.get("lot_stage1_title", lang)
        titleText = if (rawTitle.startsWith("lot_")) {
            if (lang == "RU") "Этап 1: Определение цвета" else "Stage 1: Color Selection"
        } else rawTitle
        descText = when {
            isRolling -> {
                if (lang == "RU") "Кубики летят на доску..." else "Dices are falling..."
            }
            viewModel.isOnlinePlayActive -> {
                if (viewModel.onlineLot1UserRolled && viewModel.onlineLot1BotRolled) {
                    val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                    if (isTie) {
                        Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Нажмите на доску" else "Tap board")
                    } else {
                        val localWonColors = viewModel.diceLot1User > viewModel.diceLot1Bot
                        if (localWonColors) {
                            if (lang == "RU") "Вы выиграли Белые! Нажмите, чтобы продолжить." else "You won White! Tap board to continue."
                        } else {
                            if (lang == "RU") "Соперник выиграл Белые! Нажмите, чтобы продолжить." else "Opponent won White! Tap board to continue."
                        }
                    }
                } else if (viewModel.onlineLot1UserRolled) {
                    if (lang == "RU") "Ожидаем бросок соперника..." else "Waiting for opponent roll..."
                } else {
                    if (lang == "RU") "Коснитесь доски, чтобы бросить кубик!" else "Touch the board to drop your die!"
                }
            }
            viewModel.diceLot1User > 0 && viewModel.diceLot1Bot > 0 -> {
                val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                if (isTie) {
                    Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы перебросить" else "Touch board to reroll")
                } else {
                    val userWon = viewModel.diceLot1User > viewModel.diceLot1Bot
                    val winText = if (!viewModel.isBotOpponentEnabled) {
                        if (userWon) {
                            if (lang == "RU") "Игрок 1 выиграл Белые! Нажмите для следующего этапа." else "Player 1 won White! Touch board to proceed."
                        } else {
                            if (lang == "RU") "Игрок 2 выиграл Белые! Нажмите для следующего этапа." else "Player 2 won White! Touch board to proceed."
                        }
                    } else {
                        if (userWon) Language.get("lot_result_winner_colors_white", lang) else Language.get("lot_result_winner_colors_black", lang)
                    }
                    winText + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы продолжить" else "Touch board to proceed")
                }
            }
            else -> {
                if (lang == "RU") "Коснитесь доски, чтобы бросить кубики!" else "Touch the board to drop your dice!"
            }
        }
    } else if (status == GameStatus.LOT_STAGE2) {
        val rawTitle = Language.get("lot_stage2_title", lang)
        titleText = if (rawTitle.startsWith("lot_")) {
            if (lang == "RU") "Этап 2: Первый ход" else "Stage 2: Opening Move"
        } else rawTitle
        descText = when {
            isRolling -> {
                if (lang == "RU") "Кубики летят на доску..." else "Dices are falling..."
            }
            viewModel.isOnlinePlayActive -> {
                if (viewModel.onlineLot2WhiteRolled && viewModel.onlineLot2BlackRolled) {
                    val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                    if (isTie) {
                        Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Нажмите на доску" else "Tap board")
                    } else {
                        val whiteWon = viewModel.diceLot2White > viewModel.diceLot2Black
                        val winningColor = if (whiteWon) Player.WHITE else Player.BLACK
                        val isLocalTurnWinner = viewModel.localPlayerColor == winningColor
                        if (isLocalTurnWinner) {
                            if (lang == "RU") "Вы ходите первыми! Нажмите, чтобы начать." else "You go first! Tap board to begin."
                        } else {
                            if (lang == "RU") "Соперник ходит первым! Нажмите, чтобы начать." else "Opponent goes first! Tap board to begin."
                        }
                    }
                } else {
                    if (viewModel.localPlayerColor == Player.WHITE) {
                        if (!viewModel.onlineLot2WhiteRolled) {
                            if (lang == "RU") "Коснитесь доски, чтобы бросить Белый кубик!" else "Touch board to drop White die!"
                        } else {
                            if (lang == "RU") "Ожидаем бросок Черного кубика..." else "Waiting for Black die roll..."
                        }
                    } else {
                        if (!viewModel.onlineLot2WhiteRolled) {
                            if (lang == "RU") "Ожидаем бросок соперника..." else "Waiting for opponent to drop..."
                        } else if (!viewModel.onlineLot2BlackRolled) {
                            if (lang == "RU") "Коснитесь доски, чтобы бросить Черный кубик!" else "Touch board to drop Black die!"
                        } else {
                            ""
                        }
                    }
                }
            }
            viewModel.diceLot2White > 0 && viewModel.diceLot2Black > 0 -> {
                val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                if (isTie) {
                    Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы перебросить" else "Touch board to reroll")
                } else {
                    val whiteWon = viewModel.diceLot2White > viewModel.diceLot2Black
                    val winnerColor = if (whiteWon) Player.WHITE else Player.BLACK
                    val firstMoveText = if (!viewModel.isBotOpponentEnabled) {
                        val localWinnerName = if (winnerColor == viewModel.humanPlayerColor) {
                            Language.get("p2p_player1", lang).replace(" (Белые)", "")
                        } else {
                            Language.get("p2p_player2", lang).replace(" (Черные)", "")
                        }
                        if (lang == "RU") {
                            "Жеребьёвка завершена! $localWinnerName выиграл(а) первый ход (%1\$s против %2\$s)."
                        } else {
                            "Lottery complete! $localWinnerName won first turn (%1\$s vs %2\$s)."
                        }
                    } else {
                        val winningVisualColor = if (whiteWon) viewModel.humanPlayerColor else viewModel.botPlayerColor
                        if (winningVisualColor == Player.WHITE) {
                            Language.get("lot_result_first_move_white", lang)
                        } else {
                            Language.get("lot_result_first_move_black", lang)
                        }
                    }
                    val formatted = firstMoveText
                        .replace("%1\$s", viewModel.diceLot2White.toString())
                        .replace("%2\$s", viewModel.diceLot2Black.toString())

                    formatted + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы начать игру!" else "Touch board to start the game!")
                }
            }
            else -> {
                if (lang == "RU") "Коснитесь доски, чтобы определить первый ход!" else "Touch board to drop dice for first move!"
            }
        }
    } else {
        titleText = ""
        descText = ""
    }

    if (titleText.isNotEmpty()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xEE111A24)
            ),
            border = BorderStroke(1.dp, Color(0xFFD3A373).copy(alpha = 0.5f)),
            modifier = Modifier
                .wrapContentWidth()
                .padding(top = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = titleText.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFD3A373),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = descText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * 3D-анимированный кубик для процедуры жеребьевки, падающий сверху на игровое поле.
 *
 * @param value значение на выпавшей грани.
 * @param isRolling анимация броска.
 * @param themeId идентификатор темы оформления.
 * @param isLeftPlaySide направление покачивания (левая или правая половина доски).
 */
@Composable
fun LotteryDiceCube(
    value: Int,
    isRolling: Boolean,
    themeId: String = "warm",
    isLeftPlaySide: Boolean = false
) {
    val id = themeId.lowercase()

    val rotX by animateFloatAsState(
        targetValue = if (isRolling) 1260f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val rotY by animateFloatAsState(
        targetValue = if (isRolling) 1440f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val rotZ by animateFloatAsState(
        targetValue = if (isRolling) 1080f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val dropY by animateFloatAsState(
        targetValue = if (isRolling) -450f else 0f,
        animationSpec = if (isRolling) {
            tween(120, easing = LinearEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
        }
    )

    val shakeX by animateFloatAsState(
        targetValue = if (isRolling) (if (isLeftPlaySide) -20f else 20f) else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(280, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
        }
    )

    val scaleFactor by animateFloatAsState(
        targetValue = if (isRolling) 1.4f else 1.1f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(240, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val normalizedId = when (id) {
        "warm", "vintage" -> "wood"
        "water", "sapphire", "sea" -> "marine"
        "emerald" -> "malachite"
        "space" -> "cosmic"
        "rainbow", "cyberpunk" -> "psychedelic"
        else -> id
    }

    val containerColor = when (normalizedId) {
        "marine" -> Color(0xFFE0F7FA)
        "malachite" -> Color(0xFFE8F5E9)
        "cosmic" -> Color(0xFF131124)
        "psychedelic" -> Color(0xFF210C38)
        "bw" -> Color.White
        else -> Color(0xFFFFFDF8)
    }

    val borderColor = when (normalizedId) {
        "marine" -> Color(0xFF00E5FF)
        "malachite" -> Color(0xFFFFD700)
        "cosmic" -> Color(0xFF00FFCC)
        "psychedelic" -> Color(0xFFFF007F)
        "bw" -> Color.Black
        else -> Color(0xFFB08D57)
    }

    val dotColor = when (normalizedId) {
        "marine" -> Color(0xFF003865)
        "malachite" -> Color(0xFF0A3D18)
        "cosmic" -> Color(0xFFFF007F)
        "psychedelic" -> Color(0xFF00FFFF)
        "bw" -> Color.Black
        else -> Color(0xFF2B1B10)
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                rotationZ = rotZ
                translationX = shakeX * density
                translationY = dropY * density
                scaleX = scaleFactor
                scaleY = scaleFactor
                cameraDistance = 14f * density
            }
            .shadow(if (isRolling) 14.dp else 3.dp, RoundedCornerShape(8.dp))
            .background(containerColor, RoundedCornerShape(8.dp))
            .border(0.8.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val d = size.width
                val r = 2.8.dp.toPx()
                val color = dotColor

                when (value) {
                    1 -> {
                        drawCircle(color, r, Offset(d / 2, d / 2))
                    }
                    2 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    3 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 2, d / 2))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    4 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 4, 3 * d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    5 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 2, d / 2))
                        drawCircle(color, r, Offset(d / 4, 3 * d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    6 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 4, d / 2))
                        drawCircle(color, r, Offset(3 * d / 4, d / 2))
                        drawCircle(color, r, Offset(d / 4, 3 * d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                }
            }
        }
    }
}
