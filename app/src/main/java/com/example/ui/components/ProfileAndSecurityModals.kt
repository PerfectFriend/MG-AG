package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.DuressAction
import com.example.model.ProfileType
import com.example.model.UserProfile
import com.example.ui.GameViewModel

@Composable
fun UserProfileSelectorModal(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedChatLanguage
    var isCreatingNew by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var newProfileHandle by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProfileType.HIDDEN) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13171B)),
            border = BorderStroke(1.5.dp, Color(0xFF00FF66).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (lang == "RU") "👤 Мультипрофили SimpleX" else "👤 SimpleX Multi-Profiles",
                            color = Color(0xFF00FF66),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (lang == "RU") "Изолированные криптоконтейнеры" else "Sandboxed Crypto Containers",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                if (!isCreatingNew) {
                    // Profile List
                    Text(
                        text = if (lang == "RU") "АКТИВНЫЕ ПРОФИЛИ:" else "ACTIVE PROFILES:",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.availableProfiles) { profile ->
                            val isActive = profile.id == viewModel.activeProfile.id
                            val typeColor = when (profile.profileType) {
                                ProfileType.PRIMARY -> Color(0xFF00FF66)
                                ProfileType.HIDDEN -> Color(0xFFFF9900)
                                ProfileType.INCOGNITO -> Color(0xFF00E5FF)
                                ProfileType.DECOY -> Color(0xFFB0BEC5)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) Color(0xFF1E262C) else Color(0xFF171D22))
                                    .border(
                                        width = if (isActive) 1.5.dp else 0.5.dp,
                                        color = if (isActive) Color(0xFF00FF66) else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (!isActive) {
                                            viewModel.switchUserProfile(profile.id)
                                            onDismiss()
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(typeColor.copy(alpha = 0.2f))
                                            .border(1.dp, typeColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            color = typeColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = profile.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (isActive) {
                                                Text(
                                                    text = "● ACTIVE",
                                                    color = Color(0xFF00FF66),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                        Text(
                                            text = "@${profile.handle} • ${profile.profileType.name}",
                                            color = typeColor.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (!isActive && viewModel.availableProfiles.size > 1 && !profile.isDecoy) {
                                    IconButton(
                                        onClick = { viewModel.deleteUserProfile(profile.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Button to open creation view
                    OutlinedButton(
                        onClick = { isCreatingNew = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1B2329),
                            contentColor = Color(0xFF00FF66)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == "RU") "СОЗДАТЬ НОВЫЙ ПРОФИЛЬ" else "CREATE NEW PROFILE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Vault Status Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0E10)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF00FF66).copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(14.dp))
                                Text("VAULT STATUS: AES-256-GCM (PBKDF2)", color = Color(0xFF00FF66), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                text = "Profile Salt: ${viewModel.activeProfile.saltHex.take(12)}... • Isolated Keys",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    // Create Profile View
                    Text(
                        text = if (lang == "RU") "СОЗДАНИЕ НОВОГО ПРОФИЛЯ:" else "NEW PROFILE SETUP:",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text(if (lang == "RU") "Имя профиля" else "Profile Name", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF66),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newProfileHandle,
                        onValueChange = { newProfileHandle = it },
                        label = { Text(if (lang == "RU") "SimpleX Handle (без @)" else "SimpleX Handle", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF66),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (lang == "RU") "ТИП ПРОФИЛЯ:" else "PROFILE TYPE:",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(ProfileType.HIDDEN, ProfileType.INCOGNITO).forEach { type ->
                            val isSel = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF00FF66).copy(alpha = 0.2f) else Color(0xFF171D22))
                                    .border(
                                        width = if (isSel) 1.dp else 0.5.dp,
                                        color = if (isSel) Color(0xFF00FF66) else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color(0xFF00FF66) else Color.LightGray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isCreatingNew = false },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                if (newProfileName.isNotBlank() && newProfileHandle.isNotBlank()) {
                                    viewModel.createNewProfile(newProfileName, newProfileHandle, selectedType)
                                    isCreatingNew = false
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66)),
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "СОЗДАТЬ" else "CREATE",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DuressSecuritySettingsModal(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedChatLanguage
    var duressPinInput by remember { mutableStateOf(viewModel.duressPinCode) }
    var selectedAction by remember { mutableStateOf(viewModel.duressAction) }
    var isSavedAlert by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13171B)),
            border = BorderStroke(1.5.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (lang == "RU") "🛡️ DURESS PIN & ЗАЩИТА" else "🛡️ DURESS PIN & SECURITY",
                            color = Color(0xFFFF5252),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (lang == "RU") "Защита от принуждения и утечек" else "Anti-coercion & screen protection",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                // Duress PIN input
                Text(
                    text = if (lang == "RU") "ЭКСТРЕННЫЙ (DURESS) PIN-КОД:" else "EMERGENCY DURESS PIN:",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                OutlinedTextField(
                    value = duressPinInput,
                    onValueChange = { duressPinInput = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text(if (lang == "RU") "4-8 цифр (тревожный код)" else "4-8 digits (duress code)", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5252),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Duress Action Selector
                Text(
                    text = if (lang == "RU") "ДЕЙСТВИЕ ПРИ ВВОДЕ DURESS PIN:" else "ACTION ON DURESS PIN ENTRY:",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = listOf(
                        Triple(
                            DuressAction.DECOY_PROFILE,
                            if (lang == "RU") "🎭 Декоративный профиль (Decoy)" else "🎭 Decoy Profile (Stealth)",
                            if (lang == "RU") "Открывает пустой невинный профиль без секретных переписок и контактов" else "Unlocks empty harmless profile with zero secret chats or keys"
                        ),
                        Triple(
                            DuressAction.PANIC_WIPE,
                            if (lang == "RU") "💥 Panic Wipe (Стирание данных)" else "💥 Panic Wipe (Emergency Purge)",
                            if (lang == "RU") "Мгновенно уничтожает все ключи, контейнеры и закрывает приложение" else "Instantly erases all vaults, keys and terminates the app process"
                        ),
                        Triple(
                            DuressAction.NETWORK_ERROR,
                            if (lang == "RU") "⚠️ Симуляция ошибки сети" else "⚠️ Fake Network Error",
                            if (lang == "RU") "Имитирует системный сбой соединения и блокирует доступ" else "Displays simulated network error modal and blocks entry"
                        )
                    )

                    options.forEach { (action, title, desc) ->
                        val isSel = selectedAction == action
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFFFF5252).copy(alpha = 0.15f) else Color(0xFF171D22))
                                .border(
                                    width = if (isSel) 1.dp else 0.5.dp,
                                    color = if (isSel) Color(0xFFFF5252) else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedAction = action }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) Color(0xFFFF5252) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = desc,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                // Screen Security (FLAG_SECURE) Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF171D22))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (lang == "RU") "🔒 Защита экрана (FLAG_SECURE)" else "🔒 Screen Security (FLAG_SECURE)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (lang == "RU") "Запрет скриншотов, записи видео и сокрытие экрана в Recent Apps" else "Blocks screenshots, screen recording & hides thumbnail in task switcher",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = viewModel.isScreenSecurityEnabled,
                        onCheckedChange = { viewModel.toggleScreenSecurity(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.3f)
                        )
                    )
                }

                // Privacy Receipts (Timing-Attack Defense)
                Text(
                    text = if (lang == "RU") "КВИТАНЦИИ ПРИВАТНОСТИ (Защита от тайминг-атак):" else "PRIVACY RECEIPTS (Timing Attack Defense):",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF171D22))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") "Отчеты о прочтении" else "Read Receipts",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Switch(
                        checked = viewModel.sendReadReceipts,
                        onCheckedChange = { viewModel.toggleReadReceipts() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.3f)
                        )
                    )
                }

                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF171D22))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = if (lang == "RU") "Фоновый E2EE Push (Zero-FCM)" else "Background E2EE Push (Zero-FCM)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (lang == "RU") "Без серверов Google" else "Zero Google tracking",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = viewModel.isBackgroundPushEnabled,
                        onCheckedChange = { viewModel.toggleBackgroundPush(context) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FF66),
                            checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.3f)
                        )
                    )
                }

                // Double Ratchet E2EE Status Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1217)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF00E676).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                        Column {
                            Text(
                                text = "DOUBLE RATCHET + CURVE25519",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (lang == "RU") "16 KB пакеты со случайным шумом (Padding)" else "16 KB framed blocks with noise padding",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Save button
                Button(
                    onClick = {
                        viewModel.updateDuressConfig(duressPinInput, selectedAction)
                        isSavedAlert = true
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "СОХРАНИТЬ ПАРАМЕТРЫ ЗАЩИТЫ" else "SAVE SECURITY SETTINGS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun CreateGroupOrChannelModal(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedChatLanguage
    var roomTitle by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("GROUP_CHAT") }
    var selectedTimerSec by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13171B)),
            border = BorderStroke(1.5.dp, Color(0xFF00FF66).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") "➕ СОЗДАТЬ ГРУППУ / КАНАЛ" else "➕ NEW GROUP / CHANNEL",
                        color = Color(0xFF00FF66),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                // Title Input
                OutlinedTextField(
                    value = roomTitle,
                    onValueChange = { roomTitle = it },
                    label = { Text(if (lang == "RU") "Название группы / канала" else "Group / Channel Title", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF66),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Type Selector
                Text(
                    text = if (lang == "RU") "ТИП КОМНАТЫ:" else "ROOM TYPE:",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "GROUP_CHAT" to (if (lang == "RU") "👥 Группа" else "👥 Group"),
                        "INFO_CHANNEL" to (if (lang == "RU") "📢 Канал" else "📢 Channel"),
                        "DIRECT_CHAT" to (if (lang == "RU") "🔒 E2EE" else "🔒 E2EE")
                    ).forEach { (typeKey, typeLabel) ->
                        val isSel = selectedType == typeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00FF66).copy(alpha = 0.2f) else Color(0xFF171D22))
                                .border(
                                    width = if (isSel) 1.dp else 0.5.dp,
                                    color = if (isSel) Color(0xFF00FF66) else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedType = typeKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = typeLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF00FF66) else Color.LightGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Self-Destruct Timer
                Text(
                    text = if (lang == "RU") "ТАЙМЕР САМОЛИКВИДАЦИИ СООБЩЕНИЙ:" else "DISAPPEARING MESSAGES TIMER:",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to (if (lang == "RU") "Выкл" else "Off"),
                        30 to "30s",
                        60 to "1m",
                        300 to "5m"
                    ).forEach { (sec, label) ->
                        val isSel = selectedTimerSec == sec
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) Color(0xFFFF5252).copy(alpha = 0.2f) else Color(0xFF171D22))
                                .border(
                                    width = if (isSel) 1.dp else 0.5.dp,
                                    color = if (isSel) Color(0xFFFF5252) else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedTimerSec = sec }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFFFF5252) else Color.LightGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Create Action Button
                Button(
                    onClick = {
                        if (roomTitle.isNotBlank()) {
                            viewModel.createNewRoom(roomTitle, selectedType, selectedTimerSec)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66)),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "СОЗДАТЬ И ЗАПУСТИТЬ СЕТЬ" else "CREATE AND LAUNCH MESH",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}



