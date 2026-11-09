package com.example.ui.components

import android.view.SoundEffectConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sealed interface for Keys to communicate with chat and input fields
sealed interface MatrixKey {
    data class CharKey(val text: String) : MatrixKey
    object Backspace : MatrixKey
    object Space : MatrixKey
    object Shift : MatrixKey
    object ToggleLang : MatrixKey
    object ToggleMode : MatrixKey // Toggle between ABC and Symbols (123 / ?%#)
    object Enter : MatrixKey
    object Hide : MatrixKey
}

private enum class SymbolsPage {
    NONE, // Regular alphabet
    PAGE_1, // 1 2 3 ... @ # $ ...
    PAGE_2  // ~ ` | ... < > € ...
}

/**
 * Standard Android Gboard 1-to-1 layout keyboard styled in Matrix / Cyberpunk aesthetics:
 * - Exact key placement, proportions, and rows identical to Google Gboard (Russian ЙЦУКЕН / English QWERTY)
 * - Row 1: Numbers/letters
 * - Row 2: Letters centered with standard offset
 * - Row 3: Shift on left, letters in middle, Backspace on right
 * - Row 4: ?123, Comma, Language switch, Spacebar, Period, Enter/Send
 * - Pure static rendering: ZERO animations, zero delays, immediate snappy touch response
 * - Monospace Matrix typography with neon-green `#00FF41` on stealth dark `#090D10`
 */
