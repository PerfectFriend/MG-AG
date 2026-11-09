package com.example.ui.screens.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.data.CryptoWalletManager
import com.example.model.TransactionStatus
import com.example.model.TransactionType
import com.example.model.WalletAddressBookContact
import com.example.model.WalletTransaction
import com.example.ui.GameViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Полноэкранный криптокошелек и хранилище цифровых активов (Telegram Wallet Style).
 *
 * Предоставляет полный интерфейс:
 * - Баланс портфеля в USD и криптовалютах (BTC, XMR, USDT, TON).
 * - Отправка и получение с генерацией QR-кода и валидацией адреса.
 * - Детализированный журнал транзакций со статусами и подтверждениями.
 * - Адресная книга доверенных контактов для быстрых переводов.
 * - Просмотр содержимого криптоконтейнера и BIP-39 мастер-ключа.
 *
 * @param viewModel главная вьюмодель игры и мессенджера.
 * @param onDismiss коллбек возврата в интерфейс мессенджера.
 */
@Composable
fun CryptoWalletFullScreen(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val walletManager = remember { viewModel.cryptoWalletManager }
    val lang = viewModel.selectedChatLanguage
    val seedHex = viewModel.registeredIdentity?.seedPhrase ?: viewModel.currentSeedPhrase

    var activeTab by remember { mutableStateOf(0) } // 0 = Transactions, 1 = Address Book, 2 = Vault Contents
    var selectedAssetFilter by remember { mutableStateOf<String?>(null) } // null = All
    var showSendDialog by remember { mutableStateOf(false) }
    var showReceiveDialog by remember { mutableStateOf(false) }
    var receiveAssetChoice by remember { mutableStateOf("BTC") }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var selectedTxForDetails by remember { mutableStateOf<WalletTransaction?>(null) }

    val assetBalances = remember(walletManager.btcBalance, walletManager.xmrBalance, walletManager.usdtBalance, walletManager.tonBalance, seedHex) {
        walletManager.getAssetBalances(seedHex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1014))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Верхняя панель AppBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF141920))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("wallet_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Messenger",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = if (lang == "RU") "Криптокошелёк" else "Crypto Wallet",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Text(
                                text = "BIP-39 Vault • Mainnet",
                                fontSize = 9.5.sp,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { walletManager.refreshMarketRates() },
                        enabled = !walletManager.isLiveRatesLoading
                    ) {
                        if (walletManager.isLiveRatesLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF00E676),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Market Rates", tint = Color(0xFF00E676))
                        }
                    }
                    IconButton(onClick = {
                        receiveAssetChoice = "BTC"
                        showReceiveDialog = true
                    }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Receive QR", tint = Color(0xFF00E676))
                    }
                }
            }

            // Основная часть экрана
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Карточка портфельного баланса
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17202A)),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "ОБЩИЙ БАЛАНС ПОРТФЕЛЯ" else "TOTAL PORTFOLIO VALUE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "$%,.2f USD".format(Locale.US, walletManager.totalPortfolioUsd),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        // 4 кнопки действий (Отправить, Получить, Контакты, Хранилище)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WalletActionButton(
                                icon = Icons.Default.ArrowUpward,
                                label = if (lang == "RU") "Отправить" else "Send",
                                tintColor = Color(0xFFFF5252),
                                onClick = { showSendDialog = true }
                            )

                            WalletActionButton(
                                icon = Icons.Default.ArrowDownward,
                                label = if (lang == "RU") "Получить" else "Receive",
                                tintColor = Color(0xFF00E676),
                                onClick = { showReceiveDialog = true }
                            )

                            WalletActionButton(
                                icon = Icons.Default.Contacts,
                                label = if (lang == "RU") "Контакты" else "Contacts",
                                tintColor = Color(0xFF29B6F6),
                                onClick = { activeTab = 1 }
                            )

                            WalletActionButton(
                                icon = Icons.Default.Lock,
                                label = if (lang == "RU") "Хранилище" else "Vault",
                                tintColor = Color(0xFFFFD54F),
                                onClick = { activeTab = 2 }
                            )
                        }
                    }
                }

                // Сетка активов
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    assetBalances.forEach { asset ->
                        val isSelected = selectedAssetFilter == asset.asset
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1E2D3D) else Color(0xFF161B22)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedAssetFilter = if (isSelected) null else asset.asset
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(asset.iconEmoji, fontSize = 16.sp)
                                Text(asset.asset, fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.White)
                                Text(
                                    text = if (asset.balance >= 100) "%.1f".format(asset.balance) else "%.4f".format(asset.balance),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Вкладки: [Журнал] [Адреса] [Содержимое]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141920))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        if (lang == "RU") "📜 Журнал" else "📜 History",
                        if (lang == "RU") "📖 Адреса" else "📖 Contacts",
                        if (lang == "RU") "🗄️ Содержимое" else "🗄️ Vault"
                    ).forEachIndexed { index, title ->
                        val isSel = activeTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00E676) else Color.Transparent)
                                .clickable { activeTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.Black else Color.LightGray
                            )
                        }
                    }
                }

                // Контент вкладки
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeTab) {
                        0 -> {
                            // Журнал транзакций
                            val filteredTxs = remember(walletManager.transactions.size, selectedAssetFilter) {
                                if (selectedAssetFilter == null) walletManager.transactions
                                else walletManager.transactions.filter { it.asset.equals(selectedAssetFilter, ignoreCase = true) }
                            }

                            if (filteredTxs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (lang == "RU") "Транзакций не найдено" else "No transactions found",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredTxs) { tx ->
                                        TransactionItemCard(
                                            tx = tx,
                                            onClick = { selectedTxForDetails = tx }
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Адресная книга
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showAddContactDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (lang == "RU") "ДОБАВИТЬ НОВЫЙ АДРЕС" else "ADD NEW ADDRESS",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(walletManager.addressBook) { contact ->
                                        AddressBookCard(
                                            contact = contact,
                                            onCopy = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Address", contact.address))
                                                Toast.makeText(context, "Адрес скопирован", Toast.LENGTH_SHORT).show()
                                            },
                                            onDelete = {
                                                walletManager.removeContact(contact.id)
                                            },
                                            onSend = {
                                                showSendDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Содержимое криптоконтейнера & ключи
                            VaultBreakdownView(
                                viewModel = viewModel,
                                walletManager = walletManager
                            )
                        }
                    }
                }
            }
        }

        // Диалоги
        if (showSendDialog) {
            SendCryptoModal(
                walletManager = walletManager,
                initialAsset = selectedAssetFilter ?: "BTC",
                lang = lang,
                onDismiss = { showSendDialog = false }
            )
        }

        if (showReceiveDialog) {
            ReceiveCryptoModal(
                walletManager = walletManager,
                masterSeedHex = seedHex,
                selectedAsset = receiveAssetChoice,
                onAssetChange = { receiveAssetChoice = it },
                lang = lang,
                onDismiss = { showReceiveDialog = false }
            )
        }

        if (showAddContactDialog) {
            AddAddressContactModal(
                walletManager = walletManager,
                lang = lang,
                onDismiss = { showAddContactDialog = false }
            )
        }

        selectedTxForDetails?.let { tx ->
            TxDetailsModal(
                tx = tx,
                onDismiss = { selectedTxForDetails = null }
            )
        }
    }
}

