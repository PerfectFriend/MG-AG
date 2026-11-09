package com.example.model

/**
 * Тип транзакции: входящая (получение) или исходящая (отправка).
 */
enum class TransactionType {
    INCOMING,
    OUTGOING
}

/**
 * Статус подтверждения транзакции в блокчейне.
 */
enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED
}

/**
 * Модель записи в журнале транзакций криптокошелька.
 */
data class WalletTransaction(
    val id: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val asset: String, // BTC, XMR, USDT, TON
    val amount: Double,
    val fee: Double,
    val address: String,
    val txHash: String,
    val timestamp: Long,
    val confirmations: Int = 12,
    val note: String = ""
)

/**
 * Контакт в адресной книге криптокошелька.
 */
data class WalletAddressBookContact(
    val id: String,
    val name: String,
    val address: String,
    val asset: String,
    val note: String = ""
)

/**
 * Сводка по криптоактиву (баланс, деривационный публичный адрес, курс к USD).
 */
data class CryptoAssetBalance(
    val asset: String,
    val name: String,
    val balance: Double,
    val usdRate: Double,
    val publicAddress: String,
    val iconEmoji: String,
    val colorHex: Long = 0xFF00E676
)
