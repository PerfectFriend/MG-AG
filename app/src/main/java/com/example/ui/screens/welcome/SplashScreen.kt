package com.example.ui.screens.welcome

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel
import kotlinx.coroutines.delay

/**
 * Атмосферная стартовая заставка (Splash Screen) с визуализацией фаз инициализации:
 * 1. Определение системного языка (если не поддерживается — EN).
 * 2. Загрузка ресурсов и скинов игрового поля.
 * 3. Настройка сетевого окружения (Tor, SimpleX, Радио).
 * Динамическое название: «ШЕШ БЕШ» для RU, «SHESH BESH» для всех остальных языков.
 */
@Composable
fun RomanticBackgammonSplashScreen(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    val isRu = lang == "RU"
    val appTitle = if (isRu) "ШЕШ БЕШ" else "SHESH BESH"

    var progress by remember { mutableFloatStateOf(0.10f) }
    var currentPhaseText by remember {
        mutableStateOf(if (isRu) "Определение системного языка..." else "Detecting system language...")
    }
    var detectedLangLabel by remember { mutableStateOf("") }
    var isReadyToEnter by remember { mutableStateOf(false) }

    // Glow pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "splash_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Sequential startup initialization progress
    LaunchedEffect(Unit) {
        // Фаза 1: Определение системного языка (0.10 -> 0.35)
        delay(400)
        val detected = viewModel.detectAndApplySystemLanguage()
        detectedLangLabel = when (detected) {
            "RU" -> "Русский (RU)"
            "EN" -> "English (EN)"
            "ES" -> "Español (ES)"
            "DE" -> "Deutsch (DE)"
            "FR" -> "Français (FR)"
            "TR" -> "Türkçe (TR)"
            "IT" -> "Italiano (IT)"
            "PT" -> "Português (PT)"
            "AR" -> "العربية (AR)"
            "ZH" -> "中文 (ZH)"
            else -> "English (EN)"
        }
        progress = 0.35f
        currentPhaseText = if (detected == "RU") {
            "Язык определён: $detectedLangLabel"
        } else {
            "Language detected: $detectedLangLabel"
        }
        delay(600)

        // Фаза 2: Загрузка ресурсов (0.35 -> 0.70)
        progress = 0.70f
        currentPhaseText = if (detected == "RU") {
            "Загрузка ресурсов и скинов доски..."
        } else {
            "Loading game assets and board skins..."
        }
        delay(650)

        // Фаза 3: Настройка сетевого окружения (0.70 -> 1.00)
        progress = 1.00f
        currentPhaseText = if (detected == "RU") {
            "Настройка сетевого окружения и радио..."
        } else {
            "Configuring network environment and radio..."
        }
        delay(650)

        // Завершение инициализации
        isReadyToEnter = true
        currentPhaseText = if (detected == "RU") {
            "Инициализация завершена • ШЕШ БЕШ готов"
        } else {
            "Initialization complete • SHESH BESH ready"
        }
        delay(700)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A0F))
            .clickable { onDismiss() }
            .testTag("splash_screen_root")
    ) {
        // High-Resolution Background Splash Art
        Image(
            painter = painterResource(id = com.example.R.drawable.img_backgammon_splash_1789082059077),
            contentDescription = "$appTitle Splash Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark vignette gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color(0xFF080D14).copy(alpha = 0.65f),
                            Color(0xFF05070B).copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Content layout
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Game Title (ШЕШ БЕШ for RU / SHESH BESH for others)
            Text(
                text = appTitle,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 2.5.sp,
                style = MaterialTheme.typography.headlineLarge.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFF00FF66).copy(alpha = 0.6f * glowAlpha),
                        blurRadius = 20f
                    )
                )
            )

            Text(
                text = if (isRu) "ТАКТИЧЕСКИЙ КИБЕРПАНК САЛОН" else "TACTICAL CYBERPUNK ARENA",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37),
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Progress Bar and Stage Info Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D1420).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF1E2C42), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentPhaseText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF00FF66),
                    trackColor = Color(0xFF1E2838)
                )
            }

            // Tap to continue badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f * glowAlpha))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isReadyToEnter) Color(0xFF00FF66) else Color(0xFFD4AF37))
                )
                Text(
                    text = if (isRu) "Коснитесь экрана, чтобы пропустить" else "Tap screen to continue",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
