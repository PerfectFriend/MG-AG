package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.Bip39Helper
import com.example.data.CryptoIdentity
import com.example.data.PdfPassportGenerator
import com.example.ui.GameViewModel
import kotlin.math.cos
import kotlin.math.sin

private enum class OnboardingStep {
    PERMISSIONS,
    PROFILE_SETUP,
    CREATE_BIP39_SEED,
    VERIFY_BIP39_SEED,
    RESTORE_FROM_SEED
}

// Signature Palette: Gaudí Mosaic + Van Gogh Warm Gold + Malachite & Mahogany Lounge
private val VaultGoldLight = Color(0xFFFFE082)
private val VaultGold = Color(0xFFFFCA28)
private val VaultAmber = Color(0xFFFF8F00)
private val VaultCopper = Color(0xFFD84315)
private val VaultEmerald = Color(0xFF00E676)
private val VaultTurquoise = Color(0xFF00E5FF)
private val VaultRuby = Color(0xFFFF5252)
private val VaultCream = Color(0xFFFFF8E1)
private val VaultWarmSubtext = Color(0xFFE6D5B8)
private val PlaqueSurfaceTop = Color(0xEB2C1624)
private val PlaqueSurfaceMid = Color(0xF01C182B)
private val PlaqueSurfaceBot = Color(0xEB261710)

