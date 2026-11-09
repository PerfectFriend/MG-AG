package com.example.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.GameViewModel

/**
 * Диалог встроенного аудиоплеера и интернет-радиоприёмника:
 * Окно прокрутки строго ограничено по высоте — скроллится ТОЛЬКО список радиостанций,
 * а заголовок, статус вещания и кнопки управления зафиксированы и никогда не уезжают за экран.
 */
@Composable
fun RadioDialog(viewModel: GameViewModel, onDismiss: () -> Unit) {
    val lang = viewModel.selectedLanguage
    var activePlayerTab by remember { mutableIntStateOf(0) } // 0 = Radio, 1 = Local Music, 2 = TG Channel
    var customName by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    val radioSettings = viewModel.radioManager

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101724)),
            border = BorderStroke(1.5.dp, Color(0xFFD3A373)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .heightIn(max = 330.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Фиксированный верхний блок: Заголовок и переключатель вкладок
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (activePlayerTab) {
                            0 -> if (lang == "RU") "Радиостанции" else "Web Radio"
                            1 -> if (lang == "RU") "Локальная Музыка" else "Local Tracks"
                            else -> "TG Armageddon FM"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37)
                    )
                    
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF090D15), RoundedCornerShape(6.dp))
                            .padding(2.dp)
                    ) {
                        listOf(
                            0 to (if (lang == "RU") "Радио" else "Radio"),
                            1 to (if (lang == "RU") "Файлы" else "Files"),
                            2 to (if (lang == "RU") "TG Канал" else "Telegram")
                        ).forEach { (tabIdx, tabTitle) ->
                            val isSel = activePlayerTab == tabIdx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) Color(0xFFD4AF37) else Color.Transparent)
                                    .clickable { activePlayerTab = tabIdx }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = tabTitle,
                                    fontSize = 9.sp,
                                    color = if (isSel) Color.Black else Color.White,
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. Компактная плашка текущего трека (Now Playing)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF080D14), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (radioSettings.isPlaying) Icons.Default.PlayArrow else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (radioSettings.isPlaying) Color(0xFF00FF66) else Color(0xFF8B949E),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = radioSettings.activeTrackTitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { radioSettings.togglePlay() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (radioSettings.isPlaying || radioSettings.isLoading) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF00FF66)
                        )
                    }
                }

                // 3. ОСНОВНАЯ ЗОНА: СКРОЛЛИТСЯ ТОЛЬКО СПИСОК РАДИОСТАНЦИЙ / ТРЕКОВ
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(6.dp))
                        .background(Color(0xFF080C14))
                        .padding(2.dp)
                ) {
                    when (activePlayerTab) {
                        0 -> {
                            val filtered = radioSettings.channels.filter { channel ->
                                channel.isCustom || channel.id.startsWith("${lang.lowercase()}_")
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filtered) { channel ->
                                    val isSelected = !radioSettings.isPlayingLocal && radioSettings.currentChannel?.id == channel.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) Color(0xFF1B2A3D) else Color.Transparent)
                                            .clickable { radioSettings.selectChannel(channel) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(11.dp),
                                                tint = if (isSelected) Color(0xFF00FF66) else Color(0xFF8B949E)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = channel.name,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFF00FF66) else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (channel.isCustom) {
                                            IconButton(onClick = { radioSettings.deleteCustomChannel(channel.id) }, modifier = Modifier.size(18.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(11.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (radioSettings.localTracks.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (lang == "RU") "Плейлист пуст.\nДобавьте аудиофайлы." else "Playlist is empty.\nAdd audio files.", fontSize = 9.5.sp, color = Color.Gray, textAlign = TextAlign.Center)
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
                                                .background(if (isSelectedPlaying) Color(0xFF1B2A3D) else Color.Transparent)
                                                .clickable { 
                                                    val filtered = radioSettings.localTracks.filter { it.isSelected }
                                                    val matchIdx = filtered.indexOfFirst { it.id == track.id }
                                                    if (matchIdx != -1) radioSettings.playLocalTrack(matchIdx)
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = track.isSelected,
                                                onCheckedChange = { radioSettings.toggleTrackSelection(track.id) },
                                                modifier = Modifier.scale(0.6f).size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(track.name, fontSize = 9.5.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            if (radioSettings.isTelegramLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF00FF66), modifier = Modifier.size(20.dp))
                                }
                            } else if (radioSettings.telegramTracks.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (lang == "RU") "Канал загружается...\nНажмите кнопку Обновить." else "Loading channel...\nTap Refresh.", fontSize = 9.5.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(radioSettings.telegramTracks) { idx, track ->
                                        val isSelectedPlaying = radioSettings.isPlayingTelegram && radioSettings.currentTelegramTrackIndex == idx
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelectedPlaying) Color(0xFF1B2A3D) else Color.Transparent)
                                                .clickable { radioSettings.playTelegramTrack(idx) }
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelectedPlaying) Icons.Default.PlayArrow else Icons.Default.Share,
                                                contentDescription = null,
                                                tint = if (isSelectedPlaying) Color(0xFF00FF66) else Color(0xFF8B949E),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(track.title, fontSize = 9.5.sp, fontWeight = if (isSelectedPlaying) FontWeight.Bold else FontWeight.Normal, color = if (isSelectedPlaying) Color(0xFF00FF66) else Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(track.duration, fontSize = 8.sp, color = Color(0xFF8B949E))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Фиксированная нижняя панель действий (НИКОГДА НЕ УЕЗЖАЕТ ЗА ЭКРАН)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activePlayerTab == 0) {
                        Button(
                            onClick = {
                                onDismiss()
                                viewModel.isRadioFullScreenOpen = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1.3f).height(32.dp)
                        ) {
                            Text(if (lang == "RU") "⚡ ДЭК РАДИО" else "⚡ RADIO DECK", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    } else if (activePlayerTab == 1) {
                        Button(
                            onClick = { filesLauncher.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838), contentColor = Color.White),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text(if (lang == "RU") "+ Файлы" else "+ Files", fontSize = 8.5.sp)
                        }
                    } else {
                        Button(
                            onClick = { radioSettings.fetchTelegramTracks() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838), contentColor = Color.White),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text(if (lang == "RU") "100 Треков" else "100 Tracks", fontSize = 8.5.sp)
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37), contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Text(if (lang == "RU") "Закрыть" else "Close", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
