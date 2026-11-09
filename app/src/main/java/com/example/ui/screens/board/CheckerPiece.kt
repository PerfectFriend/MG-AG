package com.example.ui.screens.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Player

/**
 * Отрисовка фишки (шашки) для нардов «Шеш-Беш».
 *
 * Особенности:
 * - Радиальный градиент с имитацией объема, фаски и отражения света.
 * - Внутреннее рельефное кольцо (ridge ring), характерное для настоящих турнирных фишек.
 * - При наложении более 6 фишек в один пункт выводится бейдж с числом (labelCount).
 * - Автоматически перекрашивается в соответствии с темой оформления доски.
 *
 * @param player цвет владельца фишки (WHITE или BLACK).
 * @param themeId активная тема оформления доски.
 * @param modifier модификатор верстки и позиционирования.
 * @param showNumberLabel флаг отображения счетчика при переполнении стека.
 * @param labelCount общее количество фишек в данном пункте.
 */
@Composable
fun CheckerPiece(
    player: Player,
    themeId: String,
    modifier: Modifier = Modifier,
    showNumberLabel: Boolean = false,
    labelCount: Int = 0
) {
    val id = when (themeId.lowercase()) {
        "warm", "vintage" -> "wood"
        "water", "sapphire", "sea" -> "marine"
        "emerald" -> "malachite"
        "space" -> "cosmic"
        "rainbow", "cyberpunk" -> "psychedelic"
        else -> themeId.lowercase()
    }

    val pieceGradient = remember(player, id) {
        if (player == Player.WHITE) {
            val colors = when (id) {
                "marine" -> listOf(Color(0xFFFFFFFF), Color(0xFFE0F7FA), Color(0xFFB2EBF2))
                "malachite" -> listOf(Color(0xFFF1F8E9), Color(0xFFDCEDC8), Color(0xFFAED581))
                "cosmic" -> listOf(Color(0xFFE0FFFF), Color(0xFF80FFE5), Color(0xFF00E5FF))
                "psychedelic" -> listOf(Color(0xFFFFF9C4), Color(0xFFFFE082), Color(0xFFFFB300))
                "bw" -> listOf(Color(0xFFFFFFFF), Color(0xFFE2E2E2), Color(0xFFCCCCCC))
                else -> listOf(Color(0xFFFFFFFF), Color(0xFFF3EEE6), Color(0xFFD7CCC8))
            }
            Brush.radialGradient(colors = colors, center = Offset(10f, 10f))
        } else {
            val colors = when (id) {
                "marine" -> listOf(Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF01142F))
                "malachite" -> listOf(Color(0xFF1B5E20), Color(0xFF0E3813), Color(0xFF041908))
                "cosmic" -> listOf(Color(0xFF4A148C), Color(0xFF2A084E), Color(0xFF0F011E))
                "psychedelic" -> listOf(Color(0xFF6A0DAD), Color(0xFF3D087B), Color(0xFF1B003B))
                "bw" -> listOf(Color(0xFF333333), Color(0xFF1B1B1B), Color(0xFF000000))
                else -> listOf(Color(0xFF3E2723), Color(0xFF21130D), Color(0xFF0F0804))
            }
            Brush.radialGradient(colors = colors, center = Offset(10f, 10f))
        }
    }

    val borderColor = remember(player, id) {
        if (player == Player.WHITE) {
            when (id) {
                "marine" -> Color(0xFF00E5FF)
                "malachite" -> Color(0xFFFFD700)
                "cosmic" -> Color(0xFF00FFCC)
                "psychedelic" -> Color(0xFFFF5722)
                "bw" -> Color(0xFFFFFFFF)
                else -> Color(0xFFB08D57)
            }
        } else {
            when (id) {
                "marine" -> Color(0xFF40C4FF)
                "malachite" -> Color(0xFFD4AF37)
                "cosmic" -> Color(0xFFFF007F)
                "psychedelic" -> Color(0xFF00FFFF)
                "bw" -> Color(0xFF555555)
                else -> Color(0xFFD4AF37)
            }
        }
    }

    val innerRingColor = remember(player, id) {
        if (player == Player.WHITE) {
            when (id) {
                "marine" -> Color(0xFF80DEEA)
                "malachite" -> Color(0xFF81C784)
                "cosmic" -> Color(0xFF00BFA5)
                "psychedelic" -> Color(0xFFFFD54F)
                "bw" -> Color(0xFFCCCCCC)
                else -> Color(0xFFC7B299)
            }
        } else {
            when (id) {
                "marine" -> Color(0xFF0A3060)
                "malachite" -> Color(0xFF104018)
                "cosmic" -> Color(0xFF7B1FA2)
                "psychedelic" -> Color(0xFF9C27B0)
                "bw" -> Color(0xFF222222)
                else -> Color(0xFF5D4037)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(pieceGradient)
            .border(
                width = 1.3.dp,
                color = borderColor,
                shape = CircleShape
            )
            .shadow(2.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.6f)
                .border(
                    width = 1.dp,
                    color = innerRingColor,
                    shape = CircleShape
                )
        )

        if (showNumberLabel) {
            Text(
                text = labelCount.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = if (player == Player.WHITE) Color.Black else Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