@Composable
fun OnboardingRegistrationScreen(
    viewModel: GameViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lang = viewModel.selectedLanguage

    var currentStep by remember {
        mutableStateOf(
            if (!viewModel.hasCompletedSystemPermissions) OnboardingStep.PERMISSIONS
            else OnboardingStep.PROFILE_SETUP
        )
    }

    // Player name always defaults to localized "Игрок" / "Player" / etc. and is never restored from previous sessions
    var isCustomNameEdited by remember { mutableStateOf(false) }
    var userNameInput by remember { mutableStateOf(Language.get("welcome_placeholder_player", lang)) }
    LaunchedEffect(lang) {
        if (!isCustomNameEdited) {
            userNameInput = Language.get("welcome_placeholder_player", lang)
        }
    }

    var pinCodeInput by remember { mutableStateOf("") }
    var pinCodeConfirmInput by remember { mutableStateOf("") }
    var panicPinInput by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf("") }

    // BIP-39 Generated Identity for Creation
    var generatedIdentity by remember { mutableStateOf<CryptoIdentity?>(null) }
    var isSeedSavedCheckbox by remember { mutableStateOf(false) }

    // BIP-39 Seed phrase input for Restoration
    var seedInputText by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf("") }

    var actionNotification by remember { mutableStateOf("") }

    LaunchedEffect(actionNotification) {
        if (actionNotification.isNotEmpty()) {
            Toast.makeText(context, actionNotification, Toast.LENGTH_SHORT).show()
            actionNotification = ""
        }
    }

    // Subtle animated glow & floating mosaic sparkles
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_atmosphere")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_phase"
    )
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0E16))
    ) {
        // 1. Custom Painted Artistic Wallpaper
        Image(
            painter = painterResource(id = R.drawable.img_onboarding_vault_bg_1790459241792),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Warm Artistic Vignette & Mosaic Glow Overlay (No dreary flat black!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99240E1C),
                            Color(0x66181226),
                            Color(0x991E1118),
                            Color(0xCC160B12)
                        )
                    )
                )
        )

        // 3. Van Gogh Starry Swirls / Golden Fireflies Canvas Layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val orbs = listOf(
                Triple(0.15f, 0.18f, VaultGold),
                Triple(0.85f, 0.12f, VaultTurquoise),
                Triple(0.22f, 0.78f, VaultAmber),
                Triple(0.82f, 0.72f, VaultEmerald),
                Triple(0.50f, 0.35f, VaultGoldLight),
                Triple(0.12f, 0.48f, VaultTurquoise)
            )
            orbs.forEachIndexed { idx, (bx, by, col) ->
                val dx = cos(shimmerPhase + idx * 1.1f) * 18f
                val dy = sin(shimmerPhase + idx * 1.4f) * 18f
                val center = Offset(w * bx + dx, h * by + dy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(col.copy(alpha = 0.22f * borderPulse), Color.Transparent),
                        center = center,
                        radius = 130f
                    ),
                    radius = 130f,
                    center = center
                )
                drawCircle(
                    color = col.copy(alpha = 0.65f * borderPulse),
                    radius = 3.5f,
                    center = center
                )
            }
        }

        // 4. Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ornate Top Header Plaque
            OrnateHeaderBar(
                lang = lang,
                currentStep = currentStep,
                borderPulse = borderPulse,
                onCycleLanguage = {
                    val langs = listOf("RU", "EN", "DE", "ES", "FR", "TR")
                    val nextIdx = (langs.indexOf(lang) + 1) % langs.size
                    viewModel.updateLanguage(langs[nextIdx])
                }
            )

            // Jeweled Step Progress Medallions
            OnboardingStepJewelRow(currentStep = currentStep, lang = lang)

            // Step Content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(320)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "OnboardingStepTransition"
            ) { step ->
                when (step) {
                    OnboardingStep.PERMISSIONS -> {
                        PermissionsStep(
                            lang = lang,
                            borderPulse = borderPulse,
                            onPermissionsGranted = {
                                viewModel.markPermissionsCompleted()
                                currentStep = OnboardingStep.PROFILE_SETUP
                            }
                        )
                    }
                    OnboardingStep.PROFILE_SETUP -> {
                        ProfileSetupStep(
                            lang = lang,
                            userName = userNameInput,
                            onUserNameChange = {
                                isCustomNameEdited = true
                                userNameInput = it
                                formError = ""
                            },
                            pinCode = pinCodeInput,
                            onPinCodeChange = { pinCodeInput = it; formError = "" },
                            pinCodeConfirm = pinCodeConfirmInput,
                            onPinCodeConfirmChange = { pinCodeConfirmInput = it; formError = "" },
                            panicPin = panicPinInput,
                            onPanicPinChange = { panicPinInput = it; formError = "" },
                            errorMessage = formError,
                            onCreateChosen = {
                                val finalName = userNameInput.trim().ifEmpty {
                                    Language.get("welcome_placeholder_player", lang)
                                }
                                userNameInput = finalName
                                if (pinCodeInput.length < 4) {
                                    formError = if (lang == "RU") "Мастер PIN-код должен содержать от 4 до 6 цифр" else "Master PIN code must be 4 to 6 digits"
                                    return@ProfileSetupStep
                                }
                                if (pinCodeInput != pinCodeConfirmInput) {
                                    formError = if (lang == "RU") "Мастер PIN-коды не совпадают" else "Master PIN codes do not match"
                                    return@ProfileSetupStep
                                }
                                if (panicPinInput.length < 4) {
                                    formError = if (lang == "RU") "ПИН-код паники должен содержать от 4 до 6 цифр" else "Panic PIN must be 4 to 6 digits"
                                    return@ProfileSetupStep
                                }
                                if (panicPinInput == pinCodeInput) {
                                    formError = if (lang == "RU") "ПИН-код паники не должен совпадать с Мастер PIN-кодом!" else "Panic PIN must not match Master PIN!"
                                    return@ProfileSetupStep
                                }
                                generatedIdentity = viewModel.createNewIdentity()
                                currentStep = OnboardingStep.CREATE_BIP39_SEED
                            },
                            onRestoreChosen = {
                                currentStep = OnboardingStep.RESTORE_FROM_SEED
                            }
                        )
                    }
                    OnboardingStep.CREATE_BIP39_SEED -> {
                        val identity = generatedIdentity ?: viewModel.createNewIdentity().also { generatedIdentity = it }
                        CreateBip39SeedStep(
                            lang = lang,
                            identity = identity,
                            isSavedChecked = isSeedSavedCheckbox,
                            onSavedCheckedChange = { isSeedSavedCheckbox = it },
                            onCopyClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("MadGammon BIP39 Seed", identity.seedPhrase)
                                clipboard.setPrimaryClip(clip)
                                actionNotification = if (lang == "RU") "📋 Сид-фраза из 12 слов скопирована в буфер!" else "📋 12-word seed phrase copied to clipboard!"
                            },
                            onSavePdfClick = {
                                val pdfFile = PdfPassportGenerator.generatePassportPdf(context, userNameInput, identity)
                                if (pdfFile != null && pdfFile.exists()) {
                                    actionNotification = if (lang == "RU") "📄 PDF Паспорт успешно сформирован!" else "📄 PDF Passport generated successfully!"
                                    PdfPassportGenerator.openOrSharePdf(context, pdfFile)
                                } else {
                                    actionNotification = if (lang == "RU") "Ошибка при сохранении PDF" else "Error generating PDF"
                                }
                            },
                            onProceedToVerifyClick = {
                                currentStep = OnboardingStep.VERIFY_BIP39_SEED
                            },
                            onBackClick = {
                                currentStep = OnboardingStep.PROFILE_SETUP
                            }
                        )
                    }
                    OnboardingStep.VERIFY_BIP39_SEED -> {
                        val identity = generatedIdentity ?: viewModel.createNewIdentity().also { generatedIdentity = it }
                        VerifyBip39SeedStep(
                            lang = lang,
                            identity = identity,
                            onCompleteClick = {
                                viewModel.completeRegistration(userNameInput, pinCodeInput, panicPinInput, identity)
                                onComplete()
                            },
                            onBackClick = {
                                currentStep = OnboardingStep.CREATE_BIP39_SEED
                            },
                            onFailedVerificationBack = {
                                isSeedSavedCheckbox = false
                                currentStep = OnboardingStep.CREATE_BIP39_SEED
                            }
                        )
                    }
                    OnboardingStep.RESTORE_FROM_SEED -> {
                        RestoreFromSeedStep(
                            lang = lang,
                            seedText = seedInputText,
                            onSeedTextChange = { seedInputText = it; restoreError = "" },
                            userName = userNameInput,
                            onUserNameChange = {
                                isCustomNameEdited = true
                                userNameInput = it
                                restoreError = ""
                            },
                            pinCode = pinCodeInput,
                            onPinCodeChange = { pinCodeInput = it; restoreError = "" },
                            pinCodeConfirm = pinCodeConfirmInput,
                            onPinCodeConfirmChange = { pinCodeConfirmInput = it; restoreError = "" },
                            panicPin = panicPinInput,
                            onPanicPinChange = { panicPinInput = it; restoreError = "" },
                            errorMessage = restoreError,
                            onPasteClipboard = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (item.isNotBlank()) {
                                    seedInputText = item.trim()
                                    restoreError = ""
                                }
                            },
                            onRestoreClick = {
                                val finalName = userNameInput.trim().ifEmpty {
                                    Language.get("welcome_placeholder_player", lang)
                                }
                                userNameInput = finalName
                                if (pinCodeInput.length < 4) {
                                    restoreError = if (lang == "RU") "Задайте новый ПИН-код (от 4 цифр)" else "Set a new PIN code (at least 4 digits)"
                                    return@RestoreFromSeedStep
                                }
                                if (pinCodeInput != pinCodeConfirmInput) {
                                    restoreError = if (lang == "RU") "ПИН-коды не совпадают" else "PIN codes do not match"
                                    return@RestoreFromSeedStep
                                }
                                if (panicPinInput.length < 4) {
                                    restoreError = if (lang == "RU") "ПИН-код паники должен содержать от 4 до 6 цифр" else "Panic PIN must be 4 to 6 digits"
                                    return@RestoreFromSeedStep
                                }
                                val result = viewModel.restoreProfileFromSeed(userNameInput, pinCodeInput, panicPinInput, seedInputText)
                                if (result.first) {
                                    onComplete()
                                } else {
                                    restoreError = result.second
                                }
                            },
                            onBackClick = {
                                currentStep = OnboardingStep.PROFILE_SETUP
                            }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// BESPOKE ARTISTIC UI COMPONENTS (Plaques, Medallions, 3D Gilded Buttons, Inputs)
// -------------------------------------------------------------------------------------------------

@Composable
private fun OrnateHeaderBar(
    lang: String,
    currentStep: OnboardingStep,
    borderPulse: Float,
    onCycleLanguage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xE6381828),
                        Color(0xE61F1835),
                        Color(0xE62E1B12)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        VaultGold.copy(alpha = borderPulse),
                        VaultTurquoise.copy(alpha = 0.7f),
                        VaultAmber.copy(alpha = borderPulse)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gilded Shield Medallion
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(VaultGoldLight, VaultAmber, Color(0xFF5D2906))
                            )
                        )
                        .border(1.2.dp, VaultCream, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎲", fontSize = 17.sp)
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = if (lang == "RU") "ШЕШ БЕШ" else "SHESH BESH",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                        color = VaultGoldLight,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            shadow = Shadow(color = Color(0xFFBF360C), blurRadius = 10f)
                        )
                    )
                    Text(
                        text = when (currentStep) {
                            OnboardingStep.PERMISSIONS -> if (lang == "RU") "✨ Шаг 1 • Доступ" else "✨ Step 1 • Access"
                            OnboardingStep.PROFILE_SETUP -> if (lang == "RU") "👑 Шаг 2 • Паспорт" else "👑 Step 2 • Passport"
                            OnboardingStep.CREATE_BIP39_SEED -> if (lang == "RU") "🗝️ Шаг 3 • Манускрипт" else "🗝️ Step 3 • Manuscript"
                            OnboardingStep.VERIFY_BIP39_SEED -> if (lang == "RU") "🛡️ Шаг 4 • Печать" else "🛡️ Step 4 • Seal"
                            OnboardingStep.RESTORE_FROM_SEED -> if (lang == "RU") "🔄 Восстановление" else "🔄 Restore"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultTurquoise,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Jeweled Language Switcher Pill (Guaranteed Single-Line Horizontal)
            val flagEmoji = when (lang) {
                "RU" -> "🇷🇺"
                "EN" -> "🇬🇧"
                "DE" -> "🇩🇪"
                "ES" -> "🇪🇸"
                "FR" -> "🇫🇷"
                "TR" -> "🇹🇷"
                else -> "🌐"
            }
            Box(
                modifier = Modifier
                    .shadow(3.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF4A2511), Color(0xFF28152C))
                        )
                    )
                    .border(
                        1.2.dp,
                        Brush.horizontalGradient(listOf(VaultGold, VaultTurquoise)),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onCycleLanguage() }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = flagEmoji,
                        fontSize = 13.sp,
                        softWrap = false
                    )
                    Text(
                        text = lang,
                        color = VaultCream,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepJewelRow(currentStep: OnboardingStep, lang: String) {
    val steps = listOf(
        Triple(OnboardingStep.PERMISSIONS, "🛡️", if (lang == "RU") "Доступ" else "Access"),
        Triple(OnboardingStep.PROFILE_SETUP, "👑", if (lang == "RU") "Профиль" else "Profile"),
        Triple(OnboardingStep.CREATE_BIP39_SEED, "🗝️", if (lang == "RU") "Ключи" else "Keys"),
        Triple(OnboardingStep.VERIFY_BIP39_SEED, "✨", if (lang == "RU") "Печать" else "Seal")
    )
    val activeIdx = when (currentStep) {
        OnboardingStep.PERMISSIONS -> 0
        OnboardingStep.PROFILE_SETUP -> 1
        OnboardingStep.RESTORE_FROM_SEED -> 1
        OnboardingStep.CREATE_BIP39_SEED -> 2
        OnboardingStep.VERIFY_BIP39_SEED -> 3
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { idx, (_, emoji, title) ->
            val isCompleted = idx < activeIdx
            val isCurrent = idx == activeIdx

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 36.dp else 30.dp)
                            .shadow(if (isCurrent) 8.dp else 2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent -> Brush.radialGradient(listOf(VaultGoldLight, VaultAmber, VaultCopper))
                                    isCompleted -> Brush.radialGradient(listOf(VaultEmerald, Color(0xFF00695C)))
                                    else -> Brush.radialGradient(listOf(Color(0xCC2A1A2E), Color(0xCC181020)))
                                }
                            )
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = when {
                                    isCurrent -> VaultCream
                                    isCompleted -> VaultEmerald
                                    else -> VaultGold.copy(alpha = 0.35f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCompleted) "✓" else emoji,
                            fontSize = if (isCurrent) 16.sp else 13.sp,
                            color = VaultCream,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = title,
                        fontSize = 9.5.sp,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isCurrent) VaultGoldLight else if (isCompleted) VaultEmerald else VaultWarmSubtext.copy(alpha = 0.7f)
                    )
                }

                if (idx < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isCompleted) {
                                    Brush.horizontalGradient(listOf(VaultEmerald, VaultGold))
                                } else {
                                    Brush.horizontalGradient(listOf(VaultGold.copy(alpha = 0.25f), VaultGold.copy(alpha = 0.12f)))
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun OrnateVaultPlaque(
    modifier: Modifier = Modifier,
    accentColor: Color = VaultGold,
    secondaryAccent: Color = VaultTurquoise,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PlaqueSurfaceTop,
                        PlaqueSurfaceMid,
                        PlaqueSurfaceBot
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.9f),
                        secondaryAccent.copy(alpha = 0.65f),
                        VaultAmber.copy(alpha = 0.85f),
                        accentColor.copy(alpha = 0.9f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        // Top decorative mosaic shimmer ribbon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            VaultAmber,
                            VaultGoldLight,
                            secondaryAccent,
                            VaultEmerald,
                            VaultGoldLight,
                            VaultAmber
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun OrnatePrimaryButton(
    text: String,
    iconEmoji: String? = null,
    enabled: Boolean = true,
    isEmeraldStyle: Boolean = false,
    onClick: () -> Unit
) {
    val bgBrush = if (!enabled) {
        Brush.horizontalGradient(listOf(Color(0xFF3E2D38), Color(0xFF2D2436)))
    } else if (isEmeraldStyle) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF00C853),
                Color(0xFF00E676),
                Color(0xFF1DE9B6)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF8F00),
                Color(0xFFFFCA28),
                Color(0xFFFFE082),
                Color(0xFFFFB300)
            )
        )
    }

    val borderBrush = if (!enabled) {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.15f)))
    } else {
        Brush.horizontalGradient(listOf(VaultCream, VaultGoldLight, Color.White))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(if (enabled) 8.dp else 0.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .border(1.5.dp, borderBrush, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconEmoji != null) {
                Text(text = iconEmoji, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp,
                color = if (enabled) Color(0xFF1F0D05) else VaultWarmSubtext.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OrnateSecondaryButton(
    text: String,
    iconEmoji: String? = null,
    accentColor: Color = VaultGoldLight,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .shadow(4.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF3B1F2B),
                        Color(0xFF251A38),
                        Color(0xFF382214)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(accentColor, VaultAmber.copy(alpha = 0.8f), accentColor)
                ),
                shape = RoundedCornerShape(15.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconEmoji != null) {
                Text(text = iconEmoji, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                color = accentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OrnateVaultInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    accentColor: Color = VaultGold,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.padding(start = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.dp, accentColor.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
            },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = VaultGold.copy(alpha = 0.38f),
                focusedContainerColor = Color(0xCC1A0E1E),
                unfocusedContainerColor = Color(0xB3160C1A),
                focusedTextColor = VaultCream,
                unfocusedTextColor = VaultCream,
                cursorColor = accentColor
            ),
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = VaultCream
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine
        )
    }
}

// -------------------------------------------------------------------------------------------------
// STEP 0: SYSTEM PERMISSIONS
// -------------------------------------------------------------------------------------------------
@Composable
private fun PermissionsStep(
    lang: String,
    borderPulse: Float,
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    var audioGranted by remember { mutableStateOf(hasPermission(Manifest.permission.RECORD_AUDIO)) }
    var cameraGranted by remember { mutableStateOf(hasPermission(Manifest.permission.CAMERA)) }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        audioGranted = results[Manifest.permission.RECORD_AUDIO] ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        cameraGranted = results[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifGranted = results[Manifest.permission.POST_NOTIFICATIONS] ?: hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    OrnateVaultPlaque(accentColor = VaultGold, secondaryAccent = VaultTurquoise) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(VaultAmber, VaultCopper)))
                    .border(1.dp, VaultGoldLight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🏛️", fontSize = 20.sp)
            }
            Column {
                Text(
                    text = if (lang == "RU") "ПРИВИЛЕГИИ И РАЗРЕШЕНИЯ САЛОНА" else "SALON PRIVILEGES & PERMISSIONS",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultGoldLight
                )
                Text(
                    text = if (lang == "RU") "Золотой стандарт приватности и живого общения" else "Golden standard of privacy & live communication",
                    fontSize = 11.sp,
                    color = VaultWarmSubtext
                )
            }
        }

        Text(
            text = if (lang == "RU")
                "Для игры по сети, живой голосовой рации за нардами и мгновенного чтения QR-ключей активируйте разрешения:"
            else
                "For P2P matches, live voice walkie-talkie over the board, and instant QR key scanning, enable permissions:",
            fontSize = 12.sp,
            color = VaultCream.copy(alpha = 0.92f),
            lineHeight = 17.sp
        )

        PermissionPlaqueRow(
            emoji = "🎙️",
            title = if (lang == "RU") "Золотой микрофон (Клубная рация)" else "Golden Mic (Salon Walkie-Talkie)",
            desc = if (lang == "RU") "Голосовые реплики за игровым столом с авто-удалением" else "Live voice banter across the board with auto-destruct",
            accentColor = VaultAmber,
            isGranted = audioGranted
        )

        PermissionPlaqueRow(
            emoji = "📷",
            title = if (lang == "RU") "Витражный сканер (Камера QR)" else "Stained-Glass Scanner (QR Camera)",
            desc = if (lang == "RU") "Мгновенное считывание инвайтов и клубных ключей" else "Instant scanning of peer invites & salon keys",
            accentColor = VaultTurquoise,
            isGranted = cameraGranted
        )

        PermissionPlaqueRow(
            emoji = "🔔",
            title = if (lang == "RU") "Гонг хода (Уведомления)" else "Turn Gong (Notifications)",
            desc = if (lang == "RU") "Сигналы о броске костей соперника и приглашениях" else "Alerts for opponent dice rolls & match invites",
            accentColor = VaultEmerald,
            isGranted = notifGranted
        )

        // Provably Fair Mental Dice Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF00E676).copy(alpha = 0.12f))
                .border(1.2.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🎲", fontSize = 22.sp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (lang == "RU") "ПРОТОКОЛ ЧЕСТНОЙ ИГРЫ (MENTAL DICE)" else "PROVABLY FAIR DICE PROTOCOL",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E676)
                    )
                    Text(
                        text = if (lang == "RU")
                            "Защита от подтасовки: все броски зариков генерируются через криптографический протокол Commit-Reveal (HMAC-SHA256). Ни один игрок или бот не может подделать результат броска костей."
                        else
                            "Anti-cheat protection: all dice rolls are cryptographically generated via Commit-Reveal (HMAC-SHA256). Neither player nor AI can manipulate roll results.",
                        fontSize = 10.5.sp,
                        color = VaultCream,
                        lineHeight = 14.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OrnatePrimaryButton(
            text = if (lang == "RU") "АКТИВИРОВАТЬ ВСЕ РАЗРЕШЕНИЯ" else "ACTIVATE ALL PERMISSIONS",
            iconEmoji = "✨",
            onClick = {
                val permissions = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissions.toTypedArray())
            }
        )

        OrnateSecondaryButton(
            text = if (lang == "RU") "ПЕРЕЙТИ В ПАСПОРТНЫЙ ЗАЛ ➔" else "ENTER PASSPORT HALL ➔",
            iconEmoji = "👑",
            accentColor = VaultGoldLight,
            onClick = onPermissionsGranted
        )
    }
}

