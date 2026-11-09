package com.example.ui.screens.radio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RadioChannel
import com.example.ui.GameViewModel
import kotlin.math.sin

// Cyberpunk Palette
private val CyberBlack = Color(0xFF06090E)
private val CyberDarkSurface = Color(0xFF0C1017)
private val CyberCardBackground = Color(0xFF111722)
private val CyberNeonGreen = Color(0xFF00FF66)
private val CyberNeonGreenGlow = Color(0xFF00FF41)
private val CyberCyan = Color(0xFF00E5FF)
private val CyberAmber = Color(0xFFFFB300)
private val CyberRed = Color(0xFFFF3366)
private val CyberTextDim = Color(0xFF8B949E)
private val CyberBorder = Color(0xFF1E2638)

/**
 * Полноэкранный футуристический аудиоплеер и радиостанция в стиле Киберпанк / Матрица:
 * 1. Интеграция с Telegram-каналами и парсинг доступных аудио-треков с названиями, авторами и продолжительностью.
 * 2. Выбор любого трека из списка в 1 клик с моментальным воспроизведением.
 * 3. Анимированный спектральный эквалайзер / осциллограф на Canvas.
 * 4. Управление станциями, локальными треками, громкостью и режимами повтора/перемешивания.
 */
@Composable
fun FuturisticCyberpunkRadioScreen(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    BackHandler {
        onDismiss()
    }

    val radio = viewModel.radioManager
    val isPlaying = radio.isPlaying
    val isLoading = radio.isLoading || radio.isTelegramLoading
    val activeTitle = radio.activeTrackTitle

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Telegram, 1 = Live Radio, 2 = Local Tracks
    var channelInput by remember { mutableStateOf(radio.currentTelegramChannel) }
    var stationLangFilter by remember { mutableStateOf("ALL") }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { radio.addLocalFolder(it) }
    }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            radio.addLocalFiles(uris)
        }
    }

    // Infinite animation for cyberpunk HUD and visualizer
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_radio_hud")
    val visualizerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val neonGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Scaffold(
        containerColor = CyberBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CyberBlack)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ==========================================
            // TOP BAR: Futuristic Cyber HUD
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberDarkSurface)
                        .border(1.dp, CyberNeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("cyber_radio_back_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyberNeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "НАЗАД",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberNeonGreen
                    )
                }

                // Center Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CYBER-RADIO // MATRIX",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = CyberNeonGreen,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "v3.8 STEREO DECK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CyberCyan
                    )
                }

                // Live Stream Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPlaying) CyberNeonGreen.copy(alpha = 0.15f) else CyberDarkSurface)
                        .border(
                            1.dp,
                            if (isPlaying) CyberNeonGreen.copy(alpha = neonGlowAlpha) else CyberBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isPlaying) CyberNeonGreen else CyberRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isPlaying) "ON AIR" else "STANDBY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) CyberNeonGreen else CyberTextDim
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // HOLOGRAPHIC AUDIO VISUALIZER & TRACK HUD
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Telemetry Line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQ: 104.2 MHz // 320 KBPS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CyberCyan
                        )
                        Text(
                            text = if (radio.isPlayingTelegram) "SOURCE: TELEGRAM CLOUD"
                            else if (radio.isPlayingLocal) "SOURCE: LOCAL DISK"
                            else "SOURCE: INTERNET STREAM",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CyberAmber
                        )
                    }

                    // Track Title Marquee
                    Text(
                        text = if (isLoading) ">>> БУФЕРИЗАЦИЯ ПОТОКА ДАННЫХ <<<" else activeTitle,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isPlaying) CyberNeonGreen else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Real-time Canvas Animated Audio Frequency Visualizer (24 bars)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        val barCount = 28
                        val barWidth = size.width / barCount
                        val maxHeight = size.height

                        for (i in 0 until barCount) {
                            val wave1 = sin(visualizerPhase + i * 0.4f)
                            val wave2 = sin(visualizerPhase * 1.8f + i * 0.7f)
                            val rawAmp = (wave1 + wave2 + 2f) / 4f

                            val currentAmp = if (isPlaying) {
                                (0.15f + rawAmp * 0.8f).coerceIn(0.08f, 1.0f)
                            } else {
                                0.06f
                            }

                            val barHeight = maxHeight * currentAmp
                            val barX = i * barWidth + barWidth * 0.15f
                            val barActualWidth = barWidth * 0.7f

                            val brush = Brush.verticalGradient(
                                colors = listOf(
                                    CyberNeonGreen,
                                    CyberCyan,
                                    CyberDarkSurface
                                ),
                                startY = maxHeight - barHeight,
                                endY = maxHeight
                            )

                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(barX, maxHeight - barHeight),
                                size = Size(barActualWidth, barHeight),
                                cornerRadius = CornerRadius(2f, 2f)
                            )

                            // Peak dot
                            if (isPlaying) {
                                drawCircle(
                                    color = if (i % 3 == 0) CyberAmber else CyberNeonGreen,
                                    radius = 1.5f,
                                    center = Offset(barX + barActualWidth / 2f, (maxHeight - barHeight - 4f).coerceAtLeast(2f))
                                )
                            }
                        }
                    }

                    // Bottom stream diagnostics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS: ${if (isLoading) "BUFFERING..." else if (isPlaying) "STREAMING // OK" else "IDLE"}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            color = if (isPlaying) CyberNeonGreen else CyberTextDim
                        )
                        Text(
                            text = "E2EE QUANTUM TUNNEL // 0.0.0.0",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            color = CyberTextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // CYBERPUNK MODE SELECTOR TABS
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberDarkSurface, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    "✈️ TELEGRAM ТРЕКИ",
                    "📻 ОНЛАЙН ЭФИР",
                    "💾 СВОИ ТРЕКИ"
                )

                tabs.forEachIndexed { index, label ->
                    val isTabSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTabSelected) CyberCardBackground else Color.Transparent)
                            .border(
                                1.dp,
                                if (isTabSelected) CyberNeonGreen.copy(alpha = 0.8f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isTabSelected) CyberNeonGreen else CyberTextDim,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // TAB CONTENT AREA (WEIGHT 1F)
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> TelegramChannelTab(
                        viewModel = viewModel,
                        channelInput = channelInput,
                        onChannelInputChange = { channelInput = it },
                        onConnectClick = {
                            radio.setTelegramChannel(channelInput)
                        }
                    )
                    1 -> LiveRadioTab(
                        viewModel = viewModel,
                        selectedLang = stationLangFilter,
                        onSelectLang = { stationLangFilter = it }
                    )
                    2 -> LocalTracksTab(
                        viewModel = viewModel,
                        onOpenFolder = { folderLauncher.launch(null) },
                        onOpenFiles = { filesLauncher.launch(arrayOf("audio/*")) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // BOTTOM MASTER CYBER DECK & CONTROLS
            // ==========================================
            CyberDeckControls(
                viewModel = viewModel,
                isPlaying = isPlaying,
                isLoading = isLoading
            )
        }
    }
}

