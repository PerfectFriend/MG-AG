package com.example.ui.screens.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel

/**
 * Секция интерактивного оппонента «Зарик-Победитель» (ИИ-бот).
 *
 * Функции:
 * - Аватар с индикатором присутствия.
 * - Речевой пузырь (Speech bubble) с колкими комментариями и сарказмом о текущей позиции.
 * - Поле диалога для отправки сообщений или провокаций боту.
 * - Индикатор размышлений и генерации ответа через Gemini API.
 *
 * @param viewModel главная вьюмодель игры.
 */
@Composable
fun BotOpponentSection(viewModel: GameViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFF252528), CircleShape)
                            .border(1.5.dp, Color(0xFFD3A373), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Bot Avatar",
                            tint = Color(0xFFEFEDE8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Green, CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (viewModel.selectedLanguage == "RU") "Зарик-Победитель (ИИ-бот)" else "Zarik Winner (AI Bot)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (viewModel.isGeminiLoadingJoke) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = viewModel.botSpeechBubble ?: "",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.fetchBotJoke(
                            if (viewModel.selectedLanguage == "RU") "человек просит новую шутку" else "human asks for another joke"
                        )
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Сарказм",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var chatInputText by remember { mutableStateOf("") }

                TextField(
                    value = chatInputText,
                    onValueChange = { chatInputText = it },
                    placeholder = {
                        Text(
                            text = if (viewModel.selectedLanguage == "RU") "Подколоть Зарика..." else "Provoke Zarik...",
                            fontSize = 11.5.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (chatInputText.isNotBlank()) {
                            val contextPrefix = if (viewModel.selectedLanguage == "RU") {
                                "Пользователь провоцирует/спрашивает бота: "
                            } else {
                                "User provokes/asks the bot: "
                            }
                            viewModel.fetchBotJoke("$contextPrefix \"$chatInputText\"")
                            chatInputText = ""
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = chatInputText.isNotBlank() && !viewModel.isGeminiLoadingJoke
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (chatInputText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