@Composable
private fun PermissionPlaqueRow(
    emoji: String,
    title: String,
    desc: String,
    accentColor: Color,
    isGranted: Boolean
) {
    val activeBorder = if (isGranted) VaultEmerald else accentColor.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xD9261422),
                        Color(0xD91A162B)
                    )
                )
            )
            .border(1.2.dp, activeBorder, RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            (if (isGranted) VaultEmerald else accentColor).copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, if (isGranted) VaultEmerald else accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 21.sp)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = VaultCream)
            Text(desc, fontSize = 10.5.sp, color = VaultWarmSubtext, lineHeight = 14.sp)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isGranted) VaultEmerald.copy(alpha = 0.22f)
                    else VaultAmber.copy(alpha = 0.22f)
                )
                .border(
                    1.dp,
                    if (isGranted) VaultEmerald else VaultGold,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = if (isGranted) "ГОТОВО ✓" else if (langIsRu(title)) "НУЖНО" else "NEEDED",
                fontSize = 10.sp,
                color = if (isGranted) VaultEmerald else VaultGoldLight,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private fun langIsRu(sample: String): Boolean = sample.any { it in 'А'..'я' || it in 'а'..'я' }

// -------------------------------------------------------------------------------------------------
// STEP 1: PROFILE SETUP (Username, PIN, and Choice between Create vs Restore)
// -------------------------------------------------------------------------------------------------
@Composable
private fun ProfileSetupStep(
    lang: String,
    userName: String,
    onUserNameChange: (String) -> Unit,
    pinCode: String,
    onPinCodeChange: (String) -> Unit,
    pinCodeConfirm: String,
    onPinCodeConfirmChange: (String) -> Unit,
    panicPin: String,
    onPanicPinChange: (String) -> Unit,
    errorMessage: String,
    onCreateChosen: () -> Unit,
    onRestoreChosen: () -> Unit
) {
    OrnateVaultPlaque(accentColor = VaultGold, secondaryAccent = VaultEmerald) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(VaultGold, VaultCopper)))
                    .border(1.dp, VaultCream, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("👑", fontSize = 20.sp)
            }
            Column {
                Text(
                    text = if (lang == "RU") "РЕГИСТРАЦИЯ И ЗАЩИТА ПАСПОРТА" else "PASSPORT REGISTRATION & VAULT",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultGoldLight
                )
                Text(
                    text = if (lang == "RU") "Двухконтурная защита: Игра + Секретный клуб" else "Dual-contour protection: Game + Secret Salon",
                    fontSize = 11.sp,
                    color = VaultTurquoise
                )
            }
        }

        // Mosaic Info Plaque about Dual-Mode Access
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xCC2E1C10), Color(0xCC182628))
                    )
                )
                .border(1.2.dp, VaultGold.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (lang == "RU") "⚜️ ТАЙНЫЙ ЭТИКЕТ САЛОНА:" else "⚜️ SECRET SALON ETIQUETTE:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultGoldLight
                )
                Text(
                    text = if (lang == "RU")
                        "• Заданное имя станет секретным именем пользователя мессенджера.\n• Чтобы открыть мессенджер, укажите это имя как «Имя игрока» перед началом игры.\n• Если указано любое другое имя — приложение остаётся в Stealth-режиме, запускается игра, сетевой стек НЕ стартует.\n• При вводе секретного имени открывается запрос PIN-кода, стартует сетевой стек, и весь трафик заворачивается в защищенный бридж."
                    else
                        "• The name below is your Secret Messenger Username.\n• To switch to Messenger, enter this exact name as 'Player Name' before starting a game.\n• If any other name is entered — the app remains in Stealth Mode, starts the game, and the network stack DOES NOT start.\n• Entering the secret username prompts for your PIN, starts the network stack, and routes traffic through the bridge.",
                    fontSize = 11.sp,
                    color = VaultCream.copy(alpha = 0.92f),
                    lineHeight = 15.5.sp
                )
            }
        }

        OrnateVaultInputField(
            value = userName,
            onValueChange = onUserNameChange,
            label = if (lang == "RU") "Секретное имя пользователя (триггер мессенджера)" else "Secret Messenger Username (Mode Trigger)",
            icon = Icons.Default.Person,
            accentColor = VaultGoldLight
        )

        OrnateVaultInputField(
            value = pinCode,
            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onPinCodeChange(it) },
            label = if (lang == "RU") "Мастер PIN-код салона (4–6 цифр)" else "Salon Master PIN Code (4–6 digits)",
            icon = Icons.Default.Lock,
            accentColor = VaultTurquoise,
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
        )

        OrnateVaultInputField(
            value = pinCodeConfirm,
            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onPinCodeConfirmChange(it) },
            label = if (lang == "RU") "Подтвердите Мастер PIN-код" else "Confirm Master PIN Code",
            icon = Icons.Default.CheckCircle,
            accentColor = VaultEmerald,
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
        )

        OrnateVaultInputField(
            value = panicPin,
            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onPanicPinChange(it) },
            label = if (lang == "RU") "Тревожный PIN-код паники (4–6 цифр)" else "Panic Duress PIN (4–6 digits)",
            icon = Icons.Default.Warning,
            accentColor = VaultRuby,
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
        )

        Text(
            text = if (lang == "RU")
                "🚨 Ввод тревожного PIN-кода мгновенно открывает обычные нарды и запечатывает секретный чат на 48 часов (снятие печати — только по 12 словам)."
            else
                "🚨 Entering the Panic PIN launches normal backgammon and seals the secret chat for 48 hours (unsealing requires your 12-word seed).",
            fontSize = 10.5.sp,
            color = Color(0xFFFFAB91),
            lineHeight = 14.5.sp
        )

        if (errorMessage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VaultRuby.copy(alpha = 0.2f))
                    .border(1.dp, VaultRuby, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "⚠️ $errorMessage",
                    color = Color(0xFFFFCDD2),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (lang == "RU") "⚜️ ВЫБЕРИТЕ ПУТЬ ИНИЦИАЦИИ КЛЮЧЕЙ:" else "⚜️ CHOOSE KEY INITIATION PATH:",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Black,
            color = VaultGoldLight
        )

        // Choice 1: Create New Royal Profile
        OrnateChoiceCard(
            emoji = "🗝️",
            title = if (lang == "RU") "СОЗДАТЬ НОВЫЙ ПАСПОРТ (BIP-39)" else "FORGE NEW PASSPORT (BIP-39)",
            subtitle = if (lang == "RU") "Генерация 12 заветных слов, копирование в 1 касание и выпуск PDF-паспорта" else "Generate 12 sacred words, 1-tap copy & official PDF passport",
            accentColor = VaultGold,
            bgGradient = listOf(Color(0xE6422210), Color(0xE62A1428)),
            onClick = onCreateChosen
        )

        // Choice 2: Restore Existing Profile
        OrnateChoiceCard(
            emoji = "📜",
            title = if (lang == "RU") "ВОССТАНОВИТЬ ПО МАНУСКРИПТУ (12 СЛОВ)" else "RESTORE FROM MANUSCRIPT (12 WORDS)",
            subtitle = if (lang == "RU") "Ввод сохраненной мнемоники из 12 слов и воссоздание ключей" else "Enter your existing 12-word seed phrase & derive vault keys",
            accentColor = VaultTurquoise,
            bgGradient = listOf(Color(0xE6142834), Color(0xE6221632)),
            onClick = onRestoreChosen
        )
    }
}

