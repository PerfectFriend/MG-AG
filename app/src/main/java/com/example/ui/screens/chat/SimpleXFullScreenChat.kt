package com.example.ui.screens.chat

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Bip39Helper
import com.example.data.MatchHistory
import com.example.model.*
import com.example.ui.GameViewModel
import com.example.ui.components.*
import com.example.ui.dialogs.CryptocontainerSetupWizard
import com.example.ui.screens.Language
import com.example.ui.screens.wallet.CryptoWalletFullScreen
import com.example.ui.screens.chat.components.AttachmentBottomSheet
import com.example.ui.screens.chat.components.MessageBubble
import com.example.ui.screens.chat.components.MessageContextMenu
import com.example.ui.screens.chat.components.ScrollToBottomFab
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleXFullScreenChat(
    viewModel: GameViewModel,
    initialTabSegment: Int = 0,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedChatLanguage
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vpnManager = viewModel.vpnManager

    // Keep portrait mode while in chat layer safely
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        try {
            if (activity?.requestedOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } catch (_: Exception) {}
        onDispose { }
    }

    // Auto dismiss chat screen if online game starts from challenge
    LaunchedEffect(viewModel.challengeGameStartedTrigger) {
        if (viewModel.challengeGameStartedTrigger) {
            onDismiss()
            viewModel.challengeGameStartedTrigger = false
        }
    }

    var activeChatRoom by remember { mutableStateOf<String?>(null) } // null = Chat list, "BOT_CHAT", "DEV_CHAT", "LOBBY_CHAT"
    var activeTabSegment by remember { mutableStateOf(initialTabSegment) }
    var chatSubSegment by remember { mutableStateOf(0) } // 0 = Chats, 1 = Contacts, 2 = Create Invite, 3 = Join Chat
    val localKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var showReactionTrayForMsgId by remember { mutableStateOf<String?>(null) }
    var focusedFieldId by remember { mutableStateOf<String?>(null) }
    
    // Camera / scanning states
    var showCameraView by remember { mutableStateOf(false) }
    var isCameraScanning by remember { mutableStateOf(false) }
    var cameraScanResult by remember { mutableStateOf("") }
    var scanningServerType by remember { mutableStateOf("SMP") } // SMP or XFTP

    var vpnFileAlert by remember { mutableStateOf("") }
    var showVpnTextImportDialog by remember { mutableStateOf(false) }

    // Backup & Restore settings state vars
    var showBackupPinVerifyDialog by remember { mutableStateOf(false) }
    var backupActionType by remember { mutableStateOf("EXPORT") } // EXPORT or IMPORT
    var backupPinError by remember { mutableStateOf("") }
    var showExportResultDialog by remember { mutableStateOf(false) }
    var showImportInputDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }

    var showSaveCryptoKeyDialog by remember { mutableStateOf(false) }
    var exportedSeedPhraseText by remember { mutableStateOf("") }
    var exportedContainerText by remember { mutableStateOf("") }

    // Multi-profile & Duress settings modals
    var showProfileModal by remember { mutableStateOf(false) }
    var showDuressSecurityModal by remember { mutableStateOf(false) }
    var showCreateGroupModal by remember { mutableStateOf(false) }
    var showMessengerSettingsModal by remember { mutableStateOf(false) }
    
    // Telegram/WhatsApp Style navigation & menu states
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRoomOverflowMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    var showFabActionModal by remember { mutableStateOf(false) }
    
    val vpnConfigImportLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val res = viewModel.vpnManager.importConfigFromFile(it)
            vpnFileAlert = res
        }
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vpnManager.startVpnConnectionSimulation()
        }
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // After runtime permissions, check and request VPN creation
        try {
            val prepIntent = android.net.VpnService.prepare(context)
            if (prepIntent != null) {
                vpnPrepareLauncher.launch(prepIntent)
            } else {
                vpnManager.startVpnConnection()
            }
        } catch (_: Exception) {}
    }

    // Comprehensive permission flow: request CAMERA, AUDIO, NOTIFICATIONS and VPN upon entering Messenger
    LaunchedEffect(Unit) {
        try {
            val neededPerms = mutableListOf<String>()
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                neededPerms.add(android.Manifest.permission.CAMERA)
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                neededPerms.add(android.Manifest.permission.RECORD_AUDIO)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                neededPerms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (neededPerms.isNotEmpty()) {
                runtimePermissionsLauncher.launch(neededPerms.toTypedArray())
            } else {
                val prepIntent = android.net.VpnService.prepare(context)
                if (prepIntent != null) {
                    vpnPrepareLauncher.launch(prepIntent)
                } else {
                    vpnManager.startVpnConnection()
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(viewModel.showVpnPermissionRequest) {
        viewModel.showVpnPermissionRequest?.let { intent ->
            vpnPrepareLauncher.launch(intent)
            viewModel.showVpnPermissionRequest = null
        }
    }

    LaunchedEffect(focusedFieldId) {
        if (focusedFieldId != null && viewModel.isNoGameModeEnabled) {
            localKeyboard?.hide()
        }
    }

    BackHandler(enabled = true) {
        if (activeChatRoom != null) {
            activeChatRoom = null
        } else if (chatSubSegment != 0) {
            chatSubSegment = 0
        } else if (isSearchActive) {
            isSearchActive = false
            chatSearchQuery = ""
        } else {
            onDismiss()
        }
    }

    if (!viewModel.isCryptocontainerMounted) {
        CryptocontainerSetupWizard(viewModel = viewModel, onDismiss = onDismiss)
        return
    }

    if (viewModel.showSimulatedXftpDialog) {
        com.example.ui.SimulatedXftpDistributionDialog(
            onDismiss = { viewModel.showSimulatedXftpDialog = false }
        )
    }

    if (viewModel.showMentalDiceAuditDialog) {
        com.example.ui.MentalDiceAuditDialog(
            outcome = viewModel.lastDiceOutcome,
            engine = viewModel.mentalDiceEngine,
            lang = lang,
            onDismiss = { viewModel.showMentalDiceAuditDialog = false },
            onSimulatedRollApplied = { d1, d2 ->
                viewModel.applySimulatedConsensusRoll(d1, d2)
            }
        )
    }

    if (viewModel.isRadioFullScreenOpen) {
        com.example.ui.screens.radio.FuturisticCyberpunkRadioScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.isRadioFullScreenOpen = false }
        )
    }

    // Master Layout Box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF0F1216))
            .clickable(enabled = false) {} // block clickthrough
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- CANONICAL TELEGRAM-STYLE APP BAR ---
            // --- CANONICAL TELEGRAM / WHATSAPP STYLE TOP APP BAR ---
            if (activeChatRoom == null) {
                if (isSearchActive) {
                    // Search Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                chatSearchQuery = ""
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        OutlinedTextField(
                            value = chatSearchQuery,
                            onValueChange = { chatSearchQuery = it },
                            placeholder = { Text(if (lang == "RU") "Поиск чатов и сообщений..." else "Search chats...", fontSize = 14.sp, color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (chatSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { chatSearchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    // Clean Telegram/WhatsApp Main Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp).testTag("messenger_back_to_game_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (lang == "RU") "Назад к игре" else "Back to game",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (lang == "RU") "Чаты" else "Chats",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00FF66))
                                    )
                                    Text(
                                        text = if (lang == "RU") "E2EE Mesh • В сети" else "E2EE Mesh • Online",
                                        color = Color(0xFF00FF66),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Right actions: Search [🔍], Wallet [👛], Overflow Menu [ ⋮ ]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.isCryptoWalletFullScreenOpen = true },
                                modifier = Modifier.size(36.dp).testTag("open_crypto_wallet_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Crypto Wallet",
                                    tint = Color(0xFF00FF66),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.size(36.dp).testTag("messenger_overflow_menu_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    modifier = Modifier
                                        .background(Color(0xFF1E262F))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "⚡ Киберпанк Радио // TG" else "⚡ Cyberpunk Radio // TG", color = Color(0xFF00FF66), fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.isRadioFullScreenOpen = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "⚙️ Настройки" else "⚙️ Settings", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showMessengerSettingsModal = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "👥 Контакты" else "👥 Contacts", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            showOverflowMenu = false
                                            chatSubSegment = 1
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "➕ Создать группу" else "➕ New Group", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showCreateGroupModal = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "📷 Сканировать QR" else "📷 Scan QR", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showCameraView = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "🔗 Мой QR / Ссылка" else "🔗 My QR / Link", color = Color.White, fontSize = 13.sp) },
                                        onClick = {
                                            showOverflowMenu = false
                                            chatSubSegment = 2
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "🔄 Синхронизировать сеть" else "🔄 Sync Network", color = Color(0xFF00FF66), fontSize = 13.sp) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.connectAndSyncAllNetworkComponents()
                                        }
                                    )
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
                                    DropdownMenuItem(
                                        text = { Text(if (lang == "RU") "🚪 Выйти в игру" else "🚪 Back to Game", color = Color(0xFFFF5252), fontSize = 13.sp) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Header inside active chat room
                val matchedRoom = viewModel.simplexRooms.firstOrNull { it.id == activeChatRoom }
                val isSavedMessages = activeChatRoom == "SAVED_MESSAGES"
                val roomTitle = if (isSavedMessages) {
                    if (lang == "RU") "🔖 Избранное" else "🔖 Saved Messages"
                } else {
                    matchedRoom?.title ?: when (activeChatRoom) {
                        "ECHO_BOT" -> if (lang == "RU") "🤖 Эхо-сервис SimpleX" else "🤖 SimpleX Echo Service"
                        "SIMPLEX_ANNOUNCEMENTS" -> "📢 SimpleX Network News"
                        else -> if (lang == "RU") "🔒 Защищенный чат" else "🔒 Secure Chat"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { activeChatRoom = null },
                            modifier = Modifier.size(36.dp).testTag("chat_room_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (lang == "RU") "Назад к чатам" else "Back to chats",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Compact Avatar Circle
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSavedMessages) Color(0xFF00FF66).copy(alpha = 0.2f)
                                    else Color(0xFF29B6F6).copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSavedMessages) "🔖" else roomTitle.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = roomTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isSavedMessages) {
                                    if (lang == "RU") "Личные зашифрованные заметки" else "Personal encrypted vault"
                                } else {
                                    if (lang == "RU") "в сети • 🔒 E2EE Direct" else "online • 🔒 E2EE Direct"
                                },
                                color = if (isSavedMessages) Color(0xFF00FF66) else Color.LightGray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Inside room action buttons: [📞 Call], [🔥 Burn Timer], [ ⋮ Menu]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!isSavedMessages) {
                            IconButton(
                                onClick = {
                                    val callTarget = matchedRoom?.title ?: "Peer"
                                    viewModel.startWebRtcCall(callTarget)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF00FF66), modifier = Modifier.size(18.dp))
                            }
                        }

                        // Quick burn timer switcher 🔥 directly in top bar
                        val currentTimer = matchedRoom?.selfDestructTimerSec ?: 0
                        IconButton(
                            onClick = {
                                if (matchedRoom != null) {
                                    matchedRoom.selfDestructTimerSec = when (currentTimer) {
                                        0 -> 10
                                        10 -> 30
                                        30 -> 60
                                        60 -> 300
                                        else -> 0
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Burn Timer",
                                    tint = if (currentTimer > 0) Color(0xFFFF5252) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (currentTimer > 0) {
                                    Text(
                                        text = "${currentTimer}s",
                                        color = Color(0xFFFF5252),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Room overflow menu
                        Box {
                            IconButton(
                                onClick = { showRoomOverflowMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            DropdownMenu(
                                expanded = showRoomOverflowMenu,
                                onDismissRequest = { showRoomOverflowMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E262F))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (lang == "RU") "🧹 Очистить историю" else "🧹 Clear history", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showRoomOverflowMenu = false
                                        matchedRoom?.messages?.clear()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (lang == "RU") "🔒 Сведения о ключе" else "🔒 Key verification", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showRoomOverflowMenu = false
                                        showMessengerSettingsModal = true
                                    }
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
                                DropdownMenuItem(
                                    text = { Text(if (lang == "RU") "🚪 Закрыть чат" else "🚪 Close chat", color = Color.LightGray, fontSize = 13.sp) },
                                    onClick = {
                                        showRoomOverflowMenu = false
                                        activeChatRoom = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Main screen displays canonical Telegram chat list directly

            // --- MAIN VIEWPORT BODY ---
            if (activeChatRoom != null) {
                // --- ACTIVE CHAT WINDOW VIEW ---
                val currentRoom = activeChatRoom!!
                val roomObj = viewModel.simplexRooms.firstOrNull { it.id == currentRoom }
                val messagesList = roomObj?.messages ?: remember { mutableStateListOf() }
                val chatListState = rememberLazyListState()
                var contextMenuTargetMessage by remember { mutableStateOf<GameViewModel.ChatMessage?>(null) }
                var showAttachmentBottomSheet by remember { mutableStateOf(false) }
                var textInputMsg by remember { mutableStateOf("") }
                var cursorOn by remember { mutableStateOf(true) }
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C0F12))
                        .padding(10.dp)
                ) {
                    // Messages List Pane

                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(530)
                            cursorOn = !cursorOn
                        }
                    }

                    // Enforce absolute offline software keyboard suppression
                    LaunchedEffect(textInputMsg, activeChatRoom) {
                        keyboardController?.hide()
                    }

                    // Automatic scroll to bottom on new incoming/sent message
                    LaunchedEffect(messagesList.size) {
                        if (messagesList.isNotEmpty()) {
                            chatListState.animateScrollToItem(messagesList.size - 1)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (messagesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (lang == "RU") "Нет сообщений. Начните защищенную сессию." else "No messages. Start a secure session.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                state = chatListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(messagesList, key = { it.id }) { m ->
                                    val isMe = m.sender == viewModel.simplexUserHandle
                                    MessageBubble(
                                        message = m,
                                        isMe = isMe,
                                        lang = lang,
                                        isSelected = contextMenuTargetMessage?.id == m.id,
                                        onLongClick = {
                                            contextMenuTargetMessage = m
                                        },
                                        onClick = {
                                            if (contextMenuTargetMessage != null) {
                                                contextMenuTargetMessage = null
                                            }
                                        },
                                        onReactionClick = { emoji ->
                                            viewModel.addSimpleXReaction(currentRoom, m.id, emoji)
                                        }
                                    )
                                }
                            }
                        }

                        // Telegram-стиль: Плавающая кнопка быстрого скролла вниз [↓]
                        val showScrollBottom = derivedStateOf {
                            val total = chatListState.layoutInfo.totalItemsCount
                            val lastVisible = chatListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            total > 5 && lastVisible < total - 2
                        }
                        ScrollToBottomFab(
                            visible = showScrollBottom.value,
                            onClick = {
                                coroutineScope.launch {
                                    if (messagesList.isNotEmpty()) {
                                        chatListState.animateScrollToItem(messagesList.size - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp)
                        )
                    }

                    // 2. Эргономичная нижняя строка ввода сообщений (Telegram/WhatsApp стиль)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF13181E))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка [📎] вызова всплывающей шторки вложений (ModalBottomSheet)
                        IconButton(
                            onClick = { showAttachmentBottomSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B232D))
                                .border(1.dp, Color(0xFF00FF41).copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attachments",
                                tint = Color(0xFF00FF41),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Текстовое поле ввода сообщения с индикатором защищенного шифрования
                        val inputScrollState = androidx.compose.foundation.rememberScrollState()
                        LaunchedEffect(textInputMsg) {
                            inputScrollState.animateScrollTo(inputScrollState.maxValue)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp, max = 80.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF080B0E))
                                .border(1.dp, Color(0xFF00FF41).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(inputScrollState)
                                ) {
                                    if (textInputMsg.isEmpty()) {
                                        Text(
                                            text = if (lang == "RU") "E2EE сообщение..." else "E2EE message...",
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF00FF41).copy(alpha = 0.45f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    } else {
                                        Text(
                                            text = textInputMsg + if (cursorOn) "█" else "",
                                            color = Color(0xFF00FF41),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "E2EE Key",
                                    tint = Color(0xFF00FF41).copy(alpha = 0.6f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Кнопка [➤] отправки сообщения
                        IconButton(
                            onClick = {
                                if (textInputMsg.isNotBlank()) {
                                    viewModel.sendSimpleXMessage(currentRoom, textInputMsg)
                                    textInputMsg = ""
                                }
                            },
                            enabled = textInputMsg.isNotBlank(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (textInputMsg.isNotBlank()) Color(0xFF00FF41) else Color(0xFF1B232D))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (textInputMsg.isNotBlank()) Color.Black else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 3. Встроенная защищенная матричная клавиатура (MatrixStyleKeyboard)
                    com.example.ui.components.MatrixStyleKeyboard(
                        onKey = { key ->
                            when (key) {
                                is com.example.ui.components.MatrixKey.CharKey -> {
                                    textInputMsg += key.text
                                }
                                com.example.ui.components.MatrixKey.Space -> {
                                    textInputMsg += " "
                                }
                                com.example.ui.components.MatrixKey.Backspace -> {
                                    if (textInputMsg.isNotEmpty()) {
                                        textInputMsg = textInputMsg.dropLast(1)
                                    }
                                }
                                com.example.ui.components.MatrixKey.Enter -> {
                                    if (textInputMsg.isNotBlank()) {
                                        viewModel.sendSimpleXMessage(currentRoom, textInputMsg)
                                        textInputMsg = ""
                                    }
                                }
                                com.example.ui.components.MatrixKey.Hide -> {
                                    keyboardController?.hide()
                                }
                                else -> {}
                            }
                        },
                        lang = lang
                    )
                }

                // 4. Всплывающее меню реакций и действий по долгому тапу
                contextMenuTargetMessage?.let { targetMsg ->
                    MessageContextMenu(
                        message = targetMsg,
                        currentRoomId = currentRoom,
                        viewModel = viewModel,
                        lang = lang,
                        onDismiss = { contextMenuTargetMessage = null }
                    )
                }

                // 5. Всплывающая нижняя шторка вложений (AttachmentBottomSheet)
                if (showAttachmentBottomSheet) {
                    AttachmentBottomSheet(
                        currentRoomId = currentRoom,
                        viewModel = viewModel,
                        lang = lang,
                        onDismiss = { showAttachmentBottomSheet = false }
                    )
                }
            } else {
                // --- CHAT LIST TAB OR NETWORKS CONFIG PANE ---
                if (activeTabSegment == 0) {
                    // If in sub-screens (Contacts, QR, Join, Relays), display sleek Telegram-style sub-header with Back button
                    if (chatSubSegment > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF161E26))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { chatSubSegment = 0 },
                                modifier = Modifier.size(36.dp).testTag("subsegment_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (lang == "RU") "Назад к чатам" else "Back to chats",
                                    tint = Color(0xFF00FF66),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (chatSubSegment) {
                                    1 -> if (lang == "RU") "👥 Контакты" else "👥 Contacts"
                                    2 -> if (lang == "RU") "🔗 Мой QR-код и инвайт" else "🔗 My QR & Invite"
                                    3 -> if (lang == "RU") "📥 Войти по ссылке" else "📥 Join via Link"
                                    4 -> if (lang == "RU") "⚙️ Серверы реле SimpleX" else "⚙️ SimpleX Relays"
                                    else -> ""
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    if (chatSubSegment == 0) {
                        // --- SUB-TAB 0: FULLSCREEN TELEGRAM / WHATSAPP CHAT LIST ---
                        var selectedRoomFilter by remember { mutableStateOf("ALL") }
                        val query = chatSearchQuery.trim().lowercase()
                        val filteredRooms = viewModel.simplexRooms.filter { room ->
                            val matchesFilter = selectedRoomFilter == "ALL" || room.roomType == selectedRoomFilter
                            val matchesSearch = query.isEmpty() ||
                                room.title.lowercase().contains(query) ||
                                room.lastMessage.lowercase().contains(query)
                            matchesFilter && matchesSearch
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0C0F12))
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Category Filter Chips Row (Telegram folder style)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF13181C))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "ALL" to (if (lang == "RU") "Все" else "All"),
                                        "DIRECT_CHAT" to (if (lang == "RU") "👤 Личные" else "👤 Direct"),
                                        "GROUP_CHAT" to (if (lang == "RU") "👥 Группы" else "👥 Groups"),
                                        "INFO_CHANNEL" to (if (lang == "RU") "📢 Каналы" else "📢 Channels")
                                    ).forEach { (filterVal, filterTitle) ->
                                        val isSel = selectedRoomFilter == filterVal
                                        Box(
                                            modifier = Modifier
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSel) Color(0xFF00FF66).copy(alpha = 0.16f) else Color(0xFF1E252D))
                                                .border(1.dp, if (isSel) Color(0xFF00FF66) else Color.Transparent, RoundedCornerShape(14.dp))
                                                .clickable { selectedRoomFilter = filterVal }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = filterTitle,
                                                color = if (isSel) Color(0xFF00FF66) else Color.LightGray,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                if (filteredRooms.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = if (query.isNotEmpty()) {
                                                    if (lang == "RU") "Ничего не найдено по запросу \"$chatSearchQuery\"" else "No chats matching \"$chatSearchQuery\""
                                                } else {
                                                    if (lang == "RU") "Нет каналов выбранного типа." else "No channels of selected type."
                                                },
                                                color = Color.Gray,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = { showFabActionModal = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2329)),
                                                border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00FF66), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (lang == "RU") "СОЗДАТЬ ЧАТ ИЛИ ГРУППУ" else "START CHAT OR GROUP",
                                                    color = Color(0xFF00FF66),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp)
                                    ) {
                                        items(filteredRooms, key = { it.id }) { room ->
                                            val id = room.id
                                            val title = room.title
                                            val lastMsg = room.lastMessage
                                            val isSaved = id == "SAVED_MESSAGES"
                                            val isEcho = id == "ECHO_BOT" || id == "BOT_CHAT"

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { activeChatRoom = id }
                                                    .testTag("chat_room_card_$id")
                                                    .background(if (isSaved) Color(0xFF142433).copy(alpha = 0.4f) else Color.Transparent)
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(46.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                when {
                                                                    isSaved -> Color(0xFF00B0FF)
                                                                    isEcho -> Color(0xFF00E676)
                                                                    room.roomType == "INFO_CHANNEL" -> Color(0xFF29B6F6)
                                                                    room.roomType == "GROUP_CHAT" -> Color(0xFFAB47BC)
                                                                    room.isOneTime -> Color(0xFFFFB300)
                                                                    else -> Color(0xFF37474F)
                                                                }
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSaved) {
                                                            Text("🔖", fontSize = 20.sp)
                                                        } else if (isEcho) {
                                                            Text("🤖", fontSize = 20.sp)
                                                        } else {
                                                            Text(
                                                                title.filter { it.isLetter() }.take(2).uppercase(),
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }

                                                        // Online green dot
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF00FF66))
                                                                .border(1.5.dp, Color(0xFF0C0F12), CircleShape)
                                                                .align(Alignment.BottomEnd)
                                                        )
                                                    }

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = if (isSaved) (if (lang == "RU") "Избранное" else "Saved Messages") else title,
                                                                color = Color.White,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = when {
                                                                    isSaved -> if (lang == "RU") "ХРАНИЛИЩЕ 🔖" else "VAULT 🔖"
                                                                    room.roomType == "INFO_CHANNEL" -> if (lang == "RU") "КАНАЛ 📢" else "CHANNEL 📢"
                                                                    room.roomType == "GROUP_CHAT" -> if (lang == "RU") "ГРУППА 👥" else "GROUP 👥"
                                                                    room.isOneTime -> "1-TIME 🔒"
                                                                    else -> "DIRECT 👤"
                                                                },
                                                                color = when {
                                                                    isSaved -> Color(0xFF00B0FF)
                                                                    room.roomType == "INFO_CHANNEL" -> Color(0xFF29B6F6)
                                                                    room.roomType == "GROUP_CHAT" -> Color(0xFFAB47BC)
                                                                    room.isOneTime -> Color(0xFFFFB300)
                                                                    else -> Color(0xFF00E676)
                                                                },
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = if (isSaved && lastMsg.isBlank()) {
                                                                if (lang == "RU") "Личное зашифрованное хранилище" else "Personal encrypted vault"
                                                            } else if (lastMsg.isBlank()) {
                                                                if (lang == "RU") "Нет сообщений" else "No messages"
                                                            } else {
                                                                lastMsg
                                                            },
                                                            color = if (isSaved) Color(0xFF81D4FA) else Color(0xFF8E99A2),
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                HorizontalDivider(
                                                    color = Color.White.copy(alpha = 0.05f),
                                                    thickness = 0.5.dp,
                                                    modifier = Modifier.padding(start = 58.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Telegram Style FAB [✏️] at BottomEnd
                            FloatingActionButton(
                                onClick = { showFabActionModal = true },
                                containerColor = Color(0xFF00FF66),
                                contentColor = Color.Black,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .testTag("messenger_fab_new_chat")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = if (lang == "RU") "Новый диалог" else "New message",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else if (chatSubSegment == 1) {
                    // --- SUB-TAB 1: CONTACTS LIST & QUICK CHALLENGE ---
                    var contactNameInput by remember { mutableStateOf("") }
                    var contactHandleInput by remember { mutableStateOf("") }
                    var contactIsAnonInput by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "👥 СПИСОК КОНТАКТОВ (SimpleX Address Book)" else "👥 CONTACT LIST (SimpleX Address Book)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Add new contact compact form
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (lang == "RU") "🆕 Добавить новый контакт" else "🆕 Add New Contact",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = contactNameInput,
                                        onValueChange = { contactNameInput = it },
                                        label = { Text(if (lang == "RU") "Имя" else "Name", fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.sp)
                                    )

                                    OutlinedTextField(
                                        value = contactHandleInput,
                                        onValueChange = { contactHandleInput = it },
                                        label = { Text("SimpleX ID", fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.sp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Checkbox(
                                            checked = contactIsAnonInput,
                                            onCheckedChange = { contactIsAnonInput = it }
                                        )
                                        Text(
                                            text = if (lang == "RU") "Анонимный" else "Anonymous",
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (contactNameInput.isNotBlank() && contactHandleInput.isNotBlank()) {
                                                viewModel.addContact(contactNameInput, contactHandleInput, contactIsAnonInput)
                                                contactNameInput = ""
                                                contactHandleInput = ""
                                                contactIsAnonInput = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "Добавить" else "Add", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Render existing contacts in simpleXContacts
                        viewModel.simplexContacts.forEach { contact ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
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
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (contact.isOnline) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = contact.name.take(2).uppercase(),
                                                    color = if (contact.isOnline) Color(0xFF00E676) else Color.LightGray,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = contact.name,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (contact.isAnonymous) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = if (lang == "RU") "АНОНИМ" else "ANON",
                                                                color = Color.Red,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "ID: ${contact.handle}",
                                                    color = Color.Gray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "E2EE",
                                                    tint = Color(0xFF00E676),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = "SMP E2EE",
                                                    color = Color(0xFF00E676),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (contact.isOnline) Color(0xFF00E676) else Color.Gray)
                                                )
                                                Text(
                                                    text = if (contact.isOnline) (if (lang == "RU") "В сети" else "Online") else (if (lang == "RU") "Не в сети" else "Offline"),
                                                    color = if (contact.isOnline) Color(0xFF00E676) else Color.Gray,
                                                    fontSize = 8.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Action buttons row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. Write Chat Button
                                        Button(
                                            onClick = {
                                                val exists = viewModel.simplexRooms.any { it.id == contact.handle }
                                                if (!exists) {
                                                    viewModel.simplexRooms.add(
                                                        com.example.ui.GameViewModel.SimpleXRoom(
                                                            id = contact.handle,
                                                            title = "👥 Chat: ${contact.name}",
                                                            lastMessage = if (lang == "RU") "Соединение установлено" else "Connection established",
                                                            simplexUrl = "smp://${contact.handle}"
                                                        )
                                                    )
                                                }
                                                activeChatRoom = contact.handle
                                            },
                                            modifier = Modifier.weight(1f).height(30.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161E26)),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(15.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = null,
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (lang == "RU") "Написать" else "Chat", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }

                                        // 2. Encrypted Voice Call Button
                                        Button(
                                            onClick = {
                                                viewModel.startWebRtcCall(contact.name)
                                            },
                                            modifier = Modifier.weight(1f).height(30.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D533A)),
                                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(15.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = null,
                                                tint = Color(0xFF00FF66),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (lang == "RU") "Позвонить" else "Call", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (chatSubSegment == 2) {
                    // --- SUB-TAB 2: CREATE INVITATION ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (lang == "RU") "🔗 ГЕНЕРАТОР ПРИГЛАШЕНИЙ" else "🔗 INVITATION GENERATOR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                            
                            var selectedInviteType by remember { mutableStateOf("ONE_TIME") }
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(if (lang == "RU") "Выберите тип соединения:" else "Select connection type:", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    
                                    val options = listOf(
                                        Triple("ONE_TIME", if (lang == "RU") "Одноразовая ссылка" else "One-time secure link", if (lang == "RU") "Идеально для анонимного подключения. Удаляется после активации." else "Ideal for anonymous 1-on-1 connection. Deleted after activation."),
                                        Triple("LONG_TERM", if (lang == "RU") "Долгосрочный адрес контакта" else "Long-term contact address", if (lang == "RU") "Позволяет нескольким игрокам слать запросы на контакт." else "Allows multiple users to send contact requests."),
                                        Triple("GROUP", if (lang == "RU") "Групповой секретный канал" else "Group secret channel", if (lang == "RU") "Для создания защищенных чатов на несколько человек." else "For creating secure chats with multiple people."),
                                        Triple("CHANNEL", if (lang == "RU") "Информационный новостной канал" else "Info News Channel", if (lang == "RU") "Канал вещания, где писать новости может только автор." else "Broadcast feed channel where only the owner can post updates.")
                                    )
                                    
                                    options.forEach { (type, title, desc) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selectedInviteType == type) Color(0xFF00E676).copy(alpha = 0.08f) else Color.Transparent)
                                                .border(1.dp, if (selectedInviteType == type) Color(0xFF00E676).copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .clickable { selectedInviteType = type }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            RadioButton(
                                                selected = selectedInviteType == type,
                                                onClick = { selectedInviteType = type },
                                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E676))
                                            )
                                            Column {
                                                Text(title, color = if (selectedInviteType == type) Color(0xFF00E676) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(desc, color = Color.LightGray, fontSize = 10.sp, lineHeight = 12.sp)
                                            }
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            viewModel.generateSimpleXInvitation(selectedInviteType)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "СГЕНЕРИРОВАТЬ ПРИГЛАШЕНИЕ ⚙️" else "GENERATE INVITATION ⚙️", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            if (viewModel.generatedInvitationLink.isNotEmpty()) {
                                // Render QR and Link block
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                                    border = BorderStroke(1.5.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = when(viewModel.generatedInvitationType) {
                                                "ONE_TIME" -> if (lang == "RU") "🔒 ОДНОРАЗОВЫЙ КЛЮЧ ГОТОВ" else "🔒 ONE-TIME KEY SECURED"
                                                "LONG_TERM" -> if (lang == "RU") "📡 ДОЛГОСРОЧНЫЙ АДРЕС СОЗДАН" else "📡 LONG-TERM ADDRESS CREATED"
                                                else -> if (lang == "RU") "📢 ССЫЛКА НА ГРУППУ ГОТОВА" else "📢 GROUP INVITATION LINK READY"
                                            },
                                            color = Color(0xFF00E676),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        // CANVAS QR CODE
                                        val matrix = viewModel.generatedInvitationQrMatrix
                                        if (matrix != null) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.White, RoundedCornerShape(12.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Canvas(
                                                    modifier = Modifier.size(130.dp)
                                                ) {
                                                    val sizeInUnits = matrix.size
                                                    val blockSizeX = size.width / sizeInUnits
                                                    val blockSizeY = size.height / sizeInUnits
                                                    
                                                    val exclusionStart = (sizeInUnits * 0.35f).toInt()
                                                    val exclusionEnd = (sizeInUnits * 0.65f).toInt()

                                                    for (r in matrix.indices) {
                                                        for (c in matrix[r].indices) {
                                                            val inExclusionZone = r in exclusionStart..exclusionEnd && c in exclusionStart..exclusionEnd
                                                            if (matrix[r][c] && !inExclusionZone) {
                                                                drawRect(
                                                                    color = Color.Black,
                                                                    topLeft = Offset(c * blockSizeX, r * blockSizeY),
                                                                    size = Size(blockSizeX + 0.5f, blockSizeY + 0.5f)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Draw ParanoidX Logo Overlay:
                                                    val logoPercent = 0.34f
                                                    val logoSize = size.width * logoPercent
                                                    val logoLeft = (size.width - logoSize) / 2f
                                                    val logoTop = (size.height - logoSize) / 2f
                                                    
                                                    drawRoundRect(
                                                        color = Color(0xFF10151B),
                                                        topLeft = Offset(logoLeft, logoTop),
                                                        size = Size(logoSize, logoSize),
                                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(logoSize * 0.25f)
                                                    )
                                                    
                                                    drawRoundRect(
                                                        color = Color(0xFF00E676),
                                                        topLeft = Offset(logoLeft, logoTop),
                                                        size = Size(logoSize, logoSize),
                                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(logoSize * 0.25f),
                                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density)
                                                     )
                                                     
                                                    val pStartX = logoLeft + logoSize * 0.26f
                                                    val pEndX = logoLeft + logoSize * 0.44f
                                                    val pTopY = logoTop + logoSize * 0.30f
                                                    val pMiddleY = logoTop + logoSize * 0.52f
                                                    val pBottomY = logoTop + logoSize * 0.72f
                                                    
                                                    drawLine(
                                                        color = Color.White,
                                                        start = Offset(pStartX, pTopY),
                                                        end = Offset(pStartX, pBottomY),
                                                        strokeWidth = 2f * density,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                    drawLine(
                                                        color = Color.White,
                                                        start = Offset(pStartX, pTopY),
                                                        end = Offset(pEndX, pTopY),
                                                        strokeWidth = 2f * density,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                    drawLine(
                                                        color = Color.White,
                                                        start = Offset(pEndX, pTopY),
                                                        end = Offset(pEndX, pMiddleY),
                                                        strokeWidth = 2f * density,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                    drawLine(
                                                        color = Color.White,
                                                        start = Offset(pEndX, pMiddleY),
                                                        end = Offset(pStartX, pMiddleY),
                                                        strokeWidth = 2f * density,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                    
                                                    val xStartX = logoLeft + logoSize * 0.52f
                                                    val xEndX = logoLeft + logoSize * 0.76f
                                                    val xTopY = logoTop + logoSize * 0.32f
                                                    val xBottomY = logoTop + logoSize * 0.70f
                                                    
                                                    drawLine(
                                                        color = Color(0xFF00E676),
                                                        start = Offset(xStartX, xTopY),
                                                        end = Offset(xEndX, xBottomY),
                                                        strokeWidth = 2.5f * density,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                    drawLine(
                                                        color = Color(0xFF00E676),
                                                        start = Offset(xEndX, xTopY),
                                                        end = Offset(xStartX, xBottomY),
                                                        strokeWidth = 2.5f * density,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Invitation URI:", fontSize = 10.sp, color = Color.Gray)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = viewModel.generatedInvitationLink,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF00FF99),
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("SimpleX Invitation URI", viewModel.generatedInvitationLink)
                                                        clipboard.setPrimaryClip(clip)
                                                        cameraScanResult = if (lang == "RU") "Ссылка успешно скопирована!" else "Link successfully copied!"
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = null,
                                                        tint = Color(0xFF00E676),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Text(
                                            text = if (lang == "RU") "Передайте её контакту безопасным способом. По соображениям приватности, метаданные соединения не регистрируются." else "Share it with your contact securely. For privacy reasons, connection metadata is not registered.",
                                            color = Color.LightGray,
                                            fontSize = 9.5.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else if (chatSubSegment == 3) {
                        // --- SUB-TAB 3: ESTABLISH CONNECTION (JOIN) ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "📥 УСТАНОВЛЕНИЕ СОЕДИНЕНИЯ ПО QR И ССЫЛКАМ" else "📥 ESTABLISH CONNECTION VIA QR & LINKS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(if (lang == "RU") "Вставьте пригласительную ссылку:" else "Paste invitation link:", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    
                                    OutlinedTextField(
                                        value = viewModel.invitationInputText,
                                        onValueChange = { viewModel.invitationInputText = it },
                                        readOnly = viewModel.isNoGameModeEnabled,
                                        singleLine = false,
                                        maxLines = 6,
                                        placeholder = { Text("simplex://contact#id=... или https://...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray,
                                            focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "invitation") Color(0xFF00FF41) else Color(0xFF00E676),
                                            unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "invitation") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clickable {
                                                if (viewModel.isNoGameModeEnabled) focusedFieldId = "invitation"
                                            }
                                    )
                                    
                                    Button(
                                        onClick = {
                                            viewModel.connectToSimpleXInvitation(viewModel.invitationInputText) { msg ->
                                                cameraScanResult = msg
                                                viewModel.invitationInputText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "🔗 ПОДКЛЮЧИТЬСЯ ПО ССЫЛКЕ" else "🔗 CONNECT VIA LINK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            // QR CODE INSTANT LIVE SCANNER
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(if (lang == "RU") "📸 Сканирование QR-кода приглашения" else "📸 Scan Invitation QR Code", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(if (lang == "RU") "Наведите камеру на QR SimpleX контакта или группы" else "Point camera at SimpleX contact or group QR", fontSize = 10.sp, color = Color.LightGray)
                                        }
                                        
                                        Button(
                                            onClick = {
                                                showCameraView = !showCameraView
                                                isCameraScanning = showCameraView
                                                cameraScanResult = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (showCameraView) Color(0xFF991B1B) else Color(0xFF1F262C)),
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp)
                                        ) {
                                            Text(if (showCameraView) (if (lang == "RU") "ЗАКРЫТЬ" else "CLOSE") else (if (lang == "RU") "ОТКРЫТЬ КАМЕРУ" else "OPEN CAMERA"), fontSize = 9.sp, color = Color.White)
                                        }
                                    }
                                    
                                    if (showCameraView) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(260.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black)
                                                .border(1.5.dp, Color(0xFF00E676), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCameraScanning) {
                                                QrCodeScannerView(
                                                    onQrScanned = { activeUrl ->
                                                        viewModel.connectToSimpleXInvitation(activeUrl) { msg ->
                                                            cameraScanResult = msg
                                                        }
                                                        isCameraScanning = false
                                                        showCameraView = false
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                    
                                    Text(if (lang == "RU") "Официальные каналы и сервисы SimpleX:" else "Official SimpleX channels & services:", fontSize = 10.sp, color = Color.Gray)
                                    val presets = listOf(
                                        Triple(
                                            "SimpleX Network News (SMP)",
                                            "https://simplex.chat/contact#/?v=1-4&smp=smp%3A%2F%2Fsmp10.simplex.im%2Fnews&name=SimpleX%20News",
                                            if (lang == "RU") "Официальный канал новостей сети SimpleX" else "Official SimpleX news channel"
                                        ),
                                        Triple(
                                            "SimpleX Echo Service (SMP)",
                                            "https://simplex.chat/contact#/?v=1-4&smp=smp%3A%2F%2Fsmp10.simplex.im%2Fecho&name=Echo%20Bot",
                                            if (lang == "RU") "Эхо-сервис для проверки сквозного шифрования" else "Echo service to test end-to-end encryption"
                                        ),
                                        Triple(
                                            "SimpleX Community Group",
                                            "simplex://group#id=community_hub&title=SimpleX%20Community",
                                            if (lang == "RU") "Открытая децентрализованная группа сообщества" else "Public decentralized community group"
                                        )
                                    )
                                    
                                    presets.forEach { (title, inviteUrl, desc) ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.connectToSimpleXInvitation(inviteUrl) { msg ->
                                                        cameraScanResult = msg
                                                    }
                                                },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("🔗", fontSize = 12.sp)
                                                }
                                                Column {
                                                    Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(desc, color = Color.LightGray, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Result Toast/Note
                            if (cameraScanResult.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF09291D)),
                                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                        Text(cameraScanResult, color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (chatSubSegment == 4) {
                        // --- SUB-TAB 4: SMP / XFTP / TURN RELAY CONFIGURATION ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "⚙️ УПРАВЛЕНИЕ ДЕЦЕНТРАЛИЗОВАННЫМИ РЕЛЕЯМИ SIMPLEX" else "⚙️ MANAGE DECENTRALIZED SIMPLEX RELAYS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            // 1. SMP servers relay configuration
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (lang == "RU") "📡 Сервер SMP (Simple Messaged Protocol)" else "📡 SMP Server Relay",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (lang == "RU") "Используется для доставки текстовых сообщений между пирами." 
                                               else "Used for low-latency asynchronous text delivery between peers.",
                                        fontSize = 9.5.sp,
                                        color = Color.LightGray
                                    )
                                    
                                    OutlinedTextField(
                                        value = viewModel.customSmpServer,
                                        onValueChange = { viewModel.updateSmpServer(it) },
                                        readOnly = viewModel.isNoGameModeEnabled,
                                        singleLine = false,
                                        maxLines = 6,
                                        placeholder = { Text("smp://...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray,
                                            focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_smp") Color(0xFF00FF41) else Color(0xFF00E676),
                                            unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_smp") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clickable {
                                                if (viewModel.isNoGameModeEnabled) focusedFieldId = "custom_smp"
                                            }
                                    )
                                }
                            }

                            // 2. XFTP servers relay configuration
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (lang == "RU") "📁 Сервер XFTP (Encrypted File Transfer)" else "📁 XFTP File Streaming Relay",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (lang == "RU") "Обеспечивает передачу медиафайлов и голосовых сообщений." 
                                               else "Handles media file transfers and cryptocontainer voice recordings.",
                                        fontSize = 9.5.sp,
                                        color = Color.LightGray
                                    )
                                    
                                    OutlinedTextField(
                                        value = viewModel.customXftpServer,
                                        onValueChange = { viewModel.updateXftpServer(it) },
                                        readOnly = viewModel.isNoGameModeEnabled,
                                        singleLine = false,
                                        maxLines = 6,
                                        placeholder = { Text("xftp://...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray,
                                            focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_xftp") Color(0xFF00FF41) else Color(0xFF00E676),
                                            unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_xftp") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clickable {
                                                if (viewModel.isNoGameModeEnabled) focusedFieldId = "custom_xftp"
                                            }
                                    )
                                }
                            }

                            // 3. WebRTC signaling relays
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (lang == "RU") "🎥 TURN/STUN Релей (Голосовая и видеосвязь)" else "🎥 TURN/STUN Relay (Voice & Video)",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (lang == "RU") "Необходим для обхода NAT и будущей P2P голосовой и видеосвязи." 
                                               else "Required for NAT traversal to support future peer-to-peer voice/video calling.",
                                        fontSize = 9.5.sp,
                                        color = Color.LightGray
                                    )
                                    
                                    OutlinedTextField(
                                        value = viewModel.customTurnServer,
                                        onValueChange = { viewModel.updateTurnServer(it) },
                                        readOnly = viewModel.isNoGameModeEnabled,
                                        singleLine = false,
                                        maxLines = 6,
                                        placeholder = { Text("stun:...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray,
                                            focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_turn") Color(0xFF00FF41) else Color(0xFF00E676),
                                            unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_turn") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clickable {
                                                if (viewModel.isNoGameModeEnabled) focusedFieldId = "custom_turn"
                                            }
                                    )
                                }
                            }
                            
                            // Relay testing log block
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF06090D)),
                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (lang == "RU") "🖥️ КОНСОЛЬ РЕЛЕЕВ" else "🖥️ RELAY STATUS SYSTEM",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (lang == "RU") "ОК" else "OK",
                                                fontSize = 8.sp,
                                                color = Color(0xFF00E676),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (lang == "RU") {
                                            "> smp-resolver: Инициализирован узел ${viewModel.customSmpServer}\n" +
                                            "> xftp-engine: Связь активна [${viewModel.customXftpServer}]\n" +
                                            "> call-signaling: Туннель WebRTC TURN готов."
                                        } else {
                                            "> smp-resolver: Upstream link active [${viewModel.customSmpServer}]\n" +
                                            "> xftp-engine: Connected & pooling chunk feeds [${viewModel.customXftpServer}]\n" +
                                            "> call-signaling: WebRTC TURN stream pipe standing by."
                                        },
                                        fontSize = 8.5.sp,
                                        color = Color(0xFF00D254),
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // --- TAB 1: NETWORKS CONFIGURATION (REAL VPN, TOR & DYNAMIC ONIONS) ---
                    var activeNetworkSectionTab by remember { mutableStateOf(0) }
                    val networkTabs = listOf(
                        Triple(if (lang == "RU") "👤 Профиль" else "👤 Profile", "PROFILE", 0),
                        Triple(if (lang == "RU") "🛡️ Мост" else "🛡️ Bridge", "BRIDGE", 1),
                        Triple(if (lang == "RU") "📡 Onion" else "📡 Onion", "ONION", 2),
                        Triple(if (lang == "RU") "🦊 VPN" else "🦊 VPN", "VPN", 3),
                        Triple(if (lang == "RU") "🧅 Tor" else "🧅 Tor", "TOR", 4),
                        Triple(if (lang == "RU") "💾 Резерв" else "💾 Backup", "BACKUP", 5)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Horizontal Tab bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            networkTabs.forEach { (label, _, index) ->
                                val isSelected = activeNetworkSectionTab == index
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF13181C))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { activeNetworkSectionTab = index }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFF00E676) else Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Scrollable content area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (activeNetworkSectionTab == 0) {
                                // User Profile Handle Config
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (lang == "RU") "👤 Профиль сетевого клиента" else "👤 Network Client Profile", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                
                                OutlinedTextField(
                                    value = viewModel.simplexUserHandle,
                                    onValueChange = { viewModel.updateSimplexHandle(it) },
                                    label = { Text(if (lang == "RU") "Имя контакта (Handle)" else "Contact Name (Handle)", fontSize = 11.sp, color = Color.LightGray) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E676),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(if (lang == "RU") "Код приглашения Е2ЕЕ:" else "E2EE Invitation Code:", fontSize = 10.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("SimpleX URI", viewModel.simplexContactCode)
                                            clipboard.setPrimaryClip(clip)
                                            cameraScanResult = if (lang == "RU") "Код скопирован в буфер!" else "Code copied to clipboard!"
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = viewModel.simplexContactCode,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF00FF99),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = Language.get("connect_on_startup", lang),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = Language.get("connect_on_startup_desc", lang),
                                            color = Color.Gray,
                                            fontSize = 8.5.sp,
                                            lineHeight = 10.5.sp
                                        )
                                    }
                                    Switch(
                                        checked = viewModel.isConnectOnStartupEnabled,
                                        onCheckedChange = { viewModel.updateConnectOnStartup(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF00E676),
                                            checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.4f),
                                            uncheckedThumbColor = Color.LightGray,
                                            uncheckedTrackColor = Color.DarkGray
                                        )
                                    )
                                }

                                 Spacer(modifier = Modifier.height(6.dp))
                                 Row(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Column(modifier = Modifier.weight(1f)) {
                                         Text(
                                             text = if (lang == "RU") "Режим 'Без игры'" else "'No Game' Mode",
                                             color = Color.White,
                                             fontSize = 11.sp,
                                             fontWeight = FontWeight.Bold
                                         )
                                         Text(
                                             text = if (lang == "RU") "При запуске игра заменяется экраном ввода ПИН-кода." else "Bypasses the game and runs E2EE chat pin screen directly.",
                                             color = Color.Gray,
                                             fontSize = 8.5.sp,
                                             lineHeight = 10.5.sp
                                         )
                                     }
                                     Switch(
                                         checked = viewModel.isNoGameModeEnabled,
                                         onCheckedChange = { viewModel.updateNoGameMode(it) },
                                         colors = SwitchDefaults.colors(
                                             checkedThumbColor = Color(0xFF00E676),
                                             checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.4f),
                                             uncheckedThumbColor = Color.LightGray,
                                             uncheckedTrackColor = Color.DarkGray
                                         )
                                     )
                                 }

                                 Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
                                 Spacer(modifier = Modifier.height(8.dp))
                                 Column(modifier = Modifier.fillMaxWidth()) {
                                     Text(
                                         text = if (lang == "RU") "Локализация интерфейса чата" else if (lang == "ES") "Idioma del chat" else "Chat Interface Language",
                                         color = Color.White,
                                         fontSize = 11.sp,
                                         fontWeight = FontWeight.Bold
                                     )
                                     Spacer(modifier = Modifier.height(6.dp))
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.spacedBy(6.dp)
                                     ) {
                                         listOf("EN" to "English", "ES" to "Español", "RU" to "Русский").forEach { (code, label) ->
                                             val isSelected = viewModel.selectedChatLanguage == code
                                             Box(
                                                 modifier = Modifier
                                                     .weight(1f)
                                                     .height(30.dp)
                                                     .clip(RoundedCornerShape(6.dp))
                                                     .background(if (isSelected) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF1B2228))
                                                     .border(
                                                         width = 1.dp,
                                                         color = if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f),
                                                         shape = RoundedCornerShape(6.dp)
                                                     )
                                                     .clickable { viewModel.updateChatLanguage(code) },
                                                 contentAlignment = Alignment.Center
                                             ) {
                                                 Text(
                                                     text = label,
                                                     color = if (isSelected) Color(0xFF00E676) else Color.LightGray,
                                                     fontSize = 10.sp,
                                                     fontWeight = FontWeight.Bold
                                                 )
                                             }
                                         }
                                     }
                                 }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 1) {
                        // NEW MASTER SYSTEM SYNC COMPONENT (FOXRAY VPN -> TOR -> SMP NODE SECURE TUNNEL!)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161F25)),
                            border = BorderStroke(1.2.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (lang == "RU") "🛡️ Защищенный мост и синхронизация" else "🛡️ Secure Bridge & Sync", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                        Text(if (lang == "RU") "Автоматическая цепь VPN (V2Ray / Tor) + Сетевой узел" else "Automated chain VPN (V2Ray / Tor) + Network Node", fontSize = 10.sp, color = Color.LightGray)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF00FF66)
                                                else if (viewModel.v2RayTorSyncStatus == "SYNCING") Color(0xFFFFB300)
                                                else Color.Gray
                                            )
                                    )
                                }

                                if (viewModel.v2RayTorSyncStatus != "NOT_SYNCED") {
                                    Text(
                                        text = if (lang == "RU") "Статус соединения: ${viewModel.v2RayTorSyncStatus}" else "Connection status: ${viewModel.v2RayTorSyncStatus}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF00E676) else Color(0xFFFFB300)
                                    )
                                    
                                    // Custom Monospace sync terminal log screen
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .background(Color.Black, RoundedCornerShape(8.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(viewModel.v2RayTorSyncLogs) { item ->
                                                Text(item, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                            }
                                        }
                                    }
                                }

                                val localKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                                Button(
                                    onClick = {
                                        localKeyboard?.hide()
                                        viewModel.connectAndSyncAllNetworkComponents()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF0D533A) else Color(0xFF00E676)
                                    ),
                                    enabled = !viewModel.isV2RayTorSyncing,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (viewModel.isV2RayTorSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (lang == "RU") "Синхронизация сетей..." else "Synchronizing networks...", fontSize = 11.sp, color = Color.White)
                                    } else {
                                        Text(
                                            text = if (viewModel.v2RayTorSyncStatus == "SYNCED") {
                                                if (lang == "RU") "СЕТИ СИНХРОНИЗИРОВАНЫ RE-SYNC" else "NETWORKS SYNCED RE-SYNC"
                                            } else {
                                                if (lang == "RU") "ИНИЦИАЛИЗИРОВАТЬ ЗАЩИЩЕННЫЙ МОСТ" else "INITIALIZE SECURE BRIDGE"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF00E676) else Color.Black
                                        )
                                    }
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 2) {
                        // NEW DYNAMIC ONION RELAYS AND SERVER NODES WITH SCANNING & TESTING
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (lang == "RU") "📡 Настройка кастомных Onion-серверов" else "📡 Custom Onion Servers Configuration", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                
                                // SMP Node Configuration
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (lang == "RU") "SMP Onion сервер доступа:" else "SMP Onion Access Server:", fontSize = 10.sp, color = Color.LightGray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = viewModel.smpOnionAddress,
                                            onValueChange = { viewModel.updateSmpOnionAddress(it) },
                                            readOnly = viewModel.isNoGameModeEnabled,
                                            singleLine = false,
                                            maxLines = 6,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.LightGray,
                                                focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "smp_onion") Color(0xFF00FF41) else Color(0xFF00E676),
                                                unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "smp_onion") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(110.dp)
                                                .clickable {
                                                    if (viewModel.isNoGameModeEnabled) focusedFieldId = "smp_onion"
                                                }
                                        )

                                        Button(
                                            onClick = {
                                                scanningServerType = "SMP"
                                                showCameraView = true
                                                isCameraScanning = true
                                                cameraScanResult = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B32)),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                        ) {
                                            Text("QR", fontSize = 10.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                localKeyboard?.hide()
                                                viewModel.testOnionAddress("SMP", viewModel.smpOnionAddress) // SMP_TEST_UNIQ

                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3B46)),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text(if (lang == "RU") "ТЕСТ" else "TEST", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // SMP Metrics Indicators
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusColor = when (viewModel.smpStatusState) {
                                        "ONLINE" -> Color(0xFF00E676)
                                        "OFFLINE" -> Color(0xFFFF5252)
                                        else -> Color(0xFF9E9E9E)
                                    }
                                    val statusText = when (viewModel.smpStatusState) {
                                        "ONLINE" -> if (lang == "RU") "АКТИВЕН" else "ONLINE"
                                        "OFFLINE" -> if (lang == "RU") "ОТКЛЮЧЕН" else "OFFLINE"
                                        else -> if (lang == "RU") "НЕИЗВЕСТНО" else "UNKNOWN"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                                        Text(text = "SMP: $statusText", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (viewModel.smpPingResult > 0) {
                                        Text(
                                            text = "Ping: ${viewModel.smpPingResult}ms | Jitter: ${(-2..2).random()}ms",
                                            fontSize = 9.sp,
                                            color = if (viewModel.smpPingResult < 155) Color(0xFF00E676) else Color(0xFFFFB300),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // XFTP Node Configuration
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (lang == "RU") "XFTP Onion сервер передачи медиа:" else "XFTP Onion Media Relay Server:", fontSize = 10.sp, color = Color.LightGray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = viewModel.xftpOnionAddress,
                                            onValueChange = { viewModel.updateXftpOnionAddress(it) },
                                            readOnly = viewModel.isNoGameModeEnabled,
                                            singleLine = false,
                                            maxLines = 6,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.LightGray,
                                                focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "xftp_onion") Color(0xFF00FF41) else Color(0xFF00E676),
                                                unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "xftp_onion") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(110.dp)
                                                .clickable {
                                                    if (viewModel.isNoGameModeEnabled) focusedFieldId = "xftp_onion"
                                                }
                                        )

                                        Button(
                                            onClick = {
                                                scanningServerType = "XFTP"
                                                showCameraView = true
                                                isCameraScanning = true
                                                cameraScanResult = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B32)),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                        ) {
                                            Text("QR", fontSize = 10.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                localKeyboard?.hide()
                                                viewModel.testOnionAddress("XFTP", viewModel.xftpOnionAddress) // XFTP_TEST_UNIQ

                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3B46)),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text(if (lang == "RU") "ТЕСТ" else "TEST", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // XFTP Metrics Indicators
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusColor = when (viewModel.xftpStatusState) {
                                        "ONLINE" -> Color(0xFF00E676)
                                        "OFFLINE" -> Color(0xFFFF5252)
                                        else -> Color(0xFF9E9E9E)
                                    }
                                    val statusText = when (viewModel.xftpStatusState) {
                                        "ONLINE" -> if (lang == "RU") "АКТИВЕН" else "ONLINE"
                                        "OFFLINE" -> if (lang == "RU") "ОТКЛЮЧЕН" else "OFFLINE"
                                        else -> if (lang == "RU") "НЕИЗВЕСТНО" else "UNKNOWN"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                                        Text(text = "XFTP: $statusText", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (viewModel.xftpPingResult > 0) {
                                        Text(
                                            text = "Ping: ${viewModel.xftpPingResult}ms | Jitter: ${(-2..2).random()}ms",
                                            fontSize = 9.sp,
                                            color = if (viewModel.xftpPingResult < 155) Color(0xFF00E676) else Color(0xFFFFB300),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Interactive QR Scanner View
                                if (showCameraView) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                            .border(2.dp, Color(0xFF00E676), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCameraScanning) {
                                            QrCodeScannerView(
                                                onQrScanned = { scannedAddr ->
                                                    if (scanningServerType == "SMP") {
                                                        viewModel.updateSmpOnionAddress(scannedAddr)
                                                    } else {
                                                        viewModel.updateXftpOnionAddress(scannedAddr)
                                                    }
                                                    cameraScanResult = if (lang == "RU") "Адрес $scanningServerType успешно импортирован из QR!" else "Address $scanningServerType successfully imported from QR!"
                                                    isCameraScanning = false
                                                    showCameraView = false
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }

                                if (false) {
                                    if (isCameraScanning) {
                                            val inf = rememberInfiniteTransition()
                                            val lineY by inf.animateFloat(
                                                initialValue = 0.1f,
                                                targetValue = 0.9f,
                                                animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse)
                                            )
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(if (lang == "RU") "[ QR РАСПОЗНАВАНИЕ АКТИВНО ]" else "[ QR DETECTOR ACTIVE ]", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFF00E676).copy(alpha = 0.4f), modifier = Modifier.size(34.dp))
                                            }
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawLine(Color.Red, Offset(0f, size.height*lineY), Offset(size.width, size.height*lineY), 2.dp.toPx())
                                            }

                                            LaunchedEffect(Unit) {
                                                delay(1800)
                                                isCameraScanning = false
                                                showCameraView = false
                                                val scannedAddr = if (scanningServerType == "SMP") {
                                                    "smp://nzy8c9wq910.onion/crazy_nardy_smp_channel_invite"
                                                } else {
                                                    "xftp://f6h7p89k921.onion/crazy_nardy_xftp_relay_invite"
                                                }
                                                if (scanningServerType == "SMP") {
                                                    viewModel.updateSmpOnionAddress(scannedAddr)
                                                } else {
                                                    viewModel.updateXftpOnionAddress(scannedAddr)
                                                }
                                                cameraScanResult = if (lang == "RU") "Луковый адрес $scanningServerType успешно импортирован из QR!" else "Onion address $scanningServerType successfully imported from QR!"
                                            }
                                        }
                                    }

                                // Availability diagnostics logs
                                if (viewModel.isTestingOnionAddress || viewModel.onionTestLogs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(if (lang == "RU") "Диагностика сети:" else "Network Diagnostics:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp)
                                            .background(Color.Black, RoundedCornerShape(8.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(viewModel.onionTestLogs) { logLine ->
                                                Text(logLine, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }

                                if (cameraScanResult.isNotEmpty()) {
                                    Text(cameraScanResult, color = Color(0xFF00E676), fontSize = 11.sp)
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 3) {
                        // FOXRAY VPN Tunnel Configuration profiles
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (lang == "RU") "🦊 Модуль Foxray VPN" else "🦊 Foxray VPN Module", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                        Text(if (lang == "RU") "Туннели VLESS / ShadowSocks / Trojan" else "VLESS / ShadowSocks / Trojan Tunnels", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val isConnected = vpnManager.vpnState == "Connected"
                                        
                                        Button(
                                            onClick = {
                                                vpnManager.updateAllSubscriptions(
                                                    onProgress = { msg -> vpnFileAlert = msg },
                                                    onFinished = { count, status -> vpnFileAlert = status }
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier.height(30.dp),
                                            enabled = !vpnManager.isTestingSpeeds
                                        ) {
                                            Text(if (lang == "RU") "🔄 ПОДПИСКИ" else "🔄 SUBS", fontSize = 8.sp, color = Color.LightGray)
                                        }

                                        Button(
                                            onClick = {
                                                vpnManager.testAndRemoveDeadServers { removed, total ->
                                                    vpnFileAlert = if (lang == "RU") "Очистка завершена! Удалено недоступных: $removed. Осталось: $total." else "Cleaned $removed dead nodes. Remaining: $total."
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier.height(30.dp),
                                            enabled = !vpnManager.isTestingSpeeds
                                        ) {
                                            Text(if (lang == "RU") "🧹 ОЧИСТКА" else "🧹 CLEAN", fontSize = 8.sp, color = Color.LightGray)
                                        }

                                        // SPEED TEST BUTTON
                                        Button(
                                            onClick = {
                                                vpnManager.testAllConfigsAndConnectFastest { res ->
                                                    vpnFileAlert = res
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(30.dp),
                                            enabled = !vpnManager.isTestingSpeeds
                                        ) {
                                            if (vpnManager.isTestingSpeeds) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.Black, strokeWidth = 1.5.dp)
                                            } else {
                                                Text(if (lang == "RU") "⚡ БЫСТРЕЙШИЙ" else "⚡ SPEED", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (isConnected) {
                                                    vpnManager.stopVpnConnectionSimulation()
                                                } else {
                                                    val prepIntent = vpnManager.getVpnPrepareIntent()
                                                    if (prepIntent != null) {
                                                        vpnPrepareLauncher.launch(prepIntent)
                                                    } else {
                                                        vpnManager.startVpnConnectionSimulation()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isConnected) Color(0xFF00E676) else Color(0xFF222B32)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                text = if (isConnected) {
                                                    if (lang == "RU") "СЕТЬ: ОНЛАЙН" else "NET: ONLINE"
                                                } else {
                                                    if (lang == "RU") "ПОДКЛЮЧИТЬ" else "CONNECT"
                                                }, 
                                                fontSize = 10.sp, 
                                                fontWeight = FontWeight.Bold,
                                                color = if (isConnected) Color.Black else Color.White
                                            )
                                        }
                                    }
                                }

                                if (vpnManager.vpnState == "Connected") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("PING", fontSize = 8.sp, color = Color.LightGray)
                                            Text("${vpnManager.pingTime} ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                                        }
                                        Column {
                                            Text(if (lang == "RU") "СКОРОСТЬ ↓" else "SPEED ↓", fontSize = 8.sp, color = Color.LightGray)
                                            Text(String.format("%.1f kB/s", vpnManager.downloadSpeed), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Column {
                                            Text(if (lang == "RU") "TРАФИК RX/TX" else "TRAFFIC RX/TX", fontSize = 8.sp, color = Color.LightGray)
                                            val rM = vpnManager.totalBytesRx / 1024.0 / 1024.0
                                            val tM = vpnManager.totalBytesTx / 1024.0 / 1024.0
                                            Text(String.format("%.1f/%.1f MB", rM, tM), fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (lang == "RU") "Конфигурации серверов VPN:" else "VPN Server Configurations:", fontSize = 11.sp, color = Color.Gray)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { vpnConfigImportLauncher.launch(arrayOf("*/*")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(if (lang == "RU") "+ ФАЙЛ" else "+ FILE", fontSize = 9.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = { showVpnTextImportDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(if (lang == "RU") "+ ТЕКСТ / ССЫЛКА" else "+ TEXT / URL", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (vpnFileAlert.isNotEmpty()) {
                                    Text(vpnFileAlert, color = Color(0xFF00E676), fontSize = 11.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(vpnManager.configs) { conf ->
                                            val isSel = vpnManager.selectedConfig?.id == conf.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.12f) else Color.Transparent)
                                                    .clickable { vpnManager.selectConfig(conf) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1.3f)) {
                                                    Text(
                                                        text = "${conf.protocol} • ${conf.name}",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSel) Color(0xFF00E676) else Color.White
                                                    )
                                                    Text(conf.server, fontSize = 9.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    val p = vpnManager.configPings[conf.id]
                                                    if (p != null) {
                                                        Text(
                                                            text = when (p) {
                                                                -2 -> if (lang == "RU") "Тест..." else "Testing..."
                                                                -1 -> "TIMEOUT"
                                                                else -> "$p ms"
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when (p) {
                                                                -2 -> Color(0xFFFFB300)
                                                                -1 -> Color(0xFFD32F2F)
                                                                else -> Color(0xFF00E676)
                                                            }
                                                        )
                                                    }
                                                    if (isSel) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 4) {
                        // TOR onion routing logs
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(if (lang == "RU") "🧅 Маршрутизатор Tor daemon" else "🧅 Tor Daemon Router", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                        Text(if (lang == "RU") "Локальное SOCKS5 проксирование" else "Local SOCKS5 proxy routing", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Switch(
                                        checked = viewModel.isTorEnabled,
                                        onCheckedChange = { viewModel.setTorEnabledState(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF00E676),
                                            checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.3f)
                                        )
                                    )
                                }

                                if (viewModel.isTorEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(if (lang == "RU") "Статус службы:" else "Service status:", fontSize = 11.sp, color = Color.Gray)
                                        Text(
                                            text = viewModel.torStatus,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.torStatus == "ACTIVE") Color(0xFF00E676) else Color(0xFFFFA000)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp)
                                            .background(Color.Black, RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(viewModel.torLogsList) { arg ->
                                                Text(arg, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Tor P2P Direct Multiplayer Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            if (lang == "RU") "🔗 Децентрализованный P2P через Tor" else "🔗 Decentralized Tor P2P Multiplayer",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF00E676)
                                        )
                                        Text(
                                            if (lang == "RU") "Прямая игра в нарды и E2EE чат по адресам Onion без единого сервера!" else "Direct backgammon matching and E2EE chat over Onion addresses, entirely serverless!",
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )

                                        if (viewModel.torStatus != "ACTIVE") {
                                            Text(
                                                if (lang == "RU") "⚠️ Пожалуйста, включите службу Tor daemon выше, чтобы запустить скрытый сервис Onion." else "⚠️ Please enable Tor daemon service above to activate Onion Hidden Service.",
                                                color = Color(0xFFFFB300),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                            
                                            // Handle fetching local address
                                            LaunchedEffect(Unit) {
                                                if (viewModel.torP2POnionAddress == null) {
                                                    viewModel.startTorP2PHost()
                                                }
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(if (lang == "RU") "Ваш локальный адрес Onion:" else "Your local Onion address:", fontSize = 10.sp, color = Color.Gray)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = viewModel.torP2POnionAddress ?: (if (lang == "RU") "Генерация адреса..." else "Generating Onion address..."),
                                                        onValueChange = {},
                                                        readOnly = true,
                                                        singleLine = true,
                                                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.LightGray,
                                                            focusedBorderColor = Color(0xFF00E676),
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                                        ),
                                                        modifier = Modifier.weight(1f).height(48.dp)
                                                    )

                                                    Button(
                                                        onClick = {
                                                            viewModel.torP2POnionAddress?.let {
                                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it))
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B32)),
                                                        modifier = Modifier.height(40.dp)
                                                    ) {
                                                        Text(if (lang == "RU") "КОПИР" else "COPY", fontSize = 10.sp, color = Color.White)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            viewModel.regenerateOnionAddress()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF321B22)),
                                                        modifier = Modifier.height(40.dp)
                                                    ) {
                                                        Text(if (lang == "RU") "ОБНОВИТЬ" else "REFRESH", fontSize = 10.sp, color = Color(0xFFFF5252))
                                                    }
                                                }
                                            }

                                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                            if (viewModel.isTorP2PConnected) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        if (lang == "RU") "🟢 СОЕДИНЕНО С ПИРОМ" else "🟢 CONNECTED TO PEER",
                                                        color = Color(0xFF00E676),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                    
                                                    Button(
                                                        onClick = { viewModel.stopTorP2P() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                                        modifier = Modifier.height(36.dp)
                                                    ) {
                                                        Text(if (lang == "RU") "ОТКЛЮЧИТЬ" else "DISCONNECT", fontSize = 10.sp, color = Color.White)
                                                    }
                                                }
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    LaunchedEffect(Unit) {
                                                        viewModel.startLanDiscovery()
                                                    }

                                                    if (viewModel.discoveredNearbyRooms.isNotEmpty()) {
                                                        Text(if (lang == "RU") "📡 Найдены игры в локальной сети:" else "📡 Local matches discovered:", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                                        viewModel.discoveredNearbyRooms.forEach { (hostName, address) ->
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(Color(0xFF00E676).copy(alpha = 0.12f))
                                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column {
                                                                    Text(hostName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                    Text(address, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                                                                }
                                                                Button(
                                                                    onClick = {
                                                                        viewModel.torP2PRemoteAddressInput = address
                                                                        viewModel.connectToTorP2PHost(address)
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                                                                    modifier = Modifier.height(30.dp),
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                                    shape = RoundedCornerShape(6.dp)
                                                                ) {
                                                                    Text(if (lang == "RU") "Войти" else "Join", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Input remote address
                                                    Text(if (lang == "RU") "Адрес Onion вашего друга:" else "Your friend's Onion address:", fontSize = 10.sp, color = Color.Gray)
                                                    OutlinedTextField(
                                                        value = viewModel.torP2PRemoteAddressInput,
                                                        onValueChange = { viewModel.torP2PRemoteAddressInput = it },
                                                        placeholder = { Text("e.g. xxxxxxxx.onion", fontSize = 11.sp, color = Color.Gray) },
                                                        singleLine = true,
                                                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.LightGray,
                                                            focusedBorderColor = Color(0xFF00E676),
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                                    )

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { viewModel.startTorP2PHost() },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D533A)),
                                                            modifier = Modifier.weight(1f).height(40.dp)
                                                        ) {
                                                            Text(if (lang == "RU") "ЖДАТЬ ДРУГА" else "HOST SERVER", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                                        }

                                                        Button(
                                                            onClick = { 
                                                                if (viewModel.torP2PRemoteAddressInput.isNotBlank()) {
                                                                    viewModel.connectToTorP2PHost(viewModel.torP2PRemoteAddressInput)
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                            modifier = Modifier.weight(1f).height(40.dp)
                                                        ) {
                                                            Text(if (lang == "RU") "ПОДКЛЮЧИТЬСЯ" else "CONNECT TO FRIEND", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // --- P2P SIMULATION LAB & LATENCY TEST ---
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131822)),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.35f))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = if (lang == "RU") "🧪 ЛАБОРАТОРИЯ P2P & ТЕЛЕМЕТРИЯ" else "🧪 P2P LAB & TELEMETRY",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF00E676)
                                                        )
                                                        if (viewModel.isSimulatedP2PPeerActive) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFF00E676).copy(alpha = 0.2f))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "LIVE PEER",
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF00E676)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                         text = if (lang == "RU") "Тестирование двухсторонней синхронизации игры и Mental Dice ZKP:" else "Test 2-way game loop & Mental Dice ZKP:",
                                                         fontSize = 8.5.sp,
                                                         color = Color.LightGray
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Latency Buttons
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        listOf(20L to "20мс (LAN)", 150L to "150мс (SMP)", 500L to "500мс (Tor)").forEach { (lat, label) ->
                                                            val isSel = viewModel.simulatedP2PLatencyMs == lat
                                                            OutlinedButton(
                                                                onClick = { viewModel.simulatedP2PLatencyMs = lat },
                                                                modifier = Modifier.weight(1f).height(32.dp),
                                                                colors = ButtonDefaults.outlinedButtonColors(
                                                                    containerColor = if (isSel) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent,
                                                                    contentColor = if (isSel) Color(0xFF00E676) else Color.LightGray
                                                                ),
                                                                border = BorderStroke(1.dp, if (isSel) Color(0xFF00E676) else Color.White.copy(alpha = 0.15f)),
                                                                contentPadding = PaddingValues(0.dp)
                                                            ) {
                                                                Text(label, fontSize = 8.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    // Action Buttons: Start/Stop Sim & Export Certificate
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (!viewModel.isSimulatedP2PPeerActive) {
                                                            Button(
                                                                onClick = { viewModel.startSimulatedP2PPeerSession(viewModel.simulatedP2PLatencyMs) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                                                                modifier = Modifier.weight(1f).height(36.dp),
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Text(if (lang == "RU") "ТЕСТ P2P МАТЧА" else "START P2P TEST", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else {
                                                            Button(
                                                                onClick = { viewModel.stopSimulatedP2PPeerSession() },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                                                modifier = Modifier.weight(1f).height(36.dp),
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Text(if (lang == "RU") "ОСТАНОВИТЬ ТЕСТ" else "STOP TEST", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                            }
                                                        }

                                                        OutlinedButton(
                                                            onClick = { viewModel.exportCurrentMatchCertificatePdf() },
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD54F)),
                                                            border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.5f)),
                                                            modifier = Modifier.weight(1f).height(36.dp),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text(if (lang == "RU") "PDF + XFTP" else "PDF + XFTP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            // Real-Time P2P & Native Network Telemetry Card
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1419)),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.25f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = if (lang == "RU") "⚡ ТЕЛЕМЕТРИЯ P2P & JNI" else "⚡ P2P & JNI TELEMETRY",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF00E676)
                                                        )
                                                        Text(
                                                            text = if (viewModel.isSimulatedP2PPeerActive || viewModel.isTorP2PConnected) "🟢 ONLINE" else "⚪ STANDBY",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (viewModel.isSimulatedP2PPeerActive || viewModel.isTorP2PConnected) Color(0xFF00E676) else Color.Gray
                                                        )
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("RTT Ping: ${viewModel.p2pTelemetryRttMs} ms", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                                        Text("Packets: ${viewModel.p2pTelemetryPacketCount}", fontSize = 10.sp, color = Color(0xFFFFD54F), fontFamily = FontFamily.Monospace)
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Tun2Socks: " + if (com.example.service.NativeNetworkBridge.isTun2SocksAvailable) "JNI libtun2socks.so" else "User-Space Router",
                                                            fontSize = 9.sp,
                                                            color = if (com.example.service.NativeNetworkBridge.isTun2SocksAvailable) Color(0xFF00E676) else Color(0xFFFFB300),
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            text = "Xray: " + if (com.example.service.NativeNetworkBridge.isXrayAvailable) "JNI libxray.so" else "Embedded",
                                                            fontSize = 9.sp,
                                                            color = if (com.example.service.NativeNetworkBridge.isXrayAvailable) Color(0xFF00E676) else Color.Gray,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "ZKP Mental Dice: ${viewModel.mentalDiceHandshakeStatus}",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF81D4FA),
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            text = "Turn Seq: #${viewModel.p2pTurnSequence}",
                                                            fontSize = 9.sp,
                                                            color = Color.LightGray,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(if (lang == "RU") "Отладочные логи P2P соединения:" else "P2P connection transit logs:", fontSize = 9.sp, color = Color.Gray)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp)
                                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            ) {
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    items(viewModel.torP2PLogs) { log ->
                                                        Text(log, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 5) {
                        // BACKUP AND RESTORE CONFIGURATION SETTINGS
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (lang == "RU") "💾 Резервная копия настроек" else "💾 Backup & Restore Config",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF00E676)
                                )
                                Text(
                                    text = if (lang == "RU") {
                                        "Экспорт или импорт всех сетевых параметров, пин-кодов и учетных записей в зашифрованном JSON-формате."
                                    } else {
                                        "Export or import all network configurations, profile handles, and credentials as portable JSON."
                                    },
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            backupActionType = "EXPORT"
                                            showBackupPinVerifyDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "ЭКСПОРТ Настроек" else "EXPORT Config", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            backupActionType = "IMPORT"
                                            showBackupPinVerifyDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "ИМПОРТ Настроек" else "IMPORT Config", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        exportedSeedPhraseText = viewModel.currentSeedPhrase
                                        exportedContainerText = viewModel.exportCryptocontainerWithSeed(viewModel.currentSeedPhrase)
                                        showSaveCryptoKeyDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "RU") "КОНТЕЙНЕР: СИД-ФРАЗА & КЛЮЧ" else "VAULT: SEED & MASTER KEY", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.showSimulatedXftpDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880E4F)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color(0xFFFF80AB),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (lang == "RU") "ZK-ШАРДИНГ & XFTP ОНТОЛОГИЯ (SIM)" else "ZK-SHARDING & XFTP ONION (SIM)",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        }
                        }

                        // RESET TO DEFAULT/PANIC BUTTON
                        Button(
                            onClick = {
                                viewModel.triggerPanicReset()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == "RU") "ЭКСТРЕННЫЙ СБРОС И ОЧИСТКА" else "EMERGENCY PANIC RESET & CLEAR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVpnTextImportDialog) {
        var importInputText by remember { mutableStateOf("") }
        var isNetworkLoading by remember { mutableStateOf(false) }
        var importErrorAlert by remember { mutableStateOf("") }

        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val dismissKeyboard = {
            focusManager.clearFocus()
            keyboardController?.hide()
        }

        Dialog(onDismissRequest = { 
            if (!isNetworkLoading) {
                dismissKeyboard()
                showVpnTextImportDialog = false 
            }
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "Импорт VPN конфигурации" else "Import VPN Configuration",
                        color = Color(0xFF00E676),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = if (lang == "RU") {
                            "Вставьте vmess/vless/ss/trojan ссылки (по одной в строку), пакет из v2rayNG (base64) или ссылку на подписку (http/https):"
                        } else {
                            "Paste vmess/vless/ss/trojan URIs, v2rayNG backup/base64 block or download subscription link (http/https):"
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = { 
                            Text(
                                "vless://... или http://...", 
                                fontSize = 11.sp, 
                                color = Color.Gray
                            ) 
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        maxLines = 6
                    )

                    if (importErrorAlert.isNotEmpty()) {
                        Text(importErrorAlert, color = Color(0xFFFFB300), fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                dismissKeyboard()
                                showVpnTextImportDialog = false 
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f),
                            enabled = !isNetworkLoading
                        ) {
                            Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                val trimmed = importInputText.trim()
                                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                    isNetworkLoading = true
                                    importErrorAlert = if (lang == "RU") "Загрузка подписки..." else "Downloading subscription..."
                                    vpnManager.importSubscriptionFromUrl(trimmed) { res ->
                                        isNetworkLoading = false
                                        if (res.startsWith("Успешно")) {
                                            dismissKeyboard()
                                            vpnFileAlert = res
                                            showVpnTextImportDialog = false
                                        } else {
                                            importErrorAlert = res
                                        }
                                    }
                                } else {
                                    val res = vpnManager.importConfigsFromText(trimmed)
                                    if (res.startsWith("Успешно")) {
                                        dismissKeyboard()
                                        vpnFileAlert = res
                                        showVpnTextImportDialog = false
                                    } else {
                                        importErrorAlert = res
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f),
                            enabled = importInputText.isNotBlank() && !isNetworkLoading
                        ) {
                            if (isNetworkLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 1.5.dp)
                            } else {
                                Text(if (lang == "RU") "ИМПОРТИРОВАТЬ" else "IMPORT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // --- PIN-CODE CONFIRMATION FOR BACKUP ACTIONS ---
    // ----------------------------------------------------
    if (showBackupPinVerifyDialog) {
        var enteredPin by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showBackupPinVerifyDialog = false; backupPinError = "" }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (lang == "RU") "🔐 Требуется PIN-код" else "🔐 PIN Security Check",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Введите PIN-код от настроек для подтверждения резервной операции."
                        } else {
                            "Enter the admin PIN code to authorize the settings backup action."
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { enteredPin = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        singleLine = true
                    )

                    if (viewModel.isPinLockedOut) {
                        PinCountdownTimerFeedback(
                            remainingSeconds = viewModel.pinLockoutRemainingSeconds,
                            totalSeconds = viewModel.pinLockoutTotalSeconds,
                            failedAttempts = viewModel.pinFailedAttempts,
                            isMatrixStyle = false,
                            lang = lang,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }

                    if (backupPinError.isNotEmpty()) {
                        Text(backupPinError, color = Color(0xFFD32F2F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showBackupPinVerifyDialog = false; backupPinError = "" },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                if (viewModel.isPinLockedOut) return@Button
                                if (enteredPin == viewModel.pinCode) {
                                    viewModel.registerPinSuccess()
                                    showBackupPinVerifyDialog = false
                                    backupPinError = ""
                                    if (backupActionType == "EXPORT") {
                                        exportedJsonText = viewModel.exportSettingsToJson()
                                        showExportResultDialog = true
                                    } else {
                                        showImportInputDialog = true
                                    }
                                } else {
                                    val delay = viewModel.registerPinFailure()
                                    backupPinError = if (lang == "RU") "Неверный PIN-код! Блокировка: ${delay}с." else "Incorrect PIN! Locked: ${delay}s."
                                }
                            },
                            enabled = !viewModel.isPinLockedOut,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "ОК" else "OK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // --- EXPORT RESULT DIALOG OVERLAY ---
    // ----------------------------------------------------
    if (showExportResultDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        Dialog(onDismissRequest = { showExportResultDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "💾 Конфигурация экспортирована" else "💾 Config Exported Successfully",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Скопируйте сгенерированный JSON-блок в буфер обмена для безопасного хранения."
                        } else {
                            "Copy the generated JSON backup block to clipboard for remote storage."
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = exportedJsonText,
                                    color = Color(0xFF00FF66),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportedJsonText))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "КОПИРОВАТЬ" else "COPY TO CLIPBOARD", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showExportResultDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "ГОТОВО" else "DONE", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // --- IMPORT INPUT DIALOG OVERLAY ---
    // ----------------------------------------------------
    if (showImportInputDialog) {
        var importJsonInputText by remember { mutableStateOf("") }
        var importSeedPhraseText by remember { mutableStateOf("") }
        var importStatusMsg by remember { mutableStateOf("") }
        var isImportSucessful by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { 
            if (!isImportSucessful) showImportInputDialog = false
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "📂 Импорт и дешифрование сети" else "📂 Network Configuration Import",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Поддерживается: стандартный JSON-файл настроек или зашифрованный блок CRAZYCONTAINER-. Если блок защищен сид-фразой, укажите ее ниже:"
                        } else {
                            "Supports: standard JSON configuration payload or CRAZYCONTAINER- base64 blocks. If secured with a seed mnemonic, specify it below:"
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = importJsonInputText,
                        onValueChange = { importJsonInputText = it },
                        placeholder = { Text("{\n  \"selectedTheme\": ...\n} OR CRAZYCONTAINER-...", fontSize = 10.sp, color = Color.Gray) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(90.dp)
                    )

                    // Optional Seed Mnemonic associated during decrypt
                    if (importJsonInputText.trim().startsWith("CRAZYCONTAINER-")) {
                        OutlinedTextField(
                            value = importSeedPhraseText,
                            onValueChange = { importSeedPhraseText = it },
                            placeholder = { Text("anchor beacon crypto ... (12 words)", fontSize = 10.sp, color = Color.Gray) },
                            label = { Text(if (lang == "RU") "Обязательная Сид-фраза" else "Required Seed Phrase", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (importStatusMsg.isNotEmpty()) {
                        Text(
                            text = importStatusMsg,
                            color = if (isImportSucessful) Color(0xFF00FF66) else Color(0xFFD32F2F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showImportInputDialog = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            enabled = !isImportSucessful
                        ) {
                            Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val text = importJsonInputText.trim()
                                val success = if (text.startsWith("CRAZYCONTAINER-")) {
                                    viewModel.importCryptocontainerWithSeed(importSeedPhraseText.trim(), text)
                                } else {
                                    viewModel.importSettingsFromJson(text)
                                }
                                
                                if (success) {
                                    isImportSucessful = true
                                    importStatusMsg = if (lang == "RU") "✅ Импорт выполнен успешно!" else "✅ Vault imported successfully!"
                                } else {
                                    importStatusMsg = if (lang == "RU") "❌ Неверный сид/формат ключа!" else "❌ Invalid seed or container format!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isImportSucessful) {
                                    if (lang == "RU") "ОТЛИЧНО" else "SUPER"
                                } else {
                                    if (lang == "RU") "ИМПОРТИРОВАТЬ" else "IMPORT"
                                },
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (isImportSucessful) {
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1200)
                            showImportInputDialog = false
                            viewModel.connectAndSyncAllNetworkComponents()
                        }
                    }
                }
            }
        }
    }

    if (showSaveCryptoKeyDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        Dialog(onDismissRequest = { showSaveCryptoKeyDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFFAB47BC).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "🔐 КЛЮЧИ БЕЗОПАСНОСТИ СЕТИ" else "🔐 NETWORK VAULT DECRYPTION KEYS",
                        color = Color(0xFFAB47BC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Это параметры шифрования вашего секретного криптоконтейнера SimpleX. Применяйте их для восстановления чата и настроек."
                        } else {
                            "These are the encryption parameters of your secure SimpleX container. Use them to rebuild contacts and configurations."
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    // 1. Seed Mnemonic Field
                    Text(
                        text = if (lang == "RU") "🔑 СИД-ФРАЗА (12 слов):" else "🔑 SEED PHRASE (12 words):",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = exportedSeedPhraseText,
                            color = Color(0xFF00FF66),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportedSeedPhraseText))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(if (lang == "RU") "СКОПИРОВАТЬ СИД-ФРАЗУ" else "COPY Mnemonic Phrase", fontSize = 10.sp, color = Color.White)
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // 2. Base64 encrypted container
                    Text(
                        text = if (lang == "RU") "📦 ТЕЛО КОНТЕЙНЕРА (Full Base64 Encrypted Block):" else "📦 CONTAINER KEY (Full Base64 Encrypted Block):",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = exportedContainerText,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color.Gray),
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Gray,
                            unfocusedTextColor = Color.Gray,
                            focusedBorderColor = Color.White.copy(alpha = 0.1f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                        )
                    )

                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportedContainerText))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(if (lang == "RU") "СКОПИРОВАТЬ ТЕЛО КОНТЕЙНЕРА" else "COPY FULL VAULT BLOCK", fontSize = 10.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showSaveCryptoKeyDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (lang == "RU") "ЗАКРЫТЬ" else "CLOSE", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showProfileModal) {
        UserProfileSelectorModal(
            viewModel = viewModel,
            onDismiss = { showProfileModal = false }
        )
    }

    if (showDuressSecurityModal) {
        DuressSecuritySettingsModal(
            viewModel = viewModel,
            onDismiss = { showDuressSecurityModal = false }
        )
    }

    if (showCreateGroupModal) {
        CreateGroupOrChannelModal(
            viewModel = viewModel,
            onDismiss = { showCreateGroupModal = false }
        )
    }

    if (showMessengerSettingsModal) {
        com.example.ui.dialogs.MessengerSettingsModal(
            viewModel = viewModel,
            onDismiss = { showMessengerSettingsModal = false }
        )
    }

    if (showFabActionModal) {
        Dialog(onDismissRequest = { showFabActionModal = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E262F)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "Быстрые действия" else "Quick Actions",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // 1. New Direct Chat -> Contacts
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showFabActionModal = false
                                chatSubSegment = 1
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FF66).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💬", fontSize = 16.sp)
                        }
                        Column {
                            Text(if (lang == "RU") "Новый диалог" else "New Direct Chat", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (lang == "RU") "Открыть контакты и адресную книгу" else "Open contacts & address book", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    // 2. Scan QR Code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showFabActionModal = false
                                showCameraView = true
                                isCameraScanning = true
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF29B6F6).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📷", fontSize = 16.sp)
                        }
                        Column {
                            Text(if (lang == "RU") "Сканировать QR-код" else "Scan QR Code", color = Color.White, fontSize = 13.sp)
                            Text(if (lang == "RU") "Быстрое подключение через камеру" else "Instant camera pairing", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    // 3. New Group or Channel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showFabActionModal = false
                                showCreateGroupModal = true
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFAB47BC).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👥", fontSize = 16.sp)
                        }
                        Column {
                            Text(if (lang == "RU") "Создать группу или канал" else "New Group or Channel", color = Color.White, fontSize = 13.sp)
                            Text(if (lang == "RU") "Шифрованная комната или новостной канал" else "Encrypted group or broadcast channel", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    // 4. My QR Code & Invite Link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showFabActionModal = false
                                chatSubSegment = 2
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB300).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔗", fontSize = 16.sp)
                        }
                        Column {
                            Text(if (lang == "RU") "Мой QR-код и инвайт" else "My QR & Invite Link", color = Color.White, fontSize = 13.sp)
                            Text(if (lang == "RU") "Сгенерировать одноразовый или постоянный ключ" else "Generate 1-time or long-term address", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    // 5. Join via Link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showFabActionModal = false
                                chatSubSegment = 3
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF26A69A).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📥", fontSize = 16.sp)
                        }
                        Column {
                            Text(if (lang == "RU") "Войти по ссылке" else "Join via Link", color = Color.White, fontSize = 13.sp)
                            Text(if (lang == "RU") "Вставить simplex:// или smp:// ссылку" else "Paste simplex:// or smp:// URI", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { showFabActionModal = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3540)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text(if (lang == "RU") "Отмена" else "Cancel", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (viewModel.isCryptoWalletFullScreenOpen) {
        CryptoWalletFullScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.isCryptoWalletFullScreenOpen = false }
        )
    }

}
