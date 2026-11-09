package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.GameViewModel

/**
 * Диалог активации сетевого моста VPN-TOR (Radio Armageddon Gateway).
 *
 * Предоставляет пользователю прозрачный контроль над тремя уровнями приватности:
 * 1. **Foxray Core VPN**: VLESS / ShadowTLS / Reality шлюз.
 * 2. **Tor SOCKS5 Proxy**: Локальный онион-маршрутизатор (127.0.0.1:9050).
 * 3. **SimpleX SMP / XFTP Relay**: Сквозное E2EE шифрование очередей сообщений без метаданных.
 * 4. Консоль сетевых логов в реальном времени.
 *
 * @param viewModel главная вьюмодель управления состоянием игры и сетевых сервисов.
 * @param onDismiss коллбек закрытия диалога.
 */
@Composable
fun BridgeActivationDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    val vpnManager = viewModel.vpnManager
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101418)),
            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Шапка
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌉", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = if (lang == "RU") "Активация моста VPN-TOR" else "VPN-TOR Bridge Activation",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (lang == "RU") "Сетевой шлюз Radio Armageddon" else "Radio Armageddon Gateway",
                                fontSize = 9.sp,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

                // Карточки компонентов сетевого стека
                // 1. Foxray VPN
                val isVpnConnected = vpnManager.vpnState == "Connected"
                val isVpnConnecting = vpnManager.vpnState == "Connecting"
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, if (isVpnConnected) Color(0xFF00E676) else Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🦊", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "Foxray Core VPN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isVpnConnected) "CONNECTED (${vpnManager.selectedConfig?.name ?: "Tor Relay VLESS"})" else if (isVpnConnecting) "CONNECTING..." else "DISCONNECTED",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isVpnConnected) Color(0xFF00E676) else if (isVpnConnecting) Color(0xFFFFB300) else Color.Gray
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (isVpnConnected || isVpnConnecting) {
                                    vpnManager.stopVpnConnectionSimulation()
                                } else {
                                    vpnManager.startVpnConnectionSimulation()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVpnConnected) Color(0xFFD32F2F) else Color(0xFF00E676)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isVpnConnected) (if (lang == "RU") "ОТКЛЮЧИТЬ" else "STOP") else (if (lang == "RU") "ВКЛЮЧИТЬ" else "START"),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVpnConnected) Color.White else Color.Black
                            )
                        }
                    }
                }

                // 2. Tor SOCKS5 Proxy
                val isTorActive = viewModel.isTorEnabled && viewModel.torStatus == "ACTIVE"
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, if (isTorActive) Color(0xFF00E676) else Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🧅", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "Tor SOCKS5 (127.0.0.1:9050)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isTorActive) "ACTIVE (Circuits 100%)" else "STOPPED",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isTorActive) Color(0xFF00E676) else Color.Gray
                                )
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.setTorEnabledState(!isTorActive)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTorActive) Color(0xFFD32F2F) else Color(0xFF00E676)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isTorActive) (if (lang == "RU") "ОТКЛЮЧИТЬ" else "STOP") else (if (lang == "RU") "ВКЛЮЧИТЬ" else "START"),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTorActive) Color.White else Color.Black
                            )
                        }
                    }
                }

                // 3. SimpleX E2EE Protocol
                val isSynced = viewModel.v2RayTorSyncStatus == "SYNCED"
                val isSyncing = viewModel.isV2RayTorSyncing
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, if (isSynced) Color(0xFF00E676) else Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("💬", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "SimpleX SMP / XFTP Relay",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isSynced) "SYNCED & READY" else if (isSyncing) "SYNCING..." else "NOT SYNCED",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSynced) Color(0xFF00E676) else if (isSyncing) Color(0xFFFFB300) else Color.Gray
                                )
                            }
                        }
                    }
                }

                // 4. Опции фоновой маршрутизации (Режим Orbot)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "⚙️ РЕЖИМ МАРШРУТИЗАЦИИ (ORBOT)" else "⚙️ ROUTING MODES (ORBOT)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E676)
                        )

                        // Переключатель фоновой работы без отключения
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == "RU") "Работа в фоне без отключения" else "Persistent Background (Orbot)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (lang == "RU") "Служба моста работает непрерывно при сворачивании" else "Bridge daemon stays running when minimized",
                                    fontSize = 8.5.sp,
                                    color = Color.LightGray
                                )
                            }
                            Switch(
                                checked = viewModel.isBackgroundBridgePersistent,
                                onCheckedChange = { viewModel.updateBackgroundBridgePersistent(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00E676),
                                    checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.35f)
                                )
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        // Переключатель полной маршрутизации устройства
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == "RU") "Полная маршрутизация устройства" else "Full Device VPN Tunneling",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (lang == "RU") "Заворачивать трафик всех приложений в VPN-Tor мост" else "Route all device apps through encrypted bridge",
                                    fontSize = 8.5.sp,
                                    color = Color.LightGray
                                )
                            }
                            Switch(
                                checked = viewModel.isFullDeviceRoutingEnabled,
                                onCheckedChange = { viewModel.updateFullDeviceRouting(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00E676),
                                    checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                }

                // Мастер-кнопка синхронизации моста
                Button(
                    onClick = {
                        viewModel.connectAndSyncAllNetworkComponents()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSynced) Color(0xFF0D533A) else Color(0xFF00E676)
                    ),
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (lang == "RU") "Активация компонентов..." else "Activating components...", fontSize = 11.sp, color = Color.White)
                    } else {
                        Text(
                            text = if (isSynced) {
                                if (lang == "RU") "⚡ МОСТ АКТИВЕН • ПЕРЕЗАПУСТИТЬ" else "⚡ BRIDGE ACTIVE • RE-SYNC"
                            } else {
                                if (lang == "RU") "🚀 АКТИВИРОВАТЬ ПОЛНЫЙ МОСТ" else "🚀 ACTIVATE ALL BRIDGES"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSynced) Color(0xFF00E676) else Color.Black
                        )
                    }
                }

                // Логи в реальном времени
                if (viewModel.v2RayTorSyncLogs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(6.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(viewModel.v2RayTorSyncLogs) { item ->
                                Text(item, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                            }
                        }
                    }
                }

                Text(
                    text = if (lang == "RU") "🔒 Все сетевые соединения инициируются явно пользователем. Мост активен во время работы приложения."
                    else "🔒 All network operations are explicitly user-activated. The bridge remains active while the application runs.",
                    fontSize = 8.5.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Кнопка перехода к расширенной конфигурации цепей и .onion релеев
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        viewModel.isOnionBridgeAdvancedConfigOpen = true
                    },
                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00E676)
                    ),
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (lang == "RU") "⚙️ КОНФИГУРАТОР ЦЕПЕЙ & .ONION РЕЛЕЕВ" else "⚙️ BRIDGE CHAINS & .ONION CONFIG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "ПРОДОЛЖИТЬ" else "CONTINUE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
