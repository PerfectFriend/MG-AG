package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 3D-анимированный кубик (Зарик) для игры в нарды «Шеш-Беш».
 *
 * Особенности:
 * - Многоосевое 3D-вращение (X, Y, Z) с имитацией физического броска.
 * - Вертикальный отскок и горизонтальное покачивание при броске.
 * - Цветовая палитра адаптируется под активную тему доски (wood, marine, malachite, cosmic, psychedelic, bw).
 * - Отрисовка классических точек (pips) на `Canvas` с точными координатами 1..6.
 *
 * @param value выпавшее значение на грани кубика (1..6).
 * @param isRolling флаг активной анимации броска кости.
 * @param themeId идентификатор темы оформления («wood», «marine», «malachite» и т.д.).
 */
@Composable
fun DiceCube(value: Int, isRolling: Boolean, themeId: String = "warm") {
    val id = themeId.lowercase()

    // Многоосевое 3D вращение
    val rotX by animateFloatAsState(
        targetValue = if (isRolling) 1260f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val rotY by animateFloatAsState(
        targetValue = if (isRolling) 1440f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(550, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val rotZ by animateFloatAsState(
        targetValue = if (isRolling) 1080f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(650, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    // Вертикальный отскок кости от деревянной доски
    val bounceY by animateFloatAsState(
        targetValue = if (isRolling) -35f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
        }
    )

    // Горизонтальное смещение / вибрация
    val rollX by animateFloatAsState(
        targetValue = if (isRolling) 15f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(350, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
        }
    )

    // Масштабирование глубины при броске
    val scaleFactor by animateFloatAsState(
        targetValue = if (isRolling) 1.3f else 1f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val normalizedId = when (id) {
        "warm", "vintage" -> "wood"
        "water", "sapphire", "sea" -> "marine"
        "emerald" -> "malachite"
        "space" -> "cosmic"
        "rainbow", "cyberpunk" -> "psychedelic"
        else -> id
    }

    val diceBg = when (normalizedId) {
        "marine" -> Color(0xFFE0F7FA)
        "malachite" -> Color(0xFFE8F5E9)
        "cosmic" -> Color(0xFFF3E5F5)
        "psychedelic" -> Color(0xFFFFF3E0)
        "bw" -> Color(0xFFF5F5F5)
        else -> Color(0xFFFFFDF7) // Теплая слоновая кость для классического дерева
    }

    val dotColor = when (normalizedId) {
        "marine" -> Color(0xFF006064)
        "malachite" -> Color(0xFF1B5E20)
        "cosmic" -> Color(0xFF4A148C)
        "psychedelic" -> Color(0xFFE65100)
        "bw" -> Color(0xFF1A1A1A)
        else -> Color(0xFF8B2500) // Глубокий терракотовый цвет точек
    }

    val borderColor = when (normalizedId) {
        "marine" -> Color(0xFF00ACC1)
        "malachite" -> Color(0xFF43A047)
        "cosmic" -> Color(0xFF8E24AA)
        "psychedelic" -> Color(0xFFFB8C00)
        "bw" -> Color(0xFF757575)
        else -> Color(0xFFD4AF37) // Полированная латунь
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                rotationZ = rotZ
                translationY = bounceY
                translationX = rollX
                scaleX = scaleFactor
                scaleY = scaleFactor
                cameraDistance = 12f * density
            }
            .shadow(if (isRolling) 10.dp else 2.5.dp, RoundedCornerShape(8.dp))
            .background(diceBg, RoundedCornerShape(8.dp))
            .border(0.8.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                val d = size.width
                val r = 2.4.dp.toPx()
                val color = dotColor

                when (value) {
                    1 -> {
                        drawCircle(color, r, Offset(d / 2, d / 2))
                    }
                    2 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    3 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 2, d / 2))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    4 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 4, 3 * d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    5 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 2, d / 2))
                        drawCircle(color, r, Offset(d / 4, 3 * d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                    6 -> {
                        drawCircle(color, r, Offset(d / 4, d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, d / 4))
                        drawCircle(color, r, Offset(d / 4, d / 2))
                        drawCircle(color, r, Offset(3 * d / 4, d / 2))
                        drawCircle(color, r, Offset(d / 4, 3 * d / 4))
                        drawCircle(color, r, Offset(3 * d / 4, 3 * d / 4))
                    }
                }
            }
        }
    }
}
