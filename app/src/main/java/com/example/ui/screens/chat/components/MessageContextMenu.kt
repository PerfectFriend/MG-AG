package com.example.ui.screens.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.GameViewModel

/**
 * Контекстное всплывающее меню в стиле Telegram / WhatsApp по долгому тапу на сообщение.
 * Содержит верхнюю плашку быстрых реакций эмодзи и список действий (Копировать, Избранное, Поделиться, Удалить).
 */
@Composable
fun MessageContextMenu(
    message: GameViewModel.ChatMessage,
    currentRoomId: String,
    viewModel: GameViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF131920))
                .border(1.dp, Color(0xFF00FF41).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Полоска быстрых реакций эмодзи (Telegram style)
            Text(
                text = if (lang == "RU") "Быстрые реакции" else "Quick reactions",
                color = Color.Gray,
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C1014))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("👍", "❤️", "🔥", "🎲", "🔒", "👏").forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.addSimpleXReaction(currentRoomId, message.id, emoji)
                                onDismiss()
                            }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)

            // 2. Список контекстных действий
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Копировать
                ContextMenuActionRow(
                    icon = Icons.Default.ContentCopy,
                    iconTint = Color.LightGray,
                    title = if (lang == "RU") "Копировать текст" else "Copy text",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("SimpleX Message", message.text))
                        Toast.makeText(context, if (lang == "RU") "Скопировано в буфер" else "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                // Сохранить в Избранное
                ContextMenuActionRow(
                    icon = Icons.Default.Bookmark,
                    iconTint = Color(0xFF00FF66),
                    title = if (lang == "RU") "Сохранить в Избранное" else "Save to Saved Messages",
                    onClick = {
                        viewModel.addSavedVaultItem(
                            text = message.text,
                            attachmentType = message.attachmentType,
                            attachmentName = message.attachmentName,
                            attachmentUrl = message.attachmentUrl,
                            attachmentSize = message.attachmentSize
                        )
                        Toast.makeText(context, if (lang == "RU") "Сохранено в Избранное 🔖" else "Saved to Bookmarks 🔖", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                // Поделиться (Android Share)
                ContextMenuActionRow(
                    icon = Icons.Default.Share,
                    iconTint = Color(0xFF29B6F6),
                    title = if (lang == "RU") "Поделиться..." else "Share...",
                    onClick = {
                        viewModel.shareChatMessage(message, context)
                        onDismiss()
                    }
                )

                // Экспорт на устройство
                ContextMenuActionRow(
                    icon = Icons.Default.SaveAlt,
                    iconTint = Color(0xFFAB47BC),
                    title = if (lang == "RU") "Экспорт файла / заметки" else "Export message / note",
                    onClick = {
                        viewModel.saveChatMessageToDevice(message, context)
                        onDismiss()
                    }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)

                // Удалить
                ContextMenuActionRow(
                    icon = Icons.Default.Delete,
                    iconTint = Color(0xFFFF5252),
                    title = if (lang == "RU") "Удалить сообщение" else "Delete message",
                    isDestructive = true,
                    onClick = {
                        viewModel.deleteChatMessage(currentRoomId, message.id)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextMenuActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = if (isDestructive) Color(0xFFFF5252) else Color.White,
            fontSize = 13.5.sp,
            fontWeight = if (isDestructive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