@Composable
fun MatrixStyleKeyboard(
    onKey: (MatrixKey) -> Unit,
    lang: String = "RU",
    modifier: Modifier = Modifier
) {
    var isRussian by remember { mutableStateOf(lang.equals("RU", ignoreCase = true)) }
    var isShiftActive by remember { mutableStateOf(false) }
    var isCapsLock by remember { mutableStateOf(false) }
    var symbolsPage by remember { mutableStateOf(SymbolsPage.NONE) }
    var lastShiftTapTime by remember { mutableStateOf(0L) }

    val localView = LocalView.current
    val playTypeSound = {
        try {
            localView.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (_: Exception) {}
    }

    // --- Standard Google Gboard Key Layouts ---
    // Russian ЙЦУКЕН (Standard Android layout: 12 - 11 - 9+Shift+BS)
    val ruRow1 = listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ")
    val ruRow2 = listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э")
    val ruRow3 = listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")

    // English QWERTY (Standard Android layout: 10 - 9 - 7+Shift+BS)
    val enRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val enRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val enRow3 = listOf("z", "x", "c", "v", "b", "n", "m")

    // Symbols Page 1 (?123)
    val sym1Row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val sym1Row2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
    val sym1Row3 = listOf("*", "\"", "'", ":", ";", "!", "?")

    // Symbols Page 2 (=/<)
    val sym2Row1 = listOf("~", "`", "|", "^", "°", "=", "{", "}", "[", "]")
    val sym2Row2 = listOf("\\", "<", ">", "€", "£", "¥", "§", "_", "©", "®")
    val sym2Row3 = listOf("«", "»", "“", "”", "„", "¿", "¡")

    val isShiftEffective = isShiftActive || isCapsLock

    val handleCharInput: (String) -> Unit = { char ->
        playTypeSound()
        val outChar = if (isShiftEffective) char.uppercase() else char.lowercase()
        onKey(MatrixKey.CharKey(outChar))
        if (isShiftActive && !isCapsLock) {
            isShiftActive = false
        }
    }

    val handleShiftClick: () -> Unit = {
        playTypeSound()
        val now = System.currentTimeMillis()
        if (now - lastShiftTapTime < 350L) {
            // Double-tap activates Caps Lock
            isCapsLock = !isCapsLock
            isShiftActive = false
        } else {
            if (isCapsLock) {
                isCapsLock = false
                isShiftActive = false
            } else {
                isShiftActive = !isShiftActive
            }
        }
        lastShiftTapTime = now
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF090D10))
            .border(
                border = BorderStroke(1.dp, Color(0xFF00FF41).copy(alpha = 0.25f)),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // --- Top Status & Quick Dismiss Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (isCapsLock) Color(0xFF00FF41) else if (isShiftActive) Color(0xFF00E5FF) else Color(0xFF00FF41).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(50)
                        )
                )
                Text(
                    text = when {
                        isCapsLock -> "CAPS LOCK"
                        isShiftActive -> "SHIFT"
                        symbolsPage != SymbolsPage.NONE -> "SYMBOLS"
                        isRussian -> "РУССКИЙ"
                        else -> "ENGLISH"
                    },
                    color = Color(0xFF00FF41).copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Keyboard minimize button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        playTypeSound()
                        onKey(MatrixKey.Hide)
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "▼ СКРЫТЬ",
                    color = Color(0xFF00FF41).copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- Keyboard Rows according to Mode ---
        when (symbolsPage) {
            SymbolsPage.NONE -> {
                // ALPHABET MODE (RU or EN)
                val row1 = if (isRussian) ruRow1 else enRow1
                val row2 = if (isRussian) ruRow2 else enRow2
                val row3 = if (isRussian) ruRow3 else enRow3

                // Row 1 (Full row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row1.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = isShiftEffective,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }
                }

                // Row 2 (Indented to match standard Gboard stagger)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Spacer(modifier = Modifier.weight(0.5f))
                    row2.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = isShiftEffective,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }
                    Spacer(modifier = Modifier.weight(0.5f))
                }

                // Row 3: Shift (left), Letters (middle), Backspace (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sideWeight = if (isRussian) 1.5f else 1.4f

                    // SHIFT KEY
                    FunctionalKey(
                        text = if (isCapsLock) "⇪" else "⇧",
                        modifier = Modifier.weight(sideWeight),
                        isEmphasized = isShiftEffective,
                        onClick = handleShiftClick
                    )

                    // Letters
                    row3.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = isShiftEffective,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }

                    // BACKSPACE KEY
                    FunctionalKey(
                        text = "⌫",
                        modifier = Modifier.weight(sideWeight),
                        onClick = {
                            playTypeSound()
                            onKey(MatrixKey.Backspace)
                        }
                    )
                }
            }

            SymbolsPage.PAGE_1 -> {
                // SYMBOLS PAGE 1: 123...
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    sym1Row1.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = false,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    sym1Row2.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = false,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch to Symbols Page 2
                    FunctionalKey(
                        text = "=\\<",
                        modifier = Modifier.weight(1.5f),
                        onClick = {
                            playTypeSound()
                            symbolsPage = SymbolsPage.PAGE_2
                        }
                    )

                    sym1Row3.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = false,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }

                    FunctionalKey(
                        text = "⌫",
                        modifier = Modifier.weight(1.5f),
                        onClick = {
                            playTypeSound()
                            onKey(MatrixKey.Backspace)
                        }
                    )
                }
            }

            SymbolsPage.PAGE_2 -> {
                // SYMBOLS PAGE 2: ~`|...
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    sym2Row1.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = false,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    sym2Row2.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = false,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch back to Symbols Page 1
                    FunctionalKey(
                        text = "?123",
                        modifier = Modifier.weight(1.5f),
                        onClick = {
                            playTypeSound()
                            symbolsPage = SymbolsPage.PAGE_1
                        }
                    )

                    sym2Row3.forEach { char ->
                        LetterKey(
                            char = char,
                            isUpper = false,
                            modifier = Modifier.weight(1f),
                            onClick = { handleCharInput(char) }
                        )
                    }

                    FunctionalKey(
                        text = "⌫",
                        modifier = Modifier.weight(1.5f),
                        onClick = {
                            playTypeSound()
                            onKey(MatrixKey.Backspace)
                        }
                    )
                }
            }
        }

        // --- ROW 4 (Bottom Bar 1-to-1 with Gboard) ---
        // Layout: [ ?123 / ABC ] [ , ] [ 🌐 RU/EN ] [ SPACEBAR ] [ . ] [ ↵ ENTER ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode toggle button (?123 / ABC)
            FunctionalKey(
                text = if (symbolsPage != SymbolsPage.NONE) "ABC" else "?123",
                modifier = Modifier.weight(1.5f),
                onClick = {
                    playTypeSound()
                    symbolsPage = if (symbolsPage != SymbolsPage.NONE) SymbolsPage.NONE else SymbolsPage.PAGE_1
                }
            )

            // Comma button
            LetterKey(
                char = ",",
                isUpper = false,
                modifier = Modifier.weight(1.0f),
                onClick = { handleCharInput(",") }
            )

            // Language switch button (🌐 / RU / EN)
            FunctionalKey(
                text = if (isRussian) "RU" else "EN",
                modifier = Modifier.weight(1.1f),
                isEmphasized = true,
                onClick = {
                    playTypeSound()
                    isRussian = !isRussian
                }
            )

            // Spacebar (wide, displaying active language in subtle matrix text)
            SpacebarKey(
                label = when {
                    isRussian -> "Русский"
                    else -> "English"
                },
                modifier = Modifier.weight(if (isRussian) 4.6f else 3.8f),
                onClick = {
                    playTypeSound()
                    onKey(MatrixKey.Space)
                }
            )

            // Period button
            LetterKey(
                char = ".",
                isUpper = false,
                modifier = Modifier.weight(1.0f),
                onClick = { handleCharInput(".") }
            )

            // Enter / Action key (Green action button)
            EnterActionKey(
                modifier = Modifier.weight(1.5f),
                onClick = {
                    playTypeSound()
                    onKey(MatrixKey.Enter)
                }
            )
        }
    }
}