@Composable
private fun OrnateChoiceCard(
    emoji: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    bgGradient: List<Color>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(bgGradient))
            .border(
                width = 1.6.dp,
                brush = Brush.horizontalGradient(listOf(accentColor, VaultGoldLight, accentColor)),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.4f), Color(0xFF1A0E16))
                    )
                )
                .border(1.5.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = VaultCream
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = VaultWarmSubtext,
                lineHeight = 14.sp
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text("➔", color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// STEP 2A: CREATE BIP-39 SEED
// -------------------------------------------------------------------------------------------------
@Composable
private fun CreateBip39SeedStep(
    lang: String,
    identity: CryptoIdentity,
    isSavedChecked: Boolean,
    onSavedCheckedChange: (Boolean) -> Unit,
    onCopyClick: () -> Unit,
    onSavePdfClick: () -> Unit,
    onProceedToVerifyClick: () -> Unit,
    onBackClick: () -> Unit
) {
    OrnateVaultPlaque(accentColor = VaultGold, secondaryAccent = VaultAmber) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🗝️", fontSize = 17.sp)
                Text(
                    text = if (lang == "RU") "ЗОЛОТОЙ МАНУСКРИПТ (12 СЛОВ)" else "GOLDEN MANUSCRIPT (12 WORDS)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultGoldLight,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, VaultGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { onBackClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (lang == "RU") "← Назад" else "← Back", color = VaultGoldLight, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Compact Responsibility Warning Plaque
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xD94A121B), Color(0xD9331024))
                    )
                )
                .border(1.2.dp, VaultRuby, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("⚠️", fontSize = 14.sp)
                Text(
                    text = if (lang == "RU")
                        "Единственный ключ к салону. Сохраните PDF или перепишите слова!"
                    else
                        "Sole key to your salon. Save PDF passport or write down words!",
                    fontSize = 10.5.sp,
                    color = VaultCream,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 13.5.sp
                )
            }
        }

        // 12 Words Inlaid Mosaic Grid (3 columns x 4 rows)
        Column(verticalArrangement = Arrangement.spacedBy(4.5.dp)) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.5.dp)
                ) {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        val word = identity.words.getOrElse(index) { "" }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(2.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF361F2D), Color(0xFF21162B))
                                    )
                                )
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(listOf(VaultGold.copy(alpha = 0.6f), VaultTurquoise.copy(alpha = 0.35f))),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(vertical = 5.5.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = VaultGold
                                )
                                Text(
                                    text = word,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = VaultCream,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fingerprint Seal Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xB3140B18))
                .border(1.dp, VaultGold.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lang == "RU") "Оттиск печати:" else "Key Seal:",
                    fontSize = 10.sp,
                    color = VaultWarmSubtext
                )
                Text(
                    text = identity.identityFingerprint.take(22),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = VaultTurquoise
                )
            }
        }

        // 1-Click Action Buttons: Copy and Save PDF
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrnateSecondaryButton(
                text = if (lang == "RU") "СКОПИРОВАТЬ" else "COPY WORDS",
                iconEmoji = "📋",
                accentColor = VaultGoldLight,
                modifier = Modifier.weight(1f),
                onClick = onCopyClick
            )
            OrnateSecondaryButton(
                text = if (lang == "RU") "PDF ПАСПОРТ" else "PDF PASSPORT",
                iconEmoji = "📄",
                accentColor = VaultTurquoise,
                modifier = Modifier.weight(1f),
                onClick = onSavePdfClick
            )
        }

        // Ornate Confirmation Plaque
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSavedChecked) VaultEmerald.copy(alpha = 0.16f)
                    else Color.White.copy(alpha = 0.05f)
                )
                .border(
                    1.dp,
                    if (isSavedChecked) VaultEmerald else VaultGold.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp)
                )
                .clickable { onSavedCheckedChange(!isSavedChecked) }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Checkbox(
                checked = isSavedChecked,
                onCheckedChange = onSavedCheckedChange,
                modifier = Modifier.size(22.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = VaultEmerald,
                    uncheckedColor = VaultGoldLight,
                    checkmarkColor = Color.Black
                )
            )
            Text(
                text = if (lang == "RU")
                    "Я сохранил(а) все 12 слов манускрипта и осознаю личную ответственность"
                else
                    "I have safely preserved all 12 words and accept full responsibility",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = VaultCream,
                lineHeight = 13.5.sp
            )
        }

        OrnatePrimaryButton(
            text = if (lang == "RU") "СКРЕПИТЬ ПЕЧАТЬЮ (ПРОВЕРКА) ➔" else "SEAL & VERIFY WORDS ➔",
            iconEmoji = "🛡️",
            enabled = isSavedChecked,
            onClick = onProceedToVerifyClick
        )
    }
}

