package com.example.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel

/**
 * Всплывающая нижняя шторка выбора вложений в стиле Telegram / WhatsApp (`ModalBottomSheet`).
 * Освобождает экран от постоянных статических кнопок и открывается только по клику на [📎].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    currentRoomId: String,
    viewModel: GameViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131920),
        tonalElevation = 6.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (lang == "RU") "Поделиться в защищенном канале" else "Share in E2EE channel",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // Сетка 3x2 в стиле Telegram вложений
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // 1. Фото
                AttachmentGridItem(
                    icon = Icons.Default.CameraAlt,
                    label = if (lang == "RU") "Фото" else "Photo",
                    color = Color(0xFF00FF66),
                    onClick = {
                        viewModel.sendSimpleXMessage(
                            currentRoomId,
                            if (lang == "RU") "Шифрованное фото отправлено" else "Encrypted image transmitted",
                            attachmentType = "IMAGE",
                            attachmentUrl = "sec_img_ram.raw"
                        )
                        onDismiss()
                    }
                )

                // 2. Файл
                AttachmentGridItem(
                    icon = Icons.Default.InsertDriveFile,
                    label = if (lang == "RU") "Файл" else "File",
                    color = Color(0xFF29B6F6),
                    onClick = {
                        viewModel.sendSimpleXMessage(
                            currentRoomId,
                            "paranoid_backup.sql",
                            attachmentType = "FILE",
                            attachmentUrl = "secure_backup.bin",
                            attachmentName = "paranoid_backup.sql",
                            attachmentSize = "45 KB"
                        )
                        onDismiss()
                    }
                )

                // 3. Голос
                AttachmentGridItem(
                    icon = Icons.Default.Mic,
                    label = if (lang == "RU") "Голос" else "Voice",
                    color = Color(0xFFAB47BC),
                    onClick = {
                        viewModel.sendSimpleXMessage(
                            currentRoomId,
                            if (lang == "RU") "Голосовая заметка (E2EE PCM)" else "E2EE Voice Note PCM",
                            attachmentType = "AUDIO",
                            attachmentUrl = "voc_ram_3.raw"
                        )
                        onDismiss()
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // 4. Честные кости (Mental Dice)
                AttachmentGridItem(
                    icon = Icons.Default.Casino,
                    label = if (lang == "RU") "Зарики" else "Dice Roll",
                    color = Color(0xFFFFB300),
                    onClick = {
                        val d1 = (1..6).random()
                        val d2 = (1..6).random()
                        viewModel.sendSimpleXMessage(
                            currentRoomId,
                            if (lang == "RU") "🎲 Бросок костей (Mental Poker): [$d1 : $d2]" else "🎲 Verified Dice Roll: [$d1 : $d2]"
                        )
                        onDismiss()
                    }
                )

                // 5. Контакт SimpleX
                AttachmentGridItem(
                    icon = Icons.Default.PersonAdd,
                    label = if (lang == "RU") "Контакт" else "Contact",
                    color = Color(0xFF26A69A),
                    onClick = {
                        viewModel.sendSimpleXMessage(
                            currentRoomId,
                            "smp://contact#id=${viewModel.simplexUserHandle}"
                        )
                        onDismiss()
                    }
                )

                // 6. Секретная сессия (Таймер самоуничтожения)
                AttachmentGridItem(
                    icon = Icons.Default.LocalFireDepartment,
                    label = if (lang == "RU") "Таймер" else "Burner",
                    color = Color(0xFFFF5252),
                    onClick = {
                        val room = viewModel.simplexRooms.firstOrNull { it.id == currentRoomId }
                        if (room != null) {
                            room.selfDestructTimerSec = if (room.selfDestructTimerSec == 0) 30 else 0
                        }
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentGridItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
