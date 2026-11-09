package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.GameViewModel

/**
 * Единое модальное окно настроек мессенджера SimpleX.
 * Скрывает всю не повседневно используемую конфигурацию:
 * - Сетевые мосты (Tor, V2Ray, Foxray VPN, Orbot фон, полный роутинг трафика).
 * - Настройки SMP и XFTP серверов / луковых адресов.
 * - Управление профилями пользователя и decoy-режимами.
 * - Настройки безопасности, кода принуждения (Duress) и ZK-распределения.
 * - Резервное копирование и восстановление криптоконтейнера.
 */
@Composable
fun MessengerSettingsModal(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedChatLanguage
    var activeTab by remember { mutableStateOf(0) } // 0 = Bridges & VPN, 1 = Relays, 2 = Security & Duress, 3 = Backup

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101419)),
            border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Заголовок и кнопка закрытия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚙️", fontSize = 18.sp)
                        Column {
                            Text(
                                text = if (lang == "RU") "Параметры и Защита SimpleX" else "SimpleX & Security Settings",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (lang == "RU") "Конфигурация сетевых мостов и приватности" else "Network bridges and privacy config",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_settings_modal_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Вкладки настроек
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        if (lang == "RU") "🛡️ Мосты & VPN" else "🛡️ Bridges & VPN",
                        if (lang == "RU") "📡 Релеи SMP" else "📡 SMP Relays",
                        if (lang == "RU") "🔒 Защита & Duress" else "🔒 Duress & Vault",
                        if (lang == "RU") "💾 Резервная копия" else "💾 Backup & Seed"
                    ).forEachIndexed { idx, title ->
                        val isSel = activeTab == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFF192028))
                                .border(1.dp, if (isSel) Color(0xFF00E676) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { activeTab = idx }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) Color(0xFF00E676) else Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Контент выбранной вкладки
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (activeTab) {
                        0 -> {
                            // --- ВКЛАДКА 0: МОСТЫ & VPN ---
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161C24)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (lang == "RU") "🌐 МАРШРУТИЗАЦИЯ ТРАФИКА И ФОН" else "🌐 TRAFFIC ROUTING & BACKGROUND",
                                        color = Color(0xFF00FF66),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )

                                    // 1. Постоянная работа в фоне без отключения (как Orbot)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (lang == "RU") "Фоновая работа без отключения (Orbot-режим)" else "Persistent background bridge (Orbot-mode)",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (lang == "RU") "Поддерживает сокеты и сетевой стек активными в фоновом сервисе" else "Keeps socket bridges alive via foreground service",
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                        Switch(
                                            checked = viewModel.isBackgroundBridgePersistent,
                                            onCheckedChange = { viewModel.updateBackgroundBridgePersistent(it) },
                                            modifier = Modifier.testTag("toggle_persistent_bg_switch")
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                    // 2. Полная маршрутизация всего трафика устройства через бридж
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (lang == "RU") "Маршрутизация всего трафика устройства" else "Full device traffic routing via bridge",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (lang == "RU") "Заворачивает 0.0.0.0/0 трафик всех приложений в VPN туннель" else "Routes all device network packets via 0.0.0.0/0 tun interface",
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                        Switch(
                                            checked = viewModel.isFullDeviceRoutingEnabled,
                                            onCheckedChange = { viewModel.updateFullDeviceRouting(it) },
                                            modifier = Modifier.testTag("toggle_full_device_routing_switch")
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                    // 3. Статус V2Ray / Foxray VPN
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Foxray V2Ray Core", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "Статус: ${viewModel.vpnManager.vpnState}",
                                                color = if (viewModel.vpnManager.vpnState == "Connected") Color(0xFF00FF66) else Color.LightGray,
                                                fontSize = 9.5.sp
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (viewModel.vpnManager.vpnState == "Connected") {
                                                    viewModel.vpnManager.stopVpnConnection()
                                                } else {
                                                    viewModel.vpnManager.startVpnConnectionSimulation()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (viewModel.vpnManager.vpnState == "Connected") Color(0xFFD32F2F) else Color(0xFF00E676)
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = if (viewModel.vpnManager.vpnState == "Connected") "СТОП" else "СТАРТ",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                    // 4. Статус встроенного Tor
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Embedded Tor (Onion Proxy)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("Status: ${viewModel.torStatus}", color = Color.Gray, fontSize = 9.5.sp)
                                        }
                                        Button(
                                            onClick = {
                                                if (viewModel.isTorEnabled) viewModel.stopTor() else viewModel.startTor()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (viewModel.isTorEnabled) Color(0xFFD32F2F) else Color(0xFF29B6F6)
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                text = if (viewModel.isTorEnabled) "ОТКЛ." else "ВКЛ.",
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // --- ВКЛАДКА 1: SMP & XFTP РЕЛЕИ ---
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161C24)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (lang == "RU") "📡 ДЕЦЕНТРАЛИЗОВАННЫЕ РЕЛЕИ SIMPLEX" else "📡 SIMPLEX RELAY ROUTING",
                                        color = Color(0xFF00FF66),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )

                                    var smpInput by remember { mutableStateOf(viewModel.customSmpServer) }
                                    var xftpInput by remember { mutableStateOf(viewModel.customXftpServer) }

                                    Text(if (lang == "RU") "SMP Сервер сообщений:" else "SMP Server URI:", color = Color.LightGray, fontSize = 10.sp)
                                    OutlinedTextField(
                                        value = smpInput,
                                        onValueChange = {
                                            smpInput = it
                                            viewModel.updateSmpServer(it)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                    )

                                    Text(if (lang == "RU") "XFTP Сервер файлов:" else "XFTP Server URI:", color = Color.LightGray, fontSize = 10.sp)
                                    OutlinedTextField(
                                        value = xftpInput,
                                        onValueChange = {
                                            xftpInput = it
                                            viewModel.updateXftpServer(it)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                    )

                                    Button(
                                        onClick = {
                                            viewModel.connectSimpleX()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text(if (lang == "RU") "ПЕРЕПОДКЛЮЧИТЬ РЕЛЕИ" else "RECONNECT RELAYS", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        2 -> {
                            // --- ВКЛАДКА 2: DURESS & ЗАЩИТА ---
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161C24)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (lang == "RU") "🔒 ЗАЩИТА ОТ ПРИНУЖДЕНИЯ (DURESS PIN)" else "🔒 DURESS PIN & SHREDDING",
                                        color = Color(0xFFFF5252),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = if (lang == "RU")
                                            "При вводе Duress-пинкода вместо настоящего активируется режим принуждения: очистка ключей шифрования, скрытие чатов или открытие фейкового профиля."
                                        else
                                            "Entering the duress PIN triggers panic actions: wipe encryption keys, hide secret rooms, or load decoy profiles.",
                                        color = Color.LightGray,
                                        fontSize = 10.sp
                                    )

                                    Button(
                                        onClick = { viewModel.showSimulatedXftpDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text(if (lang == "RU") "ТЕСТ ДУРЕСС / ZK SHARDING" else "TEST DURESS / ZK SHARDING", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        3 -> {
                            // --- ВКЛАДКА 3: БЭКАП & СИД-ФРАЗА ---
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161C24)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (lang == "RU") "💾 ЭКСПОРТ & ВОССТАНОВЛЕНИЕ" else "💾 BACKUP & VAULT RECOVERY",
                                        color = Color(0xFF29B6F6),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    val seed = viewModel.registeredIdentity?.seedPhrase ?: viewModel.currentSeedPhrase
                                    Text(if (lang == "RU") "BIP-39 Мастер-сид контейнера:" else "BIP-39 Master Mnemonic:", color = Color.Gray, fontSize = 10.sp)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black)
                                            .padding(8.dp)
                                    ) {
                                        Text(seed, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00FF66))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text(if (lang == "RU") "ГОТОВО" else "DONE", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}