// -------------------------------------------------------------------------------------------------
// STEP 2B: VERIFY BIP-39 SEED
// -------------------------------------------------------------------------------------------------
@Composable
private fun VerifyBip39SeedStep(
    lang: String,
    identity: CryptoIdentity,
    onCompleteClick: () -> Unit,
    onBackClick: () -> Unit,
    onFailedVerificationBack: () -> Unit
) {
    val words = identity.words
    val testIndices = remember { listOf(2, 6, 10) }
    var selectedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var verificationFailed by remember { mutableStateOf(false) }
    var failedWordNum by remember { mutableStateOf(0) }

    val optionsForIndices = remember(identity) {
        testIndices.associateWith { testIdx ->
            val correctWord = words.getOrElse(testIdx) { "" }
            val otherWords = words.filterIndexed { i, _ -> i != testIdx }.shuffled().take(3)
            (otherWords + correctWord).shuffled()
        }
    }

    val isAllSelected = testIndices.all { selectedAnswers.containsKey(it) }
    val isAllCorrect = !verificationFailed && isAllSelected && testIndices.all { idx -> selectedAnswers[idx] == words.getOrNull(idx) }

    OrnateVaultPlaque(accentColor = VaultEmerald, secondaryAccent = VaultGold) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🛡️", fontSize = 17.sp)
                Text(
                    text = if (lang == "RU") "ПЕЧАТЬ ПОДЛИННОСТИ" else "SEAL OF AUTHENTICITY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultGoldLight,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, VaultGold.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .clickable { onBackClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (lang == "RU") "📜 Сверить слова" else "📜 Check words",
                    color = VaultGoldLight,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = if (lang == "RU")
                "Укажите 3 контрольных слова из вашего манускрипта по их порядковым номерам:"
            else
                "Select the 3 check words from your manuscript according to their numbers:",
            fontSize = 12.sp,
            color = VaultCream,
            lineHeight = 16.sp
        )

        testIndices.forEach { idx ->
            val wordNum = idx + 1
            val options = optionsForIndices[idx] ?: emptyList()
            val selected = selectedAnswers[idx]
            val isAnswerCorrect = selected != null && selected == words.getOrNull(idx)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC1C1224))
                    .border(
                        1.4.dp,
                        when {
                            selected == null -> VaultGold.copy(alpha = 0.35f)
                            isAnswerCorrect -> VaultEmerald
                            else -> VaultRuby
                        },
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") "⚜️ Слово №$wordNum:" else "⚜️ Word #$wordNum:",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VaultGoldLight
                    )
                    if (selected != null) {
                        Text(
                            text = if (isAnswerCorrect) (if (lang == "RU") "✓ Подлинно" else "✓ Verified")
                            else (if (lang == "RU") "✗ Ошибка" else "✗ Mismatch"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isAnswerCorrect) VaultEmerald else VaultRuby
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { optionWord ->
                        val isChosen = selected == optionWord
                        val isCorrect = optionWord == words.getOrNull(idx)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(if (isChosen) 6.dp else 2.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isChosen && isCorrect -> Brush.verticalGradient(listOf(Color(0xFF00C853), Color(0xFF00695C)))
                                        isChosen && !isCorrect -> Brush.verticalGradient(listOf(Color(0xFFD50000), Color(0xFF6A0000)))
                                        else -> Brush.verticalGradient(listOf(Color(0xFF382032), Color(0xFF241628)))
                                    }
                                )
                                .border(
                                    1.2.dp,
                                    when {
                                        isChosen && isCorrect -> VaultCream
                                        isChosen && !isCorrect -> VaultRuby
                                        else -> VaultGold.copy(alpha = 0.4f)
                                    },
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(enabled = !verificationFailed) {
                                    selectedAnswers = selectedAnswers + (idx to optionWord)
                                    val isMatch = optionWord == words.getOrNull(idx)
                                    if (!isMatch) {
                                        verificationFailed = true
                                        failedWordNum = idx + 1
                                    }
                                }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optionWord,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VaultCream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (verificationFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xD95A0B16), Color(0xD92E0812))
                        )
                    )
                    .border(1.5.dp, VaultRuby, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("❌", fontSize = 22.sp)
                        Text(
                            text = if (lang == "RU") "ОШИБКА ВЫБОРА СЛОВА" else "WORD VERIFICATION ERROR",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = VaultCream
                        )
                    }
                    Text(
                        text = if (lang == "RU")
                            "Выбрано неверное слово для позиции №$failedWordNum. Подбор слов простым перебором запрещён правилами безопасности. Пожалуйста, вернитесь на шаг назад, сохраните свою сид-фразу и повторно отметьте подтверждение сохранения."
                        else
                            "Incorrect word chosen for position #$failedWordNum. Brute-force guessing is prohibited for security. Please return to the previous screen, verify and safely store your 12-word seed phrase, and re-check the confirmation box.",
                        fontSize = 11.sp,
                        color = VaultCream.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OrnatePrimaryButton(
                        text = if (lang == "RU") "← ВЕРНУТЬСЯ К СИД-ФРАЗЕ" else "← RETURN TO SEED PHRASE",
                        iconEmoji = "🗝️",
                        enabled = true,
                        isEmeraldStyle = false,
                        onClick = onFailedVerificationBack
                    )
                }
            }
        } else if (isAllCorrect) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VaultEmerald.copy(alpha = 0.18f))
                    .border(1.2.dp, VaultEmerald, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("👑", fontSize = 18.sp)
                    Text(
                        text = if (lang == "RU")
                            "Печать подлинности подтверждена! Двери клуба открыты."
                        else
                            "Seal of authenticity confirmed! The salon doors are open.",
                        fontSize = 11.5.sp,
                        color = VaultCream,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (!verificationFailed) {
            OrnatePrimaryButton(
                text = if (lang == "RU") "ВОЙТИ В ИГРОВОЙ САЛОН 🎲" else "ENTER THE GAMING SALON 🎲",
                iconEmoji = "👑",
                enabled = isAllCorrect,
                isEmeraldStyle = true,
                onClick = onCompleteClick
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// STEP 2C: RESTORE FROM SEED PHRASE
// -------------------------------------------------------------------------------------------------
@Composable
private fun RestoreFromSeedStep(
    lang: String,
    seedText: String,
    onSeedTextChange: (String) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    pinCode: String,
    onPinCodeChange: (String) -> Unit,
    pinCodeConfirm: String,
    onPinCodeConfirmChange: (String) -> Unit,
    panicPin: String,
    onPanicPinChange: (String) -> Unit,
    errorMessage: String,
    onPasteClipboard: () -> Unit,
    onRestoreClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val wordsList = remember(seedText) {
        seedText.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    val isValidMnemonic = remember(seedText) {
        if (wordsList.size == 12) {
            Bip39Helper.validateMnemonic(context, seedText)
        } else false
    }

    OrnateVaultPlaque(accentColor = VaultTurquoise, secondaryAccent = VaultGold) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📜", fontSize = 20.sp)
                Text(
                    text = if (lang == "RU") "ВОССТАНОВЛЕНИЕ ПАСПОРТА" else "PASSPORT RESTORATION",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultGoldLight
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, VaultGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { onBackClick() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(if (lang == "RU") "← Назад" else "← Back", color = VaultGoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = if (lang == "RU")
                "Введите 12 слов вашего BIP-39 манускрипта через пробел:"
            else
                "Enter the 12 words of your BIP-39 manuscript separated by spaces:",
            fontSize = 11.5.sp,
            color = VaultWarmSubtext,
            lineHeight = 15.sp
        )

        OutlinedTextField(
            value = seedText,
            onValueChange = onSeedTextChange,
            label = { Text(if (lang == "RU") "12 слов BIP-39 через пробел" else "12 BIP-39 words", fontSize = 11.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isValidMnemonic) VaultEmerald else VaultTurquoise,
                unfocusedBorderColor = VaultGold.copy(alpha = 0.4f),
                focusedContainerColor = Color(0xCC1A0E1E),
                unfocusedContainerColor = Color(0xB3160C1A),
                focusedTextColor = VaultCream,
                unfocusedTextColor = VaultCream
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            trailingIcon = {
                IconButton(onClick = onPasteClipboard) {
                    Text("📋", fontSize = 18.sp)
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${if (lang == "RU") "Слов" else "Words"}: ${wordsList.size} / 12",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (wordsList.size == 12) VaultEmerald else VaultGoldLight
            )

            if (isValidMnemonic) {
                Text(
                    text = if (lang == "RU") "✓ Подлинный манускрипт BIP-39" else "✓ Valid BIP-39 manuscript",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultEmerald
                )
            } else if (wordsList.size == 12) {
                Text(
                    text = if (lang == "RU") "⚠️ Неверная контрольная сумма" else "⚠️ Checksum mismatch",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultRuby
                )
            }
        }

        OrnateVaultInputField(
            value = userName,
            onValueChange = onUserNameChange,
            label = if (lang == "RU") "Имя игрока / Секретный логин" else "Player Name / Secret Handle",
            icon = Icons.Default.Person,
            accentColor = VaultGoldLight
        )

        OrnateVaultInputField(
            value = pinCode,
            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onPinCodeChange(it) },
            label = if (lang == "RU") "Новый Мастер PIN-код (4–6 цифр)" else "New Master PIN code (4–6 digits)",
            icon = Icons.Default.Lock,
            accentColor = VaultTurquoise,
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
        )

        OrnateVaultInputField(
            value = pinCodeConfirm,
            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onPinCodeConfirmChange(it) },
            label = if (lang == "RU") "Повторите новый ПИН-код" else "Confirm new PIN code",
            icon = Icons.Default.CheckCircle,
            accentColor = VaultEmerald,
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
        )

        OrnateVaultInputField(
            value = panicPin,
            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onPanicPinChange(it) },
            label = if (lang == "RU") "Тревожный ПИН-код паники (4–6 цифр)" else "Panic Duress PIN (4–6 digits)",
            icon = Icons.Default.Warning,
            accentColor = VaultRuby,
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
        )

        if (errorMessage.isNotEmpty()) {
            Text(
                text = "⚠️ $errorMessage",
                color = VaultRuby,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        OrnatePrimaryButton(
            text = if (lang == "RU") "ВОССТАНОВИТЬ И ВОЙТИ В САЛОН" else "RESTORE & ENTER SALON",
            iconEmoji = "🔓",
            enabled = isValidMnemonic,
            isEmeraldStyle = true,
            onClick = onRestoreClick
        )
    }
}
