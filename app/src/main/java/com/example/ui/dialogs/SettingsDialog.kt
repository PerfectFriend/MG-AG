package com.example.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GameMode
import com.example.ui.GameViewModel
import com.example.ui.screens.Language

/**
 * Диалог настроек игры «Шеш-Беш».
 *
 * Предоставляет пользователю управление:
 * 1. Локализацией интерфейса (EN, ES, DE, FR, RU, TR).
 * 2. Темой оформления игрового поля (дерево, море, малахит, космос, психоделика).
 * 3. Режимами игры (Бешеные, Длинные, Короткие).
 * 4. Настройками оппонента (ИИ-бот, автобросок, согласие на отмену ходов).
 * 5. Встроенным интернет-радио, локальным медиаплеером и Telegram-каналом.
 * 6. Экстренным выходом и сбросом фоновых процессов.
 *
 * @param viewModel главная вьюмодель управления состоянием игры.
 * @param onDismiss коллбек закрытия диалога.
 * @param onNetworkClick опциональный коллбек перехода в сетевые настройки.
 */
@Composable
fun SettingsDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    onNetworkClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val lang = viewModel.selectedLanguage

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.radioManager.addLocalFolder(it)
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.radioManager.addLocalFiles(uris)
        }
    }

    var vpnFileAlertMessage by remember { mutableStateOf("") }
    val vpnConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val res = viewModel.vpnManager.importConfigFromFile(it)
            vpnFileAlertMessage = res
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("settings_title", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.height(380.dp)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Раздел 1: Язык и оформление
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (lang == "RU") "Язык и оформление" else "Language & Presentation",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Выбор языка интерфейса
                                    val languages = listOf(
                                        "EN" to "🇺🇸 EN",
                                        "ES" to "🇪🇸 ES",
                                        "DE" to "🇩🇪 DE",
                                        "FR" to "🇫🇷 FR",
                                        "RU" to "🇷🇺 RU",
                                        "TR" to "🇹🇷 TR"
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = if (lang == "RU") "Язык интерфейса" else "Interface Language",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            languages.take(3).forEach { (code, label) ->
                                                val isSelected = viewModel.selectedLanguage == code
                                                Button(
                                                    onClick = { viewModel.updateLanguage(code) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp)
                                                ) {
                                                    Text(
                                                        label,
                                                        fontSize = 10.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            languages.drop(3).forEach { (code, label) ->
                                                val isSelected = viewModel.selectedLanguage == code
                                                Button(
                                                    onClick = { viewModel.updateLanguage(code) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp)
                                                ) {
                                                    Text(
                                                        label,
                                                        fontSize = 10.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Темы оформления игрового поля
                                    val themeOptions = listOf(
                                        Triple("wood", Color(0xFF8D5B33), if (lang == "RU") "Дерево" else "Wood"),
                                        Triple("marine", Color(0xFF00ACC1), if (lang == "RU") "Море" else "Marine"),
                                        Triple("malachite", Color(0xFF2E7D32), if (lang == "RU") "Малахит" else "Malachite"),
                                        Triple("cosmic", Color(0xFF9C27B0), if (lang == "RU") "Космос" else "Cosmic"),
                                        Triple("psychedelic", Color(0xFFFF5722), if (lang == "RU") "Психоделика" else "Psychedelic")
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = if (lang == "RU") "Скин игрового поля:" else "Board Skin / Theme:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            themeOptions.forEach { (themeId, themeColor, themeLabel) ->
                                                val isSelected = viewModel.selectedTheme.lowercase() == themeId
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.clickable { viewModel.updateTheme(themeId) }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(CircleShape)
                                                            .background(themeColor)
                                                            .border(
                                                                if (isSelected) 3.dp else 1.dp,
                                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                                CircleShape
                                                            )
                                                            .padding(2.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = themeLabel,
                                                        fontSize = 9.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Раздел 2: Режимы игры и правила
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (lang == "RU") "Режим нард" else "Game Mode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val modes = listOf(
                                            GameMode.MAD to (if (lang == "RU") "Бешеные" else "Mad"),
                                            GameMode.LONG to (if (lang == "RU") "Длинные" else "Long"),
                                            GameMode.SHORT to (if (lang == "RU") "Короткие" else "Short")
                                        )
                                        modes.forEach { (mode, label) ->
                                            val isSel = viewModel.selectedGameMode == mode
                                            Button(
                                                onClick = {
                                                    viewModel.selectedGameMode = mode
                                                    viewModel.startNewGame(mode)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(32.dp)
                                            ) {
                                                Text(
                                                    label,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(Language.get("ai_opponent", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(Language.get("ai_opponent_desc", lang), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = viewModel.isBotOpponentEnabled,
                                            onCheckedChange = { viewModel.isBotOpponentEnabled = it }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(Language.get("auto_roll", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(Language.get("auto_roll_desc", lang), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = viewModel.isAutoRollEnabled,
                                            onCheckedChange = { viewModel.isAutoRollEnabled = it }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Переключатель отмены хода (Undo)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (lang == "RU") "Отмена хода (Undo)" else "Move Undo",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = if (lang == "RU") {
                                                    "Разрешить отмену ходов во время партии. Если хотя бы один игрок запретил отмену — отмена не работает."
                                                } else {
                                                    "Allow move undo during game. If either player disallows it, undo is disabled for both."
                                                },
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = viewModel.allowUndoPlayer1,
                                            onCheckedChange = { viewModel.updateAllowUndoPlayer1(it) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (lang == "RU") "Согласие соперника на отмену" else "Opponent Undo Consent",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = if (lang == "RU") {
                                                    "Разрешение на отмену со стороны соперника / второго игрока."
                                                } else {
                                                    "Permission for undo from the opponent / player 2."
                                                },
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = viewModel.allowUndoPlayer2,
                                            onCheckedChange = { viewModel.updateAllowUndoPlayer2(it) }
                                        )
                                    }

                                    if (!viewModel.isUndoAllowed) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (lang == "RU") "⚠️ Отмена хода заблокирована (один из игроков запретил)." else "⚠️ Move undo is locked (a player disallowed it).",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        // Раздел 3: Интернет-радио и локальный медиаплеер
                        item {
                            var activePlayerTab by remember { mutableStateOf(0) }
                            var customName by remember { mutableStateOf("") }
                            var customUrl by remember { mutableStateOf("") }
                            val radioSettings = viewModel.radioManager

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (activePlayerTab == 0) "Web Radio" else if (activePlayerTab == 1) "Local Playlist" else "Telegram Bridge",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (activePlayerTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { activePlayerTab = 0 }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text("Радио", fontSize = 9.sp, color = if (activePlayerTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (activePlayerTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { activePlayerTab = 1 }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text("Медиа", fontSize = 9.sp, color = if (activePlayerTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (activePlayerTab == 2) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { activePlayerTab = 2 }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text("TG Канал", fontSize = 9.sp, color = if (activePlayerTab == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // HUD панель текущего трека
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (radioSettings.isPlaying) Icons.Default.PlayArrow else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = radioSettings.activeTrackTitle,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (radioSettings.isLoading) "Загрузка..."
                                                else if (radioSettings.isPlaying) "Воспроизведение"
                                                else "Остановлено",
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (radioSettings.isPlayingLocal) {
                                            IconButton(
                                                onClick = {
                                                    if (radioSettings.isPlayingLocal) radioSettings.prevLocalTrack()
                                                    else radioSettings.prevTelegramTrack()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        }

                                        IconButton(
                                            onClick = { radioSettings.togglePlay() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (radioSettings.isPlaying || radioSettings.isLoading) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (radioSettings.isPlayingLocal) {
                                            IconButton(
                                                onClick = {
                                                    if (radioSettings.isPlayingLocal) radioSettings.nextLocalTrack()
                                                    else radioSettings.nextTelegramTrack()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }

                                    radioSettings.errorMessage?.let { err ->
                                        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 7.sp, modifier = Modifier.padding(top = 2.dp))
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (activePlayerTab == 0) {
                                        // Вкладка Радио
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(75.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .padding(2.dp)
                                        ) {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(radioSettings.channels) { channel ->
                                                    val isSelected = !radioSettings.isPlayingLocal && radioSettings.currentChannel?.id == channel.id
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                                                            .clickable { radioSettings.selectChannel(channel) }
                                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                            Icon(
                                                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.PlayArrow,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(11.dp),
                                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(channel.name, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                        if (channel.isCustom) {
                                                            IconButton(onClick = { radioSettings.deleteCustomChannel(channel.id) }, modifier = Modifier.size(16.dp)) {
                                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(11.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customName,
                                                onValueChange = { customName = it },
                                                placeholder = { Text("Название", fontSize = 8.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1.3f).height(48.dp),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 8.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            )
                                            OutlinedTextField(
                                                value = customUrl,
                                                onValueChange = { customUrl = it },
                                                placeholder = { Text("Stream URL", fontSize = 8.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(2.2f).height(48.dp),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 8.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (customName.isNotBlank() && customUrl.isNotBlank()) {
                                                        radioSettings.addCustomChannel(customName, customUrl)
                                                        customName = ""
                                                        customUrl = ""
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    } else if (activePlayerTab == 1) {
                                        // Вкладка Локальной музыки
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Button(
                                                onClick = { folderLauncher.launch(null) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(24.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("📁 Папка", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = { filesLauncher.launch(arrayOf("audio/*")) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(24.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("📄 Файлы", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(75.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .padding(2.dp)
                                        ) {
                                            if (radioSettings.localTracks.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("Плейлист пуст.\nДобавьте треки.", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                }
                                            } else {
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    items(radioSettings.localTracks) { track ->
                                                        val isSelectedPlaying = radioSettings.isPlayingLocal &&
                                                            radioSettings.currentTrackIndex in radioSettings.localTracks.filter { it.isSelected }.indices &&
                                                            radioSettings.localTracks.filter { it.isSelected }[radioSettings.currentTrackIndex].id == track.id
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isSelectedPlaying) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                                                .clickable {
                                                                    val filtered = radioSettings.localTracks.filter { it.isSelected }
                                                                    val matchIdx = filtered.indexOfFirst { it.id == track.id }
                                                                    if (matchIdx != -1) {
                                                                        radioSettings.playLocalTrack(matchIdx)
                                                                    } else {
                                                                        radioSettings.toggleTrackSelection(track.id)
                                                                        val newFiltered = radioSettings.localTracks.filter { it.isSelected }
                                                                        val newMatchIdx = newFiltered.indexOfFirst { it.id == track.id }
                                                                        if (newMatchIdx != -1) {
                                                                            radioSettings.playLocalTrack(newMatchIdx)
                                                                        }
                                                                    }
                                                                }
                                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Checkbox(
                                                                checked = track.isSelected,
                                                                onCheckedChange = { radioSettings.toggleTrackSelection(track.id) },
                                                                modifier = Modifier.scale(0.5f).size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = track.name,
                                                                fontSize = 9.sp,
                                                                fontWeight = if (isSelectedPlaying) FontWeight.Bold else FontWeight.Normal,
                                                                modifier = Modifier.weight(1f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            IconButton(
                                                                onClick = { radioSettings.deleteTrack(track.id) },
                                                                modifier = Modifier.size(16.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(10.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = { radioSettings.isShuffle = !radioSettings.isShuffle },
                                                    modifier = Modifier.size(20.dp).background(if (radioSettings.isShuffle) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp), tint = if (radioSettings.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(
                                                    onClick = { radioSettings.isRepeat = !radioSettings.isRepeat },
                                                    modifier = Modifier.size(20.dp).background(if (radioSettings.isRepeat) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(10.dp), tint = if (radioSettings.isRepeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = { radioSettings.selectAllTracks(true) },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(18.dp)
                                                ) {
                                                    Text("Все", fontSize = 8.sp)
                                                }
                                                TextButton(
                                                    onClick = { radioSettings.selectAllTracks(false) },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(18.dp)
                                                ) {
                                                    Text("Ни одного", fontSize = 8.sp)
                                                }
                                                TextButton(
                                                    onClick = { radioSettings.clearAllTracks() },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(18.dp)
                                                ) {
                                                    Text("Очистить", fontSize = 8.sp, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    } else {
                                        // Вкладка Telegram (activePlayerTab == 2)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = { radioSettings.fetchTelegramTracks() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                modifier = Modifier.height(24.dp),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Обновить", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = { radioSettings.playRandomTelegramTrack() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                                modifier = Modifier.height(24.dp),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Случайно", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(75.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .padding(2.dp)
                                        ) {
                                            if (radioSettings.isTelegramLoading) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                            } else if (radioSettings.telegramTracks.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("Канал пуст или не загружен.\nНажмите Обновить.", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                }
                                            } else {
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    itemsIndexed(radioSettings.telegramTracks) { idx, track ->
                                                        val isSelectedPlaying = radioSettings.isPlayingTelegram && radioSettings.currentTelegramTrackIndex == idx
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isSelectedPlaying) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                                                .clickable { radioSettings.playTelegramTrack(idx) }
                                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isSelectedPlaying) Icons.Default.PlayArrow else Icons.Default.Share,
                                                                contentDescription = null,
                                                                tint = if (isSelectedPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = track.title,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = if (isSelectedPlaying) FontWeight.Bold else FontWeight.Normal,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = track.artist,
                                                                    fontSize = 7.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.radioManager.stop()
                            viewModel.vpnManager.stopVpnConnectionSimulation()
                            viewModel.setTorEnabledState(false)
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(0.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "ВЫХОД & Очистка" else "EXIT & CLEAR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text(Language.get("done", lang))
                    }
                }
            }
        }
    }
}
