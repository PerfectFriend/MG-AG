package com.example.ui.screens.board

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Player
import com.example.ui.GameViewModel

/**
 * Отрисовка отдельного пункта (стрелки) на доске нард «Шеш-Беш».
 *
 * Интерактивность:
 * - Отображает треугольник пункта с направлением острия вверх или вниз.
 * - Анимированная пульсация зеленого контура при выборе исходного пункта.
 * - Анимированная пульсация бирюзового контура для допустимых целевых пунктов хода.
 * - Вертикальный стек фишек с перекрытием слоев и индикацией переполнения (>6 фишек).
 * - Назначен testTag `point_cell_$absoluteIndex` для автотестов.
 *
 * @param absoluteIndex абсолютный индекс пункта на доске (0..23).
 * @param pointingUp острие направлено вверх (нижний ряд пунктов) или вниз (верхний ряд).
 * @param viewModel главная вьюмодель управления партией.
 * @param modifier модификатор верстки.
 */
@Composable
fun PointCell(
    absoluteIndex: Int,
    pointingUp: Boolean,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val relativeIndex = viewModel.absoluteToRelative(viewModel.activePlayer, absoluteIndex)

    val isSelected = viewModel.selectedPointIndex == relativeIndex

    val isHighlightedDestination = remember(viewModel.selectedPointIndex, viewModel.reachablePaths) {
        viewModel.reachablePaths.any { it.finalTo == relativeIndex }
    }

    val stack = viewModel.boardState[absoluteIndex]

    val themeSpecs = remember(viewModel.selectedTheme) { BoardThemeSpecs(viewModel.selectedTheme) }
    val darkTriangleColor = themeSpecs.darkTriangle
    val lightTriangleColor = themeSpecs.lightTriangle
    val triangleColor = if (absoluteIndex % 2 == 0) darkTriangleColor else lightTriangleColor

    val glowAnim by rememberInfiniteTransition(label = "PointGlow").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = { viewModel.handlePointClicked(relativeIndex) })
            .testTag("point_cell_$absoluteIndex")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trianglePath = Path().apply {
                if (pointingUp) {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                } else {
                    moveTo(size.width / 2f, size.height)
                    lineTo(size.width, 0f)
                    lineTo(0f, 0f)
                    close()
                }
            }

            drawPath(path = trianglePath, color = triangleColor)

            if (isSelected) {
                drawPath(
                    path = trianglePath,
                    color = Color.Green,
                    style = Stroke(width = 3.dp.toPx()),
                    alpha = glowAnim
                )
            } else if (isHighlightedDestination) {
                drawPath(
                    path = trianglePath,
                    color = Color.Cyan,
                    style = Stroke(width = 3.dp.toPx()),
                    alpha = glowAnim
                )
            }
        }

        val outerNumber = absoluteIndex + 1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Text(
                text = outerNumber.toString(),
                fontSize = 8.sp,
                color = Color(0xFFA59283).copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )
        }

        if (stack.count > 0 && stack.player != null) {
            val maxCheckersToDraw = 6
            val drawCount = if (stack.count > maxCheckersToDraw) maxCheckersToDraw else stack.count

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.padding(bottom = if (pointingUp) 8.dp else 0.dp, top = if (!pointingUp) 8.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (pointingUp) Arrangement.Bottom else Arrangement.Top
                ) {
                    Box(
                        modifier = Modifier.width(48.dp).height(165.dp),
                        contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter
                    ) {
                        val stepY = if (drawCount > 4) 19.5.dp else 24.dp

                        for (i in 0 until drawCount) {
                            val offsetMultiplier = if (pointingUp) i else (drawCount - 1 - i)
                            val yOffset = if (pointingUp) {
                                -(offsetMultiplier * stepY.value).dp
                            } else {
                                (offsetMultiplier * stepY.value).dp
                            }

                            val visualColor = stack.player ?: Player.WHITE
                            CheckerPiece(
                                player = visualColor,
                                themeId = viewModel.selectedTheme,
                                modifier = Modifier
                                    .size(46.dp)
                                    .offset(y = yOffset),
                                showNumberLabel = (i == drawCount - 1 && stack.count > maxCheckersToDraw),
                                labelCount = stack.count
                            )
                        }
                    }
                }
            }
        }
    }
}
