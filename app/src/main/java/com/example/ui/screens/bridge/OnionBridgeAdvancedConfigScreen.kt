package com.example.ui.screens.bridge

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.GameViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnionBridgeAdvancedConfigScreen(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val bridgeManager = remember { viewModel.onionBridgeConfigManager }
    val lang = viewModel.selectedLanguage

    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Топология & Цепочка, 1 = Протоколы & Импорт, 2 = Сервера Onion, 3 = Strict Killswitch
    var showAddServerDialog by remember { mutableStateOf(false) }
    var selectedFilterType by remember { mutableStateOf<RelayNodeType?>(null) }
    var rawConfigInput by remember { mutableStateOf(bridgeManager.activeBridgeRawConfig) }

    // Лаунчер для импорта файла конфигурации
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                } ?: ""
                if (content.isNotBlank()) {
                    rawConfigInput = content
                    val imported = bridgeManager.importConfigsFromText(content)
                    Toast.makeText(context, if (lang == "RU") "Импортировано записей: $imported" else "Imported entries: $imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ==========================================
            // ВЕРХНИЙ APP BAR
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Color(0xFF101622))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("bridge_config_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (lang == "RU") "КОНФИГУРАТОР МОСТА VPN + TOR" else "VPN + TOR BRIDGE CONFIG",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            if (bridgeManager.isStrictOnionOnlyEnabled) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFAB47BC).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color(0xFFAB47BC), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("ONION-ONLY", fontSize = 8.sp, color = Color(0xFFCE93D8), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            text = if (lang == "RU") "Мультипротокольные цепи • SMP/XFTP/ICE релеи • Killswitch" else "Multi-protocol chains • SMP/XFTP/ICE relays • Killswitch",
                            fontSize = 8.5.sp,
                            color = Color(0xFF00FF66)
                        )
                    }
                }

                // Кнопка быстрого тестирования
                Button(
                    onClick = {
                        bridgeManager.testAllRelaysThroughBridge { verified, failed ->
                            Toast.makeText(context, if (lang == "RU") "Доступно: $verified, Ошибок: $failed" else "Online: $verified, Failed: $failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !bridgeManager.isTestingActive,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    if (bridgeManager.isTestingActive) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "RU") "ТЕСТ" else "TEST", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }

            // ==========================================
            // ВКЛАДКИ РАЗДЕЛОВ
            // ==========================================
            val tabs = listOf(
                if (lang == "RU") "1. Топология цепи" else "1. Chain Topology",
                if (lang == "RU") "2. VPN & Импорт" else "2. VPN & Import",
                if (lang == "RU") "3. Onion Релеи" else "3. Onion Relays",
                if (lang == "RU") "4. Killswitch" else "4. Killswitch"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C121C))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = activeSubTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1E2838) else Color.Transparent)
                            .border(0.5.dp, if (isSelected) Color(0xFF00FF66) else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { activeSubTab = index }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF00FF66) else Color.Gray,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Статус-бар тестирования (если активно)
            AnimatedVisibility(visible = bridgeManager.isTestingActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B2A1C))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF00FF66), strokeWidth = 1.5.dp)
                        Text(bridgeManager.testingStatusMessage, fontSize = 9.5.sp, color = Color(0xFF00FF66), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ==========================================
            // СОДЕРЖИМОЕ АКТИВНОЙ ВКЛАДКИ
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                when (activeSubTab) {
                    0 -> TabChainTopology(bridgeManager = bridgeManager, lang = lang)
                    1 -> TabVpnAndImport(
                        bridgeManager = bridgeManager,
                        rawConfigInput = rawConfigInput,
                        onConfigChanged = { rawConfigInput = it },
                        onPickFile = { filePickerLauncher.launch("*/*") },
                        lang = lang
                    )
                    2 -> TabOnionRelays(
                        bridgeManager = bridgeManager,
                        filterType = selectedFilterType,
                        onFilterSelected = { selectedFilterType = it },
                        onAddNewServer = { showAddServerDialog = true },
                        lang = lang
                    )
                    3 -> TabStrictKillswitch(bridgeManager = bridgeManager, lang = lang)
                }
            }
        }
    }

    // Диалог добавления кастомного сервера
    if (showAddServerDialog) {
        AddServerDialog(
            lang = lang,
            onDismiss = { showAddServerDialog = false },
            onAdd = { type, name, url, notes ->
                val success = bridgeManager.addRelayNode(type, name, url, notes)
                if (success) {
                    Toast.makeText(context, if (lang == "RU") "Сервер добавлен" else "Server added", Toast.LENGTH_SHORT).show()
                    showAddServerDialog = false
                } else {
                    Toast.makeText(context, if (lang == "RU") "Некорректный адрес" else "Invalid URL", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ============================================================================
// ВКЛАДКА 1: ТОПОЛОГИЯ ЦЕПОЧКИ МОСТА
// ============================================================================
@Composable
private fun TabChainTopology(bridgeManager: OnionBridgeConfigManager, lang: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121926)),
                border = BorderStroke(1.dp, Color(0xFF1F2C42)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "RU") "СТРУКТУРА СЕТЕВОГО МОСТА (МУЛЬТИХОП)" else "BRIDGE CHAIN TOPOLOGY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00FF66)
                    )
                    Text(
                        text = if (lang == "RU") "Выберите порядок прохождения трафика через прокси-узлы и луковую сеть Tor. Цепочки маскируют ваше присутствие и защищают от блокировок провайдера (DPI)."
                        else "Select the path of traffic through proxy hops and Tor. Multi-hop chains mask your connection against DPI censorship.",
                        fontSize = 9.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        items(BridgeChainTopology.values()) { topology ->
            val isSelected = bridgeManager.chainTopology == topology
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF14241E) else Color(0xFF121824)
                ),
                border = BorderStroke(1.dp, if (isSelected) Color(0xFF00FF66) else Color(0xFF1F2C42)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { bridgeManager.updateChainTopology(topology) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { bridgeManager.updateChainTopology(topology) },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00FF66))
                            )
                            Text(
                                text = if (lang == "RU") topology.displayNameRu else topology.displayNameEn,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF00FF66) else Color.White
                            )
                        }
                        Text(
                            text = topology.descriptionRu,
                            fontSize = 8.5.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                        // Визуальная диаграмма звеньев
                        Row(
                            modifier = Modifier.padding(start = 32.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChainDiagramBadge("Приложение", Color(0xFF29B6F6))
                            Text("➔", fontSize = 9.sp, color = Color.Gray)
                            when (topology) {
                                BridgeChainTopology.TOR_DIRECT -> {
                                    ChainDiagramBadge("Tor SOCKS5 (:9050)", Color(0xFFAB47BC))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("Onion Сервис", Color(0xFF00FF66))
                                }
                                BridgeChainTopology.VPN_OVER_TOR -> {
                                    ChainDiagramBadge("VLESS / Reality", Color(0xFFFFB300))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("Tor Guard Node", Color(0xFFAB47BC))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("Onion Сервис", Color(0xFF00FF66))
                                }
                                BridgeChainTopology.TOR_OVER_VPN -> {
                                    ChainDiagramBadge("Tor SOCKS5", Color(0xFFAB47BC))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("VPN Gateway", Color(0xFFFFB300))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("Clearnet / Web", Color(0xFF42A5F5))
                                }
                                BridgeChainTopology.MULTI_HOP_CHAIN -> {
                                    ChainDiagramBadge("Obfs4 Bridge", Color(0xFFE91E63))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("VLESS Reality", Color(0xFFFFB300))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("Tor Guard", Color(0xFFAB47BC))
                                    Text("➔", fontSize = 9.sp, color = Color.Gray)
                                    ChainDiagramBadge("Onion", Color(0xFF00FF66))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainDiagramBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .border(0.5.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 7.5.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

// ============================================================================
// ВКЛАДКА 2: VPN ПРОТОКОЛЫ & ИМПОРТ
// ============================================================================
@Composable
private fun TabVpnAndImport(
    bridgeManager: OnionBridgeConfigManager,
    rawConfigInput: String,
    onConfigChanged: (String) -> Unit,
    onPickFile: () -> Unit,
    lang: String
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121926)),
                border = BorderStroke(1.dp, Color(0xFF1F2C42)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "RU") "ПОДДЕРЖИВАЕМЫЕ ПРОТОКОЛЫ VPN & OBFUSCATION" else "SUPPORTED PROTOCOLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = if (lang == "RU") "Выберите протокол активного шлюза для сопряжения с сетью Tor:" else "Select active protocol to bridge with Tor:",
                        fontSize = 8.5.sp,
                        color = Color.LightGray
                    )

                    // Сетка выбора протоколов
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        VpnProtocolType.values().take(4).forEach { proto ->
                            val isSel = bridgeManager.selectedVpnProtocol == proto
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF090D14))
                                    .border(1.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF1F2C42), RoundedCornerShape(6.dp))
                                    .clickable { bridgeManager.updateVpnProtocol(proto) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(proto.protocolTag, fontSize = 8.sp, color = if (isSel) Color(0xFF00E5FF) else Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        VpnProtocolType.values().drop(4).forEach { proto ->
                            val isSel = bridgeManager.selectedVpnProtocol == proto
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF090D14))
                                    .border(1.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF1F2C42), RoundedCornerShape(6.dp))
                                    .clickable { bridgeManager.updateVpnProtocol(proto) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(proto.protocolTag, fontSize = 8.sp, color = if (isSel) Color(0xFF00E5FF) else Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121926)),
                border = BorderStroke(1.dp, Color(0xFF1F2C42)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (lang == "RU") "ДОБАВЛЕНИЕ КОНФИГУРАЦИИ МОСТА" else "BRIDGE CONFIGURATION INPUT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        OutlinedButton(
                            onClick = onPickFile,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "RU") "Импорт файла" else "Import File", fontSize = 9.sp, color = Color(0xFF00E5FF))
                        }
                    }

                    OutlinedTextField(
                        value = rawConfigInput,
                        onValueChange = onConfigChanged,
                        placeholder = {
                            Text("Вставьте vless://, vmess://, ss://, hy2://, trojan://, obfs4 строку или JSON конфиг...", fontSize = 8.5.sp, color = Color.Gray)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF1F2C42),
                            focusedContainerColor = Color(0xFF070B12),
                            unfocusedContainerColor = Color(0xFF070B12)
                        )
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (rawConfigInput.isNotBlank()) {
                                    val imported = bridgeManager.importConfigsFromText(rawConfigInput)
                                    bridgeManager.updateActiveBridgeRawConfig(rawConfigInput)
                                    Toast.makeText(context, if (lang == "RU") "Применено узлов: $imported" else "Applied nodes: $imported", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text(if (lang == "RU") "ПРИМЕНИТЬ В МОСТ" else "APPLY TO BRIDGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        Button(
                            onClick = {
                                onConfigChanged("")
                                bridgeManager.updateActiveBridgeRawConfig("")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF243042)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (lang == "RU") "ОЧИСТИТЬ" else "CLEAR", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// ВКЛАДКА 3: ONION РЕЛЕИ & СЕРВЕРА
// ============================================================================
@Composable
private fun TabOnionRelays(
    bridgeManager: OnionBridgeConfigManager,
    filterType: RelayNodeType?,
    onFilterSelected: (RelayNodeType?) -> Unit,
    onAddNewServer: () -> Unit,
    lang: String
) {
    val context = LocalContext.current
    val displayedNodes = remember(bridgeManager.relayNodes.size, filterType) {
        if (filterType == null) bridgeManager.relayNodes else bridgeManager.relayNodes.filter { it.type == filterType }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Панель фильтров и кнопок действий
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChipCompact(label = if (lang == "RU") "Все" else "All", isSelected = filterType == null) { onFilterSelected(null) }
                FilterChipCompact(label = "SMP", isSelected = filterType == RelayNodeType.SMP) { onFilterSelected(RelayNodeType.SMP) }
                FilterChipCompact(label = "XFTP", isSelected = filterType == RelayNodeType.XFTP) { onFilterSelected(RelayNodeType.XFTP) }
                FilterChipCompact(label = "ICE/TURN", isSelected = filterType == RelayNodeType.ICE_TURN) { onFilterSelected(RelayNodeType.ICE_TURN) }
                FilterChipCompact(label = "VPN", isSelected = filterType == RelayNodeType.VPN_NODE) { onFilterSelected(RelayNodeType.VPN_NODE) }
            }

            IconButton(
                onClick = onAddNewServer,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF00FF66).copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Server", tint = Color(0xFF00FF66), modifier = Modifier.size(16.dp))
            }
        }

        // Кнопка сохранения только проверенных серверов
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141D2B), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (lang == "RU") "Активных узлов: ${bridgeManager.relayNodes.size} (Onion: ${bridgeManager.relayNodes.count { it.isOnion }})"
                    else "Active nodes: ${bridgeManager.relayNodes.size} (Onion: ${bridgeManager.relayNodes.count { it.isOnion }})",
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lang == "RU") "Удалить упавшие узлы после теста" else "Remove unreachable nodes",
                    fontSize = 7.5.sp,
                    color = Color.LightGray
                )
            }

            Button(
                onClick = {
                    val removed = bridgeManager.commitVerifiedServersOnly()
                    Toast.makeText(context, if (lang == "RU") "Сохранено! Удалено недоступных: $removed" else "Committed! Removed: $removed", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2F40)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(if (lang == "RU") "💾 СОХРАНИТЬ РАБОЧИЕ" else "💾 COMMIT ONLINE", fontSize = 8.5.sp, color = Color(0xFF00FF66), fontWeight = FontWeight.Bold)
            }
        }

        // Список узлов
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(displayedNodes, key = { it.id }) { node ->
                RelayNodeItemCard(
                    node = node,
                    onDelete = { bridgeManager.removeRelayNode(node.id) }
                )
            }
        }
    }
}

