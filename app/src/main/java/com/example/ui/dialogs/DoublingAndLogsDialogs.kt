package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GameLogEntry
import com.example.model.Player
import com.example.ui.GameViewModel
import com.example.ui.screens.Language

/**
 * Диалог предложения удвоения ставки партии (Куб удвоения / Даве).
 * Позволяет игроку запросить увеличение ставки матча (x2, x4, x8, x16, x32, x64)
 * либо добровольно признать поражение в партии.
 */
@Composable
fun DoublingDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    onResignClick: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    val nextMult = viewModel.stakeMultiplier * 2
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (lang == "RU") "🎲 Удвоение ставки (Даве)" else "🎲 Doubling Cube",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (lang == "RU") "Текущая ставка партии:" else "Current Stake:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "x${viewModel.stakeMultiplier}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val ownerText = when (viewModel.doublingOwner) {
                            null -> if (lang == "RU") "Куб в центре (удвоение доступно)" else "Cube is neutral (available to propose)"
                            viewModel.humanPlayerColor -> if (lang == "RU") "Куб у вас (вы можете удвоить)" else "You own the cube"
                            else -> if (lang == "RU") "Куб у соперника" else "Opponent owns the cube"
                        }
                        Text(
                            text = ownerText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (lang == "RU") {
                        "Предложите сопернику удвоить ставку до x$nextMult. Если соперник откажется — он сдается, и вы побеждаете со ставкой x${viewModel.stakeMultiplier}."
                    } else {
                        "Propose doubling stake to x$nextMult. If opponent declines, they resign and you win with stake x${viewModel.stakeMultiplier}."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.proposeDoubling()
                        onDismiss()
                    },
                    enabled = viewModel.canProposeDoubling,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("propose_doubling_btn")
                ) {
                    Text(
                        text = if (lang == "RU") "Предложить ставку x$nextMult" else "Propose x$nextMult",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onResignClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("dialog_resign_btn")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (lang == "RU") "🏳️ Сдаться в партии" else "🏳️ Resign Match",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (lang == "RU") "Закрыть" else "Cancel")
                }
            }
        }
    }
}

/**
 * Диалог входящего предложения удвоения ставки от соперника или ИИ.
 */
@Composable
fun IncomingDoublingDialog(
    viewModel: GameViewModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    val nextMult = viewModel.stakeMultiplier * 2
    val proposer = viewModel.doublingProposer?.label(lang) ?: if (lang == "RU") "Соперник" else "Opponent"

    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎲 " + (if (lang == "RU") "ПРЕДЛОЖЕНИЕ УДВОЕНИЯ!" else "DOUBLING PROPOSED!"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (lang == "RU") {
                        "$proposer предлагает удвоить ставку партии до x$nextMult!\n\nВы можете принять удвоение и продолжить игру со ставкой x$nextMult, либо сдаться и зафиксировать поражение с текущей ставкой x${viewModel.stakeMultiplier}."
                    } else {
                        "$proposer proposes doubling match stake to x$nextMult!\n\nAccept to continue with x$nextMult, or resign with current stake x${viewModel.stakeMultiplier}."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("accept_doubling_btn")
                ) {
                    Text(
                        text = if (lang == "RU") "Принять удвоение (x$nextMult)" else "Accept Doubling (x$nextMult)",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("decline_doubling_btn")
                ) {
                    Text(
                        text = if (lang == "RU") "🏳️ Сдаться (отклонить)" else "🏳️ Surrender (Decline)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Диалог подтверждения добровольной сдачи (капитуляции) в матче.
 */
@Composable
fun ResignConfirmDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (lang == "RU") "Признать поражение?" else "Resign Match?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = if (lang == "RU") {
                    "Вы действительно хотите сдаться? Победа в этой партии со ставкой x${viewModel.stakeMultiplier} будет присуждена сопернику."
                } else {
                    "Are you sure you want to resign? The victory with stake x${viewModel.stakeMultiplier} will be awarded to your opponent."
                },
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_resign_btn")
            ) {
                Text(if (lang == "RU") "Сдаться" else "Resign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "RU") "Отмена" else "Cancel")
            }
        }
    )
}

/**
 * Диалог подробного технического лога ходов текущей партии.
 */
@Composable
fun LogsDialog(
    logs: List<GameLogEntry>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Подробный Лог Игры",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Box(modifier = Modifier.height(200.dp)) {
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("История ходов пуста", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val state = rememberLazyListState()
                        LaunchedEffect(logs.size) {
                            state.animateScrollToItem(logs.size - 1)
                        }
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                            items(logs) { entry ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(
                                        text = "[${entry.timestampString}] ",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = entry.message,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Локализованная метка цвета игрока.
 */
fun Player.localizedLabel(lang: String): String {
    return if (this == Player.WHITE) Language.get("white", lang) else Language.get("black", lang)
}
