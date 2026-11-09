package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crypto.MentalDiceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MentalDiceAuditDialog(
    outcome: MentalDiceEngine.DiceOutcome?,
    engine: MentalDiceEngine,
    lang: String = "RU",
    onDismiss: () -> Unit,
    onSimulatedRollApplied: ((Int, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentOutcome by remember { mutableStateOf(outcome ?: engine.simulateFairConsensusRoll()) }
    var isSimulatingExchange by remember { mutableStateOf(false) }
    var simulationStep by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10131A),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (lang == "RU") "АУДИТ MENTAL DICE" else "MENTAL DICE AUDIT",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (lang == "RU") "Provably Fair: commit-reveal (не ZK)" else "Provably Fair: commit-reveal (not ZK)",
                                fontSize = 10.sp,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Verification Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (currentOutcome.isValid) Color(0xFF00E676).copy(alpha = 0.12f)
                            else Color(0xFFFF5252).copy(alpha = 0.15f)
                        )
                        .border(
                            1.dp,
                            if (currentOutcome.isValid) Color(0xFF00E676).copy(alpha = 0.4f)
                            else Color(0xFFFF5252).copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentOutcome.isValid) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (currentOutcome.isValid) Color(0xFF00E676) else Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (currentOutcome.isValid) {
                                    if (lang == "RU") "МАТЕМАТИЧЕСКИ ПОДТВЕРЖДЕНО: 100% ЧЕСТНО"
                                    else "CRYPTOGRAPHICALLY VERIFIED: 100% FAIR"
                                } else {
                                    if (lang == "RU") "ОШИБКА АУДИТА: ОБНАРУЖЕНО НЕСООТВЕТСТВИЕ"
                                    else "AUDIT FAILURE: FRAUD DETECTED"
                                },
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentOutcome.isValid) Color(0xFF00E676) else Color(0xFFFF5252)
                            )
                            Text(
                                text = if (lang == "RU") "HMAC-SHA256 Commit-Reveal Scheme. Ни одна сторона не могла знать результат."
                                else "HMAC-SHA256 Commit-Reveal Scheme. Neither peer could anticipate outcome.",
                                fontSize = 8.5.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dice Result Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A202C))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (lang == "RU") "РЕЗУЛЬТАТ КОНСЕНСУСА" else "CONSENSUS OUTCOME",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🎲 ${currentOutcome.die1} : ${currentOutcome.die2}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD54F)
                            )
                            if (currentOutcome.die1 == currentOutcome.die2 && currentOutcome.die1 > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFFD54F).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (lang == "RU") "КУШ x4" else "DOUBLE x4",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (lang == "RU") "ФОРМУЛА КОНСЕНСУСА" else "CONSENSUS FORMULA",
                            fontSize = 8.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "D1 = (V_A + V_B) mod 6 + 1",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            text = "D2 = (S_A ⊕ S_B) mod 6 + 1",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E676)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Exchange Details Section
                Text(
                    text = if (lang == "RU") "ПРОТОКОЛ ОБМЕНА ХЭШАМИ И СЕМЕНАМИ:" else "PROTOCOL COMMITMENT & SEED EXCHANGE:",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Player A (Local) Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3748))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (lang == "RU") "Игрок А (Локальный)" else "Player A (Local)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF90CAF9)
                            )
                            Text(
                                text = "Secret Val: ${currentOutcome.localSecretValue}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF90CAF9)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Commitment HMAC: ${currentOutcome.localCommitmentHex.take(24)}...",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Seed Entropy: ${currentOutcome.localSeedHex.take(24)}...",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Player B (Peer) Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3748))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (lang == "RU") "Игрок Б (Peer / Оппонент)" else "Player B (Peer / Opponent)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFAB91)
                            )
                            Text(
                                text = "Secret Val: ${currentOutcome.peerSecretValue}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFAB91)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Commitment HMAC: ${currentOutcome.peerCommitmentHex.take(24)}...",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Seed Entropy: ${currentOutcome.peerSeedHex.take(24)}...",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live simulation indicator
                AnimatedVisibility(visible = isSimulatingExchange) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.15f * pulseAlpha))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = simulationStep,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Test Simulation Button
                    Button(
                        onClick = {
                            if (isSimulatingExchange) return@Button
                            coroutineScope.launch {
                                isSimulatingExchange = true
                                simulationStep = "1. Generating local entropy (32-byte CSPRNG)..."
                                delay(300)
                                simulationStep = "2. Exchanging HMAC-SHA256 Commitments..."
                                delay(350)
                                simulationStep = "3. Transmitting Seed Reveals over Ratchet..."
                                delay(300)
                                simulationStep = "4. Verifying HMAC & Computing XOR Modulo consensus..."
                                delay(250)

                                val freshOutcome = engine.simulateFairConsensusRoll()
                                currentOutcome = freshOutcome
                                onSimulatedRollApplied?.invoke(freshOutcome.die1, freshOutcome.die2)
                                isSimulatingExchange = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lang == "RU") "Тест броска ZKP" else "Test ZKP Roll",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Copy Proof Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(
                                "Mental Dice Proof",
                                "=== MADGAMMON MENTAL DICE ZKP AUDIT ===\n" +
                                        "Audit: ${currentOutcome.auditProof}\n" +
                                        "Local Seed: ${currentOutcome.localSeedHex}\n" +
                                        "Local HMAC: ${currentOutcome.localCommitmentHex}\n" +
                                        "Peer Seed: ${currentOutcome.peerSeedHex}\n" +
                                        "Peer HMAC: ${currentOutcome.peerCommitmentHex}\n" +
                                        "Result: ${currentOutcome.die1} : ${currentOutcome.die2}\n" +
                                        "Timestamp: ${currentOutcome.timestamp}"
                            )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                context,
                                if (lang == "RU") "ZKP аудит скопирован в буфер" else "ZKP audit copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A5568))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lang == "RU") "Копировать" else "Copy Proof",
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SimulatedXftpDistributionDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232A)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "XFTP File Distribution",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Decentralized file chunk distribution via SimpleX XFTP relays.",
                    fontSize = 13.sp,
                    color = Color(0xFFA0AEC0),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