/**
 * Вкладка подключения к Telegram-каналу с возможностью спарсить треки
 * и выбрать любой трек из таблицы.
 */
@Composable
private fun TelegramChannelTab(
    viewModel: GameViewModel,
    channelInput: String,
    onChannelInputChange: (String) -> Unit,
    onConnectClick: () -> Unit
) {
    val radio = viewModel.radioManager
    val tracks = radio.telegramTracks
    val isLoading = radio.isTelegramLoading
    val currentTgIndex = radio.currentTelegramTrackIndex
    val isPlayingTg = radio.isPlayingTelegram && radio.isPlaying

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(CyberDarkSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        // Channel Input Row
        Text(
            text = "ПОДКЛЮЧЕНИЕ К TELEGRAM КАНАЛУ С МУЗЫКОЙ:",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = channelInput,
                onValueChange = onChannelInputChange,
                placeholder = {
                    Text(
                        "например: retrowaveru или @techno",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberTextDim
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("telegram_channel_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberNeonGreen,
                    unfocusedBorderColor = CyberBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyberNeonGreen,
                    focusedContainerColor = CyberBlack,
                    unfocusedContainerColor = CyberBlack
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                singleLine = true
            )

            Button(
                onClick = onConnectClick,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = CyberNeonGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("telegram_channel_connect_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = CyberBlack,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = CyberBlack,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "СПАРСИТЬ",
                            color = CyberBlack,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "retrowaveru" to "⚡ @retrowaveru (RetroWave)",
                "techno" to "🎛️ @techno (Electronic)",
                "cyberpunk" to "🤖 @cyberpunk (Cyberpunk)",
                "nightwaveplaza" to "🌌 @nightwaveplaza",
                "chillhop_radio" to "🎧 @chillhop_radio"
            )

            presets.forEach { (channel, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberCardBackground)
                        .border(0.5.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable {
                            onChannelInputChange(channel)
                            radio.setTelegramChannel(channel)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CyberCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "КАНАЛ: @${radio.currentTelegramChannel.uppercase()}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyberNeonGreen
            )
            Text(
                text = "НАЙДЕНО ТРЕКОВ: ${tracks.size}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CyberTextDim
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tracklist
        if (isLoading && tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyberNeonGreen, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "СКАНИРОВАНИЕ TELEGRAM ПОТОКА...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = CyberNeonGreen
                    )
                }
            }
        } else if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Треки не найдены или канал закрыт.\nПопробуйте ввести другой открытый канал Telegram.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = CyberTextDim,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    val isCurrent = radio.isPlayingTelegram && currentTgIndex == index
                    val isThisPlaying = isCurrent && isPlayingTg

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) CyberCardBackground else CyberDarkSurface)
                            .border(
                                1.dp,
                                if (isCurrent) CyberNeonGreen else CyberBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                radio.playTelegramTrack(index)
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .testTag("tg_track_item_$index"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Track Number / Playing indicator
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        if (isThisPlaying) CyberNeonGreen else CyberBlack,
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrent) CyberNeonGreen else CyberBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isThisPlaying) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Playing",
                                        tint = CyberBlack,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) CyberNeonGreen else CyberTextDim
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) CyberNeonGreen else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = if (isCurrent) CyberCyan else CyberTextDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Duration / Live badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberBlack)
                                .border(0.5.dp, CyberBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = track.duration,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = if (isCurrent) CyberNeonGreen else CyberTextDim
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Вкладка онлайн радиостанций со всего мира
 */
@Composable
private fun LiveRadioTab(
    viewModel: GameViewModel,
    selectedLang: String,
    onSelectLang: (String) -> Unit
) {
    val radio = viewModel.radioManager
    val channels = radio.channels
    val currentChannel = radio.currentChannel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(CyberDarkSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        // Language filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                "ALL" to "🌐 ВСЕ",
                "ru" to "🇷🇺 RU",
                "en" to "🇬🇧 EN",
                "es" to "🇪🇸 ES",
                "de" to "🇩🇪 DE",
                "fr" to "🇫🇷 FR",
                "tr" to "🇹🇷 TR",
                "onion" to "🧅 ONION"
            )

            filters.forEach { (code, label) ->
                val isSelected = selectedLang == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) CyberNeonGreen else CyberCardBackground)
                        .clickable { onSelectLang(code) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CyberBlack else CyberTextDim
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val filteredList = channels.filter {
            if (selectedLang == "ALL") true
            else it.id.startsWith("${selectedLang}_") || (selectedLang == "onion" && it.id.contains("onion"))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(filteredList) { index, channel ->
                val isCurrent = !radio.isPlayingTelegram && !radio.isPlayingLocal && currentChannel?.id == channel.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrent) CyberCardBackground else CyberDarkSurface)
                        .border(
                            1.dp,
                            if (isCurrent) CyberNeonGreen else CyberBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { radio.selectChannel(channel) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("radio_station_item_$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isCurrent && radio.isPlaying) Icons.Default.PlayArrow else Icons.Default.Radio,
                            contentDescription = null,
                            tint = if (isCurrent) CyberNeonGreen else CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = channel.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) CyberNeonGreen else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isCurrent && radio.isPlaying) {
                        Text(
                            text = "LIVE ●",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CyberNeonGreen
                        )
                    }
                }
            }
        }
    }
}