@Composable
private fun FilterChipCompact(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF00FF66) else Color(0xFF121926))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 8.5.sp, color = if (isSelected) Color.Black else Color.LightGray, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RelayNodeItemCard(node: RelayNode, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111722)),
        border = BorderStroke(0.5.dp, if (node.isVerified) Color(0xFF00FF66) else if (node.pingMs == -3 || node.pingMs == -4) Color(0xFFFF5252) else Color(0xFF1F2C42)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when (node.type) {
                            RelayNodeType.SMP -> "💬 SMP"
                            RelayNodeType.XFTP -> "📁 XFTP"
                            RelayNodeType.ICE_TURN -> "📞 ICE/TURN"
                            RelayNodeType.VPN_NODE -> "🛡️ VPN"
                        },
                        fontSize = 8.sp,
                        color = Color(0xFF29B6F6),
                        fontWeight = FontWeight.Bold
                    )
                    if (node.isOnion) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFAB47BC).copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("🧅 ONION v3", fontSize = 7.sp, color = Color(0xFFCE93D8), fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = node.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = node.url,
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Пинг и статус
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        node.pingMs == -2 -> {
                            Text("⏳ Проверка через мост...", fontSize = 7.5.sp, color = Color(0xFFFFB300))
                        }
                        node.pingMs > 0 -> {
                            Text("⚡ ONLINE (${node.pingMs}ms)", fontSize = 7.5.sp, color = Color(0xFF00FF66), fontWeight = FontWeight.Bold)
                        }
                        node.pingMs == -4 -> {
                            Text("🛑 BLOCKED (Strict Onion Policy)", fontSize = 7.5.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                        node.pingMs == -3 -> {
                            Text("❌ OFFLINE (Таймаут сокета)", fontSize = 7.5.sp, color = Color(0xFFFF5252))
                        }
                        else -> {
                            Text("⚪ Не тестировался", fontSize = 7.5.sp, color = Color.Gray)
                        }
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ============================================================================
// ВКЛАДКА 4: STRICT ONION-ONLY POLICY (KILLSWITCH)
// ============================================================================
@Composable
private fun TabStrictKillswitch(bridgeManager: OnionBridgeConfigManager, lang: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (bridgeManager.isStrictOnionOnlyEnabled) Color(0xFF261230) else Color(0xFF141926)
                ),
                border = BorderStroke(1.5.dp, if (bridgeManager.isStrictOnionOnlyEnabled) Color(0xFFAB47BC) else Color(0xFF1F2C42)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFAB47BC).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🧅", fontSize = 18.sp)
                            }
                            Column {
                                Text(
                                    text = if (lang == "RU") "СТРОГАЯ ONION-ИЗОЛЯЦИЯ (KILLSWITCH)" else "STRICT ONION-ONLY KILLSWITCH",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (bridgeManager.isStrictOnionOnlyEnabled) Color(0xFFE1BEE7) else Color.White
                                )
                                Text(
                                    text = if (bridgeManager.isStrictOnionOnlyEnabled) {
                                        if (lang == "RU") "🛡️ АКТИВЕН • ЛЮБОЙ ТРАФИК ВНЕ ONION ЗАБЛОКИРОВАН" else "🛡️ ENFORCED • ALL CLEARNET TRAFFIC DROPPED"
                                    } else {
                                        if (lang == "RU") "⚪ НЕАКТИВЕН • РАЗРЕШЕН CLEARNET ТРАФИК" else "⚪ INACTIVE • CLEARNET ALLOWED"
                                    },
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bridgeManager.isStrictOnionOnlyEnabled) Color(0xFF00FF66) else Color.Gray
                                )
                            }
                        }

                        Switch(
                            checked = bridgeManager.isStrictOnionOnlyEnabled,
                            onCheckedChange = { bridgeManager.updateStrictOnionOnly(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00FF66),
                                checkedTrackColor = Color(0xFFAB47BC)
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    Text(
                        text = if (lang == "RU") """
                            Аппаратный сетевой выключатель (Killswitch):
                            • При включении приложения полностью запрещается отправка сетевых пакетов на адреса за пределами сети .onion.
                            • Исключается риск утечки реального IP-адреса и деанонимизации пользователя при случайном запросе мимо моста VPN+Tor.
                            • Все SimpleX очереди (SMP), медиа-хранилища (XFTP), STUN/TURN звонки и сокеты нард маршрутизируются строго через локальный Tor SOCKS5 (:9050).
                            • Попытки соединения с открытыми HTTP/TCP серверами блокируются на уровне встроенного пакетного фильтра приложения.
                        """.trimIndent()
                        else """
                            Hardware-grade network killswitch:
                            • Strictly prevents transmission of packets to addresses outside the .onion network.
                            • Eliminates risk of IP deanonymization if traffic ever bypasses the VPN+Tor bridge.
                            • Forces SimpleX queues (SMP), XFTP storage, STUN/TURN calls, and Backgammon sockets to route solely through Tor SOCKS5 (:9050).
                            • Clearnet connections are immediately intercepted and terminated by the in-app packet filter.
                        """.trimIndent(),
                        fontSize = 8.5.sp,
                        color = Color.LightGray,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// ДИАЛОГ ДОБАВЛЕНИЯ СЕРВЕРА
// ============================================================================
@Composable
private fun AddServerDialog(
    lang: String,
    onDismiss: () -> Unit,
    onAdd: (type: RelayNodeType, name: String, url: String, notes: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(RelayNodeType.SMP) }
    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101622)),
            border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (lang == "RU") "ДОБАВЛЕНИЕ СЕРВЕРА / РЕЛЕЯ" else "ADD RELAY / SERVER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00FF66)
                )

                // Выбор типа
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RelayNodeType.values().forEach { t ->
                        val isSel = selectedType == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSel) Color(0xFF00FF66) else Color(0xFF1B2433))
                                .clickable { selectedType = t }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t.name, fontSize = 8.sp, color = if (isSel) Color.Black else Color.LightGray, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(if (lang == "RU") "Название узла" else "Node Name", fontSize = 9.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = Color.White)
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(if (lang == "RU") "Адрес (.onion или clearnet)" else "Address (.onion or clearnet)", fontSize = 9.sp) },
                    placeholder = { Text("smp://...onion или turn:...onion:3478", fontSize = 8.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color.White)
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(if (lang == "RU") "Примечание" else "Notes", fontSize = 9.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = Color.White)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838)),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 9.5.sp, color = Color.White)
                    }

                    Button(
                        onClick = { onAdd(selectedType, nameInput, urlInput, notesInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66)),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Text(if (lang == "RU") "ДОБАВИТЬ" else "ADD", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