@Composable
private fun WalletActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tintColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tintColor.copy(alpha = 0.15f))
                .border(1.dp, tintColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tintColor, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray
        )
    }
}

@Composable
private fun TransactionItemCard(
    tx: WalletTransaction,
    onClick: () -> Unit
) {
    val isIncoming = tx.type == TransactionType.INCOMING
    val statusColor = if (tx.status == TransactionStatus.CONFIRMED) Color(0xFF00E676) else Color(0xFFFFB300)
    val timeStr = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(tx.timestamp))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141920)),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isIncoming) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isIncoming) Color(0xFF00E676) else Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = tx.note.ifBlank { if (isIncoming) "Получено ${tx.asset}" else "Отправлено ${tx.asset}" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$timeStr • Tx: ${tx.txHash.take(8)}...",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncoming) "+" else "-"}${tx.amount} ${tx.asset}",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.5.sp,
                    color = if (isIncoming) Color(0xFF00E676) else Color(0xFFFF5252)
                )
                Text(
                    text = if (tx.status == TransactionStatus.CONFIRMED) "✓ Подтверждена" else "⌛ В обработке",
                    fontSize = 8.5.sp,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AddressBookCard(
    contact: WalletAddressBookContact,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141920)),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contact.name, fontWeight = FontWeight.Black, fontSize = 12.5.sp, color = Color.White)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(contact.asset, color = Color(0xFF00E676), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = contact.address,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.note.isNotBlank()) {
                    Text(contact.note, fontSize = 8.5.sp, color = Color.LightGray)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onSend, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF00E676), modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun VaultBreakdownView(
    viewModel: GameViewModel,
    walletManager: CryptoWalletManager
) {
    var isSeedRevealed by remember { mutableStateOf(false) }
    val seed = viewModel.registeredIdentity?.seedPhrase ?: viewModel.currentSeedPhrase

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141920)),
            border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔐 BIP-39 Мнемоническая фраза",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = Color(0xFFFFD54F)
                    )
                    TextButton(onClick = { isSeedRevealed = !isSeedRevealed }) {
                        Text(if (isSeedRevealed) "Скрыть" else "Показать", fontSize = 10.sp, color = Color(0xFF00E676))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (isSeedRevealed) (seed.ifBlank { "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about" })
                        else "•••• •••• •••• •••• •••• •••• •••• •••• •••• •••• •••• ••••",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSeedRevealed) Color.White else Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141920)),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🌐 Деривационные адреса сетей",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )

                walletManager.getAssetBalances(seed).forEach { asset ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${asset.iconEmoji} ${asset.name}", fontSize = 10.sp, color = Color.LightGray)
                        Text(
                            text = asset.publicAddress.take(10) + "..." + asset.publicAddress.takeLast(6),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendCryptoModal(
    walletManager: CryptoWalletManager,
    initialAsset: String,
    lang: String,
    onDismiss: () -> Unit
) {
    var selectedAsset by remember { mutableStateOf(initialAsset) }
    var addressInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (lang == "RU") "⬆️ Отправка криптовалюты" else "⬆️ Send Crypto",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("BTC", "XMR", "USDT", "TON").forEach { asset ->
                        val isSel = selectedAsset == asset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00E676) else Color(0xFF222B35))
                                .clickable { selectedAsset = asset }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(asset, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                        }
                    }
                }

                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it; errorMessage = "" },
                    label = { Text("Адрес получателя ($selectedAsset)", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color.DarkGray)
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it; errorMessage = "" },
                    label = { Text("Сумма перевода", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color.DarkGray)
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Примечание (необязательно)", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color.DarkGray)
                )

                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
                            val res = walletManager.sendTransaction(selectedAsset, addressInput, parsedAmount, noteInput)
                            if (res.first) {
                                Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                                onDismiss()
                            } else {
                                errorMessage = res.second
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text("ОТПРАВИТЬ", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiveCryptoModal(
    walletManager: CryptoWalletManager,
    masterSeedHex: String,
    selectedAsset: String,
    onAssetChange: (String) -> Unit,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val balanceObj = walletManager.getAssetBalances(masterSeedHex).firstOrNull { it.asset == selectedAsset }
        ?: walletManager.getAssetBalances(masterSeedHex).first()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (lang == "RU") "⬇️ Получить перевод" else "⬇️ Receive Funds",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("BTC", "XMR", "USDT", "TON").forEach { asset ->
                        val isSel = selectedAsset == asset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00E676) else Color(0xFF222B35))
                                .clickable { onAssetChange(asset) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(asset, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellSize = size.width / 15f
                        val hash = balanceObj.publicAddress.hashCode()
                        for (r in 0 until 15) {
                            for (c in 0 until 15) {
                                val isCorner = (r < 4 && c < 4) || (r < 4 && c > 10) || (r > 10 && c < 4)
                                val bit = ((hash xor (r * 31 + c * 17)) and 1) == 1
                                if (isCorner || bit) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(c * cellSize, r * cellSize),
                                        size = Size(cellSize, cellSize)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = balanceObj.publicAddress,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Wallet Address", balanceObj.publicAddress))
                            Toast.makeText(context, "Адрес скопирован в буфер", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("СКОПИРОВАТЬ", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }

                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.7f)) {
                        Text("Закрыть", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAddressContactModal(
    walletManager: CryptoWalletManager,
    lang: String,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var assetInput by remember { mutableStateOf("BTC") }
    var noteInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (lang == "RU") "Добавить контакт в адресную книгу" else "Add Address Book Contact",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color.White
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Имя контакта / Название", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    label = { Text("Криптографический адрес", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("BTC", "XMR", "USDT", "TON").forEach { asset ->
                        val isSel = assetInput == asset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00E676) else Color(0xFF222B35))
                                .clickable { assetInput = asset }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(asset, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                        }
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Заметка (необязательно)", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (walletManager.addContact(nameInput, addressInput, assetInput, noteInput)) {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("СОХРАНИТЬ", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TxDetailsModal(
    tx: WalletTransaction,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isIncoming = tx.type == TransactionType.INCOMING
    val timeStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(tx.timestamp))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isIncoming) "Входящая транзакция" else "Исходящая транзакция",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color.White
                )

                Text(
                    text = "${if (isIncoming) "+" else "-"}${tx.amount} ${tx.asset}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isIncoming) Color(0xFF00E676) else Color(0xFFFF5252)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Статус", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = if (tx.status == TransactionStatus.CONFIRMED) "Подтверждена (${tx.confirmations} подтв.)" else "В обработке",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Время", fontSize = 10.sp, color = Color.Gray)
                            Text(timeStr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Комиссия", fontSize = 10.sp, color = Color.Gray)
                            Text("${tx.fee} ${tx.asset}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Адрес", fontSize = 10.sp, color = Color.Gray)
                            Text(tx.address.take(12) + "..." + tx.address.takeLast(6), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Хэш", fontSize = 10.sp, color = Color.Gray)
                            Text(tx.txHash.take(12) + "..." + tx.txHash.takeLast(6), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("TxHash", tx.txHash))
                            Toast.makeText(context, "Хэш скопирован", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Скопировать хэш", color = Color.Black, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.7f)) {
                        Text("Закрыть", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