/**
 * Вкладка локальных аудио-треков пользователя
 */
@Composable
private fun LocalTracksTab(
    viewModel: GameViewModel,
    onOpenFolder: () -> Unit,
    onOpenFiles: () -> Unit
) {
    val radio = viewModel.radioManager
    val tracks = radio.localTracks

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(CyberDarkSurface)
            .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenFolder,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Text(
                    "📁 ПАПКА С МУЗЫКОЙ",
                    color = CyberBlack,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onOpenFiles,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCardBackground),
                border = BorderStroke(1.dp, CyberCyan),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Text(
                    "🎵 ВЫБРАТЬ ФАЙЛЫ",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Нет загруженных аудио-файлов.\nНажмите «Папка с музыкой» или «Выбрать файлы».",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = CyberTextDim,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    val isCurrent = radio.isPlayingLocal && radio.currentTrackIndex == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) CyberCardBackground else CyberDarkSurface)
                            .border(
                                1.dp,
                                if (isCurrent) CyberNeonGreen else CyberBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { radio.playLocalTrack(index) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isCurrent) CyberNeonGreen else CyberTextDim,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = track.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (isCurrent) CyberNeonGreen else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Нижняя контрольная панель управления воспроизведением (Cyber Deck)
 */
@Composable
private fun CyberDeckControls(
    viewModel: GameViewModel,
    isPlaying: Boolean,
    isLoading: Boolean
) {
    val radio = viewModel.radioManager

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDarkSurface),
        border = BorderStroke(1.dp, CyberNeonGreen.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main control buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = { radio.isShuffle = !radio.isShuffle },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (radio.isShuffle) CyberNeonGreen else CyberTextDim,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Previous Track
                IconButton(
                    onClick = {
                        if (radio.isPlayingTelegram) radio.prevTelegramTrack()
                        else if (radio.isPlayingLocal) radio.prevLocalTrack()
                        else radio.prevChannel()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = CyberCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Master Play/Pause circular button with neon border
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) CyberNeonGreen else CyberCardBackground)
                        .border(2.dp, CyberNeonGreen, CircleShape)
                        .clickable { radio.togglePlay() }
                        .testTag("cyber_radio_master_play_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = if (isPlaying) CyberBlack else CyberNeonGreen,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = if (isPlaying) CyberBlack else CyberNeonGreen,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Next Track
                IconButton(
                    onClick = {
                        if (radio.isPlayingTelegram) radio.nextTelegramTrack()
                        else if (radio.isPlayingLocal) radio.nextLocalTrack()
                        else radio.nextChannel()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = CyberCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Repeat Button
                IconButton(
                    onClick = { radio.isRepeat = !radio.isRepeat },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (radio.isRepeat) CyberNeonGreen else CyberTextDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
