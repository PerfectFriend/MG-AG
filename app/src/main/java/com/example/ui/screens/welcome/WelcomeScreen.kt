package com.example.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameMode
import com.example.ui.GameViewModel
import com.example.ui.screens.*

/**
 * Экран первоначальной настройки и выбора параметров матча (Welcome Onboarding Wizard):
 * Шаг 1: Выбор языка интерфейса и озвучки.
 * Шаг 2: Выбор радиостанции / фонового музыкального сопровождения.
 * Шаг 3: Выбор варианта правил (Бешеные, Длинные, Короткие нарды).
 * Шаг 4: Выбор формата матча (Против бота, Вдвоем на одном экране, P2P Онлайн).
 * Шаг 5: Имена игроков и запуск матча.
 */
@Composable
fun WelcomeScreenLayout(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.selectedLanguage
    val radioState = viewModel.radioManager

    // Ensure player names always start as localized "Игрок" / "Player" when opening the welcome wizard
    LaunchedEffect(Unit) {
        viewModel.userName = Language.get("welcome_placeholder_player", viewModel.selectedLanguage)
        viewModel.player1Name = Language.get("welcome_p1_placeholder", viewModel.selectedLanguage)
        viewModel.player2Name = Language.get("welcome_p2_placeholder", viewModel.selectedLanguage)
    }

    SalonWallpaperBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .wrapContentHeight()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            SalonHeaderMedallion(
                title = Language.get("welcome_title", lang),
                slogan = Language.get("welcome_slogan", lang)
            )

            SalonStepJewelsRow(
                currentStep = viewModel.welcomeStep,
                onStepClick = { step -> viewModel.welcomeStep = step }
            )

            SalonMainPlaque {
                when (viewModel.welcomeStep) {
                    1 -> {
                        Text(
                            text = "⚜️ SELECT LANGUAGE / ВЫБЕРИТЕ ЯЗЫК",
                            fontWeight = FontWeight.Black,
                            color = SalonGoldLight,
                            fontSize = 14.sp,
                            letterSpacing = 0.6.sp
                        )

                        val languages = listOf(
                            Triple("RU", "🇷🇺 Русский", "Озвучка и реплики"),
                            Triple("EN", "🇬🇧 English", "Voiceover & comments"),
                            Triple("DE", "🇩🇪 Deutsch", "Spielkommentare"),
                            Triple("ES", "🇪🇸 Español", "Comentarios de juego"),
                            Triple("FR", "🇫🇷 Français", "Commentaires de jeu"),
                            Triple("TR", "🇹🇷 Türkçe", "Yorumlar"),
                            Triple("IT", "🇮🇹 Italiano", "Commenti di gioco"),
                            Triple("PT", "🇵🇹 Português", "Comentários de jogo"),
                            Triple("AR", "🇸🇦 العربية", "تعليقات اللعبة"),
                            Triple("ZH", "🇨🇳 中文", "游戏解说")
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            languages.chunked(2).forEach { rowLangs ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowLangs.forEach { (code, name, subtitle) ->
                                        val isSelected = viewModel.selectedLanguage == code
                                        SalonOptionPlaque(
                                            title = name,
                                            subtitle = subtitle,
                                            isSelected = isSelected,
                                            accentColor = SalonGold,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                viewModel.updateLanguage(code)
                                                viewModel.welcomeStep = 2
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Language.get("welcome_select_radio", lang),
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = SalonGoldLight
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SalonGold.copy(alpha = 0.2f))
                                    .border(1.dp, SalonGold, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = viewModel.selectedLanguage,
                                    fontSize = 11.sp,
                                    color = SalonGoldLight,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Text(
                            text = Language.get("welcome_radio_subtitle", lang),
                            fontSize = 11.5.sp,
                            color = SalonSubtext,
                            textAlign = TextAlign.Center
                        )

                        val filteredChannels = radioState.channels.filter { channel ->
                            val langLower = viewModel.selectedLanguage.lowercase()
                            channel.id.startsWith(langLower) || channel.isCustom
                        }
                        val displayChannels = if (filteredChannels.isEmpty()) radioState.channels.take(3) else filteredChannels

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SalonOptionPlaque(
                                title = Language.get("welcome_radio_disable", lang),
                                leadingEmoji = "🔇",
                                isSelected = !viewModel.welcomePlayRadio,
                                accentColor = SalonTurquoise,
                                onClick = { viewModel.welcomePlayRadio = false }
                            )

                            displayChannels.forEach { channel ->
                                val isSelected = viewModel.welcomePlayRadio && viewModel.welcomeSelectedChannelId == channel.id
                                SalonOptionPlaque(
                                    title = channel.name,
                                    leadingEmoji = "📻",
                                    isSelected = isSelected,
                                    accentColor = SalonGold,
                                    onClick = {
                                        viewModel.welcomePlayRadio = true
                                        viewModel.welcomeSelectedChannelId = channel.id
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SalonSecondaryButton(
                                text = "← " + Language.get("welcome_back", lang),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.welcomeStep = 1 }
                            )
                            SalonPrimaryButton(
                                text = Language.get("welcome_next", lang) + " ➔",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.welcomeStep = 3 }
                            )
                        }
                    }

                    3 -> {
                        Text(
                            text = if (lang == "RU") "🎲 ВЫБЕРИТЕ РЕЖИМ ПРАВИЛ" else "🎲 SELECT GAME RULES",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.5.sp,
                            color = SalonGoldLight
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            SalonOptionPlaque(
                                title = Language.get("game_mode_mad_title", lang),
                                subtitle = Language.get("game_mode_mad_desc", lang),
                                leadingEmoji = "🔥",
                                isSelected = viewModel.selectedGameMode == GameMode.MAD,
                                accentColor = SalonAmber,
                                onClick = {
                                    viewModel.selectedGameMode = GameMode.MAD
                                    viewModel.welcomeStep = 4
                                }
                            )

                            SalonOptionPlaque(
                                title = Language.get("game_mode_long_title", lang),
                                subtitle = Language.get("game_mode_long_desc", lang),
                                leadingEmoji = "📜",
                                isSelected = viewModel.selectedGameMode == GameMode.LONG,
                                accentColor = SalonGold,
                                onClick = {
                                    viewModel.selectedGameMode = GameMode.LONG
                                    viewModel.welcomeStep = 4
                                }
                            )

                            SalonOptionPlaque(
                                title = Language.get("game_mode_short_title", lang),
                                subtitle = Language.get("game_mode_short_desc", lang),
                                leadingEmoji = "⚡",
                                isSelected = viewModel.selectedGameMode == GameMode.SHORT,
                                accentColor = SalonTurquoise,
                                onClick = {
                                    viewModel.selectedGameMode = GameMode.SHORT
                                    viewModel.welcomeStep = 4
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SalonSecondaryButton(
                                text = "← " + Language.get("welcome_back", lang),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.welcomeStep = 2 }
                            )
                            SalonPrimaryButton(
                                text = Language.get("welcome_next", lang) + " ➔",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.welcomeStep = 4 }
                            )
                        }
                    }

                    4 -> {
                        Text(
                            text = Language.get("welcome_select_mode", lang),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.5.sp,
                            color = SalonGoldLight
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            SalonOptionPlaque(
                                title = Language.get("welcome_mode_bot", lang),
                                leadingEmoji = "🤖",
                                isSelected = viewModel.selectedWelcomeMode == 0,
                                accentColor = SalonGold,
                                onClick = {
                                    viewModel.selectedWelcomeMode = 0
                                    viewModel.welcomeStep = 5
                                }
                            )

                            SalonOptionPlaque(
                                title = Language.get("welcome_mode_local", lang),
                                leadingEmoji = "👥",
                                isSelected = viewModel.selectedWelcomeMode == 1,
                                accentColor = SalonEmerald,
                                onClick = {
                                    viewModel.selectedWelcomeMode = 1
                                    viewModel.welcomeStep = 5
                                }
                            )

                            SalonOptionPlaque(
                                title = Language.get("welcome_mode_online", lang),
                                leadingEmoji = "🌐",
                                isSelected = viewModel.selectedWelcomeMode == 2,
                                accentColor = SalonTurquoise,
                                onClick = {
                                    viewModel.selectedWelcomeMode = 2
                                    viewModel.welcomeStep = 5
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SalonSecondaryButton(
                                text = "← " + Language.get("welcome_back", lang),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.welcomeStep = 3 }
                            )
                            SalonPrimaryButton(
                                text = Language.get("welcome_next", lang) + " ➔",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.welcomeStep = 5 }
                            )
                        }
                    }

                    5 -> {
                        Text(
                            text = Language.get("welcome_ask_name", lang),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.5.sp,
                            color = SalonGoldLight
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (viewModel.selectedWelcomeMode == 1) {
                                SalonNameField(
                                    value = viewModel.player1Name,
                                    onValueChange = { viewModel.player1Name = it },
                                    label = Language.get("welcome_p1_label", lang),
                                    placeholder = Language.get("welcome_p1_placeholder", lang),
                                    accentColor = SalonGoldLight
                                )
                                SalonNameField(
                                    value = viewModel.player2Name,
                                    onValueChange = { viewModel.player2Name = it },
                                    label = Language.get("welcome_p2_label", lang),
                                    placeholder = Language.get("welcome_p2_placeholder", lang),
                                    accentColor = SalonTurquoise
                                )
                            } else {
                                SalonNameField(
                                    value = viewModel.userName,
                                    onValueChange = { viewModel.userName = it },
                                    label = Language.get("welcome_name_your", lang),
                                    placeholder = Language.get("welcome_placeholder_player", lang),
                                    accentColor = SalonGoldLight
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SalonSecondaryButton(
                                text = "← " + Language.get("welcome_back", lang),
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.welcomeStep = 4 }
                            )
                            SalonPrimaryButton(
                                text = Language.get("welcome_btn_finish", lang),
                                modifier = Modifier.weight(1.35f),
                                onClick = {
                                    if (viewModel.userName.isBlank()) {
                                        viewModel.userName = Language.get("welcome_placeholder_player", lang)
                                    }
                                    if (viewModel.player1Name.isBlank()) {
                                        viewModel.player1Name = Language.get("welcome_p1_placeholder", lang)
                                    }
                                    if (viewModel.player2Name.isBlank()) {
                                        viewModel.player2Name = Language.get("welcome_p2_placeholder", lang)
                                    }
                                    viewModel.startGameWithSettings()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
