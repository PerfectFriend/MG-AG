package com.example.ui.screens.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameStatus
import com.example.ui.GameViewModel
import com.example.ui.components.DiceCube
import com.example.ui.screens.Language

/**
 * Интерактивный блок отображения зариков (костей) на игровом поле.
 *
 * Состояния отображения:
 * 1. **Анимация броска (isRollingDice)**: Отображение вращающихся кубиков и индикатора ZKP обмена хэшами.
 * 2. **Ожидание броска (BEFORE_ROLL)**:
 *    - Кнопка «БРОСОК / TAP TO ROLL» для человека.
 *    - Плашка «Бот думает...» с возможностью принудительного прогресса хода при клике.
 * 3. **Фаза совершения хода (PLAYER_MOVE)**:
 *    - Оставшиеся доступные кости (включая учет 4 ходов при куше).
 *    - Кликабельный бейдж криптографической честности Mental Dice (HMAC-SHA256 Commit-Reveal).
 *
 * @param viewModel главная вьюмодель игры.
 */
@Composable
fun BoardDiceDisplay(viewModel: GameViewModel) {
    val isBotTurn = viewModel.isBotOpponentEnabled && viewModel.activePlayer == viewModel.botPlayerColor
    val lang = viewModel.selectedLanguage
    val theme = viewModel.selectedTheme

    when {
        viewModel.isRollingDice -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    DiceCube(value = viewModel.diceValue1, isRolling = true, themeId = theme)
                    DiceCube(value = viewModel.diceValue2, isRolling = true, themeId = theme)
                }
            }
        }
        viewModel.gameStatus == GameStatus.BEFORE_ROLL -> {
            if (!isBotTurn) {
                Card(
                    modifier = Modifier
                        .clickable { viewModel.rollDice() }
                        .shadow(6.dp, RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFD3A373).copy(alpha = 0.9f),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black
                            )
                            Text(
                                text = Language.get("roll", lang).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = Language.get("tap_to_roll", lang),
                            fontSize = 7.5.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .clickable { viewModel.rollDice() }
                        .shadow(4.dp, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.65f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = Language.get("bot_thinking", lang),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        viewModel.gameStatus == GameStatus.PLAYER_MOVE -> {
            val remaining = viewModel.remainingDice
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (remaining.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        remaining.forEach { valD ->
                            DiceCube(value = valD, isRolling = false, themeId = theme)
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        DiceCube(value = viewModel.diceValue1, isRolling = false, themeId = theme)
                        DiceCube(value = viewModel.diceValue2, isRolling = false, themeId = theme)
                    }
                }
            }
        }
    }
}
