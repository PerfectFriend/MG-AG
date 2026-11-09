package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * UI Feedback component displaying the incrementing PIN retry delay countdown timer.
 * Provides real-time countdown visualization (minutes:seconds), radial circular progress,
 * pulsing security alert cues, and informative threat mitigation messaging.
 */
@Composable
fun PinCountdownTimerFeedback(
    remainingSeconds: Int,
    totalSeconds: Int,
    failedAttempts: Int,
    modifier: Modifier = Modifier,
    isMatrixStyle: Boolean = true,
    lang: String = "EN"
) {
    val progress = remember(remainingSeconds, totalSeconds) {
        if (totalSeconds > 0) {
            (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "countdown_progress"
    )

    // Pulsing heartbeat animation for security shield
    val infiniteTransition = rememberInfiniteTransition(label = "security_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val primaryColor = if (isMatrixStyle) Color(0xFFFF4141) else MaterialTheme.colorScheme.error
    val accentColor = if (isMatrixStyle) Color(0xFFFFB300) else Color(0xFFFF9800)
    val cardBg = if (isMatrixStyle) Color(0xFF140808) else Color(0xFF1E1717)
    val borderColor = if (isMatrixStyle) Color(0xFFFF4141).copy(alpha = 0.6f) else primaryColor.copy(alpha = 0.5f)

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pin_countdown_timer_card")
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = primaryColor.copy(alpha = 0.3f),
                spotColor = primaryColor.copy(alpha = 0.4f)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header badge: Brute-Force Defense Active
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = primaryColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                modifier = Modifier.testTag("pin_lockout_warning")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Security Alert",
                        tint = primaryColor,
                        modifier = Modifier
                            .size(14.dp)
                            .scale(pulseScale)
                    )
                    Text(
                        text = if (lang == "RU") "ЗАЩИТА ОТ ПОДБОРА (BRUTE-FORCE)" else "SECURITY LOCKOUT ACTIVE",
                        color = primaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isMatrixStyle) FontFamily.Monospace else FontFamily.Default,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Radial progress with timer in center
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Track and Progress Arc
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 6.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    val arcSize = Size(diameter, diameter)

                    // Background circle track
                    drawArc(
                        color = primaryColor.copy(alpha = 0.15f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )

                    // Animated countdown remaining arc (sweeping backwards as time ticks down)
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(primaryColor, accentColor, primaryColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Inner digital countdown timer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = primaryColor,
                        modifier = Modifier
                            .size(16.dp)
                            .scale(pulseScale)
                    )
                    Text(
                        text = formattedTime,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.testTag("pin_countdown_timer_text")
                    )
                }
            }

            // Subtitle status: Attempt penalty & retry notice
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (lang == "RU") {
                        "Неверных попыток: $failedAttempts | Задержка: ${totalSeconds}с"
                    } else {
                        "Failed Attempts: $failedAttempts • Retry Delay: ${totalSeconds}s"
                    },
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isMatrixStyle) FontFamily.Monospace else FontFamily.Default,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (lang == "RU") {
                        "Клавиатура заблокирована. Повторите ввод через $remainingSeconds сек."
                    } else {
                        "Keypad locked. Wait $remainingSeconds seconds before retrying PIN."
                    },
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = if (isMatrixStyle) FontFamily.Monospace else FontFamily.Default,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }

            // Linear bar feedback as an additional visual reference
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .testTag("pin_countdown_linear_progress"),
                color = primaryColor,
                trackColor = primaryColor.copy(alpha = 0.2f)
            )
        }
    }
}