/**
 * Standard character tile matching Gboard dimensions (height 44dp, rounded corners, crisp neon monospace text)
 */
@Composable
private fun LetterKey(
    char: String,
    isUpper: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val displayChar = if (isUpper) char.uppercase() else char.lowercase()

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141C22))
            .border(
                width = 1.dp,
                color = Color(0xFF00FF41).copy(alpha = 0.22f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF00FF41)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayChar,
            color = Color(0xFF00FF41),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (displayChar.length > 1) 13.sp else 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Functional key (Shift, Backspace, ?123, Language switch) with subtle slate background and distinct status
 */
@Composable
private fun FunctionalKey(
    text: String,
    modifier: Modifier = Modifier,
    isEmphasized: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isEmphasized) Color(0xFF00FF41).copy(alpha = 0.22f) else Color(0xFF0F151B)
    val borderColor = if (isEmphasized) Color(0xFF00FF41) else Color(0xFF00FF41).copy(alpha = 0.3f)
    val textColor = if (isEmphasized) Color(0xFFE0FFE5) else Color(0xFF00FF41)

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF00FF41)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (text.length > 2) 12.sp else 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Spacebar key with centered language label and standard Gboard proportions
 */
@Composable
private fun SpacebarKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141C22))
            .border(
                width = 1.dp,
                color = Color(0xFF00FF41).copy(alpha = 0.22f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF00FF41)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFF00FF41).copy(alpha = 0.45f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Enter / Send key styled as the primary action key (similar to Gboard's blue/accent enter button)
 */
@Composable
private fun EnterActionKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF00FF41))
            .border(
                width = 1.dp,
                color = Color(0xFF00FF41),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.Black),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "↵",
            color = Color(0xFF090D10),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Backward-compatible matrix background for MatrixNoGameScreen.
 * Note: MatrixStyleKeyboard does NOT use this and has zero animations.
 */
@Composable
fun MatrixFallingRainBg(
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cols = 16
        val spacing = width / cols
        for (i in 0 until cols) {
            val x = spacing * i + spacing / 2
            drawLine(
                color = Color(0xFF00FF41).copy(alpha = 0.08f),
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, height),
                strokeWidth = 1f
            )
        }
    }
}
