package com.example.ui.screens.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Компактный пузырь сообщения в классическом стиле Telegram / WhatsApp.
 * - Метаданные (время и статус E2EE) аккуратно "впекаются" в правый нижний угол.
 * - По долгому тапу открывается контекстное меню и панель реакций.
 * - Сохранена фирменная киберпанк-палитра (черный / неоново-зеленый).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: GameViewModel.ChatMessage,
    isMe: Boolean,
    lang: String,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onReactionClick: (String) -> Unit
) {
    val context = LocalContext.current
    val isSys = message.id == "sys" || message.sender == "System" || message.sender == "SimpleX System"

    if (isSys) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161E26))
                    .border(0.5.dp, Color(0xFF00FF41).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = message.text,
                    color = Color(0xFF00E676),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val timeFormatted = rememberFormattedTime(message.timestamp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(min = 100.dp, max = 300.dp)
        ) {
            // Главная плашка сообщения
            val bubbleBg = when {
                message.isSelfDestructed -> Color(0xFF2C1E1E)
                isSelected -> Color(0xFF00FF41).copy(alpha = 0.22f)
                isMe -> Color(0xFF092918) // глубокий изумрудный
                else -> Color(0xFF141920) // тёмный графит
            }
            val bubbleBorder = when {
                message.isSelfDestructed -> BorderStroke(1.dp, Color(0xFFFF4D4D))
                isSelected -> BorderStroke(1.5.dp, Color(0xFF00FF41))
                isMe -> BorderStroke(1.dp, Color(0xFF00FF41).copy(alpha = 0.4f))
                else -> BorderStroke(1.dp, Color(0xFF222C38))
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isMe) 14.dp else 3.dp,
                            bottomEnd = if (isMe) 3.dp else 14.dp
                        )
                    )
                    .background(bubbleBg)
                    .border(
                        bubbleBorder,
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isMe) 14.dp else 3.dp,
                            bottomEnd = if (isMe) 3.dp else 14.dp
                        )
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column {
                    // Имя отправителя для групповых комнат
                    if (!isMe) {
                        Text(
                            text = message.sender,
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    if (message.isSelfDestructed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("🔥", fontSize = 13.sp)
                            Text(
                                text = if (lang == "RU") "[СООБЩЕНИЕ УНИЧТОЖЕНО]" else "[MESSAGE SHREDDED]",
                                color = Color(0xFFFF5252),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Вложения
                        when (message.attachmentType) {
                            "IMAGE" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0C1014))
                                        .border(1.dp, Color(0xFF00FF41).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("📷", fontSize = 20.sp)
                                        Text(
                                            text = if (lang == "RU") "Шифрованное E2EE фото" else "Encrypted E2EE Photo",
                                            color = Color(0xFF00FF41),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            "FILE" -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0C1014))
                                        .border(1.dp, Color(0xFF29B6F6).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("📁", fontSize = 18.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.attachmentName.ifEmpty { "encrypted_file.bin" },
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = message.attachmentSize.ifEmpty { "45 KB" },
                                            color = Color.Gray,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            "AUDIO" -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0C1014))
                                        .border(1.dp, Color(0xFFAB47BC).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🎤", fontSize = 18.sp)
                                    Text(
                                        text = if (lang == "RU") "Голосовая заметка (E2EE PCM)" else "E2EE Voice Note PCM",
                                        color = Color(0xFFE1BEE7),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        // Основной текст сообщения
                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                color = if (isMe) Color(0xFFE8F5E9) else Color(0xFFF0F6FC),
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Telegram/WhatsApp стиль: встроенная строка метаданных в правом нижнем углу
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (message.selfDestructTimeLeft > 0) {
                                Text(
                                    text = "🔥 ${message.selfDestructTimeLeft}s",
                                    color = Color(0xFFFFA726),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = timeFormatted,
                                fontSize = 9.sp,
                                color = Color.LightGray.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(
                                imageVector = if (isMe) Icons.Default.DoneAll else Icons.Default.Lock,
                                contentDescription = "Status",
                                tint = if (isMe) Color(0xFF00FF41) else Color(0xFF29B6F6),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // Реакции-эмодзи под пузырем (Telegram стиль)
            if (message.reactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    message.reactions.forEach { reactionEmoji ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF161E26))
                                .border(0.5.dp, Color(0xFF00FF41).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable { onReactionClick(reactionEmoji) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(reactionEmoji, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberFormattedTime(timestamp: Long): String {
    return if (timestamp <= 0L) {
        "12:00"
    } else {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    }
}
