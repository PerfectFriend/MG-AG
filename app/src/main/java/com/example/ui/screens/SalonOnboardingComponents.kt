package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlin.math.cos
import kotlin.math.sin

val SalonGoldLight = Color(0xFFFFE082)
val SalonGold = Color(0xFFFFCA28)
val SalonAmber = Color(0xFFFF8F00)
val SalonCopper = Color(0xFFD84315)
val SalonEmerald = Color(0xFF00E676)
val SalonTurquoise = Color(0xFF00E5FF)
val SalonCream = Color(0xFFFFF8E1)
val SalonSubtext = Color(0xFFE6D5B8)

@Composable
fun SalonWallpaperBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "salon_bg_glow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "salon_phase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "salon_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1F1018)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_welcome_lounge_bg_1790459252918),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Warm Golden-Velvet & Mahogany Vignette Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x8826111B),
                            Color(0x661C1224),
                            Color(0x99231217),
                            Color(0xCC180C12)
                        )
                    )
                )
        )

        // Floating Golden & Turquoise Mosaic Lights
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val lights = listOf(
                Triple(0.18f, 0.20f, SalonGold),
                Triple(0.82f, 0.18f, SalonTurquoise),
                Triple(0.15f, 0.75f, SalonAmber),
                Triple(0.85f, 0.78f, SalonEmerald),
                Triple(0.50f, 0.12f, SalonGoldLight)
            )
            lights.forEachIndexed { idx, (bx, by, col) ->
                val cx = w * bx + cos(phase + idx * 1.3f) * 20f
                val cy = h * by + sin(phase + idx * 1.6f) * 20f
                val center = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(col.copy(alpha = 0.24f * pulse), Color.Transparent),
                        center = center,
                        radius = 140f
                    ),
                    radius = 140f,
                    center = center
                )
                drawCircle(
                    color = col.copy(alpha = 0.7f * pulse),
                    radius = 3.5f,
                    center = center
                )
            }
        }

        content()
    }
}

@Composable
fun SalonHeaderMedallion(title: String, slogan: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SalonGoldLight, SalonAmber, SalonCopper)
                    )
                )
                .border(2.dp, SalonCream, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🎲", fontSize = 26.sp)
        }

        Text(
            text = title,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            color = SalonGoldLight,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp,
            style = TextStyle(
                shadow = Shadow(color = Color(0xFFBF360C), blurRadius = 16f)
            )
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x992C1622))
                .border(1.dp, SalonGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp)
        ) {
            Text(
                text = slogan,
                fontSize = 9.sp,
                color = SalonTurquoise,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SalonStepJewelsRow(currentStep: Int, onStepClick: (Int) -> Unit) {
    val icons = listOf("🌍", "📻", "📜", "⚔️", "👑")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val isActive = currentStep == i
            val isPassed = currentStep > i
            Box(
                modifier = Modifier
                    .size(if (isActive) 34.dp else 28.dp)
                    .shadow(if (isActive) 8.dp else 2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> Brush.radialGradient(listOf(SalonGoldLight, SalonAmber, SalonCopper))
                            isPassed -> Brush.radialGradient(listOf(SalonEmerald, Color(0xFF00695C)))
                            else -> Brush.radialGradient(listOf(Color(0xCC2E1929), Color(0xCC1B1222)))
                        }
                    )
                    .border(
                        width = if (isActive) 1.8.dp else 1.dp,
                        color = when {
                            isActive -> SalonCream
                            isPassed -> SalonEmerald
                            else -> SalonGold.copy(alpha = 0.4f)
                        },
                        shape = CircleShape
                    )
                    .clickable { if (i < currentStep) onStepClick(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPassed) "✓" else icons[i - 1],
                    fontSize = if (isActive) 15.sp else 12.sp,
                    color = SalonCream,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun SalonMainPlaque(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xEB2D1624),
                        Color(0xF01D172B),
                        Color(0xEB281711)
                    )
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        SalonGold,
                        SalonTurquoise.copy(alpha = 0.75f),
                        SalonAmber,
                        SalonGoldLight
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            SalonAmber,
                            SalonGoldLight,
                            SalonTurquoise,
                            SalonEmerald,
                            SalonGoldLight,
                            SalonAmber
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
fun SalonOptionPlaque(
    title: String,
    subtitle: String? = null,
    leadingEmoji: String? = null,
    isSelected: Boolean,
    accentColor: Color = SalonGold,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
) {
    val bgBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xF24A2412),
                Color(0xF233182E),
                Color(0xF21B2E2B)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xCC261522),
                Color(0xCC1B1526)
            )
        )
    }

    val borderBrush = if (isSelected) {
        Brush.horizontalGradient(listOf(SalonGoldLight, accentColor, SalonTurquoise))
    } else {
        Brush.horizontalGradient(listOf(SalonGold.copy(alpha = 0.35f), Color.White.copy(alpha = 0.15f)))
    }

    Row(
        modifier = modifier
            .shadow(if (isSelected) 8.dp else 3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(if (isSelected) 2.dp else 1.dp, borderBrush, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leadingEmoji != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.28f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .border(1.dp, if (isSelected) accentColor else SalonGold.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = leadingEmoji, fontSize = 18.sp)
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = if (isSelected) SalonGoldLight else SalonCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = SalonSubtext,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(SalonEmerald.copy(alpha = 0.22f))
                    .border(1.dp, SalonEmerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = SalonEmerald,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Text("➔", color = SalonGold.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SalonPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        SalonAmber,
                        SalonGold,
                        SalonGoldLight,
                        SalonAmber
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.horizontalGradient(listOf(SalonCream, SalonGoldLight, Color.White)),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Black,
            fontSize = 12.5.sp,
            letterSpacing = 0.6.sp,
            color = Color(0xFF210E04),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SalonSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF381D2A), Color(0xFF241834))
                )
            )
            .border(
                1.4.dp,
                Brush.horizontalGradient(listOf(SalonGold.copy(alpha = 0.8f), SalonTurquoise.copy(alpha = 0.6f))),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            color = SalonGoldLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SalonNameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    accentColor: Color = SalonGoldLight
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor,
            modifier = Modifier.padding(start = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = SalonSubtext.copy(alpha = 0.5f)) },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = SalonCream
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = SalonGold.copy(alpha = 0.45f),
                focusedContainerColor = Color(0xD91D1022),
                unfocusedContainerColor = Color(0xBF170D1B),
                focusedTextColor = SalonCream,
                unfocusedTextColor = SalonCream,
                cursorColor = accentColor
            )
        )
    }
}
