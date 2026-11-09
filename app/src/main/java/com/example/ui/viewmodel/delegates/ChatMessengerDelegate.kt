package com.example.ui.viewmodel.delegates

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.GameViewModel.ChatMessage
import com.example.ui.GameViewModel.SimpleXContact
import com.example.ui.GameViewModel.SimpleXRoom
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.UUID

/**
 * Делегат мессенджера SimpleX, чатов, комнат и персонального хранилища «Избранное».
 *
 * Отвечает за:
 * - Управление списком комнат [SimpleXRoom] и контактов [SimpleXContact].
 * - Персональное хранилище «Избранное» (`SAVED_MESSAGES`) с поддержкой всех типов вложений.
 * - Системные действия с сообщениями: экспорт через Android Share Chooser, сохранение в память устройства, удаление.
 * - Управление приватностью (уведомления о доставке, прочтении, статус набора текста).
 *
 * @param context Контекст приложения Android.
 * @param scope CoroutineScope ViewModel для выполнения фоновых операций.
 */
class ChatMessengerDelegate(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)

    var simplexUserHandle by mutableStateOf(prefs.getString("simplex_handle", "GhostOperative") ?: "GhostOperative")

    val simplexRooms = mutableStateListOf<SimpleXRoom>()
    val simplexContacts = mutableStateListOf<SimpleXContact>()

    var activeRoomId by mutableStateOf("SAVED_MESSAGES")

    var sendDeliveryReceipts by mutableStateOf(prefs.getBoolean("simplex_delivery_receipts", true))
    var sendReadReceipts by mutableStateOf(prefs.getBoolean("simplex_read_receipts", true))
    var sendTypingIndicators by mutableStateOf(prefs.getBoolean("simplex_typing_indicators", true))

    init {
        // Инициализируем хранилище «Избранное» на первой позиции списка чатов
        val savedMessagesRoom = SimpleXRoom(
            id = "SAVED_MESSAGES",
            title = "Избранное",
            lastMessage = "Личное зашифрованное хранилище заметок и файлов",
            roomType = "SAVED_MESSAGES",
            messages = mutableStateListOf(
                ChatMessage(
                    id = "vault_welcome_1",
                    sender = simplexUserHandle,
                    text = "Добро пожаловать в персональное хранилище Избранное! Здесь вы можете сохранять заметки, файлы, аудиозаписи и резервные копии.",
                    timestamp = System.currentTimeMillis()
                )
            )
        )
        simplexRooms.add(savedMessagesRoom)

        // Инициализируем базовые контакты
        if (simplexContacts.isEmpty()) {
            simplexContacts.add(SimpleXContact("Zarik Bot", "@zarik_bot_simplex", isOnline = true))
            simplexContacts.add(SimpleXContact("Admin Armageddon", "@admin_armageddon", isOnline = true))
            simplexContacts.add(SimpleXContact("Anonymous Opponent", "@anon_opp", isOnline = true))
        }
    }

    /**
     * Удаляет сообщение из комнаты по его идентификатору.
     */
    fun deleteChatMessage(roomId: String, messageId: String) {
        val room = simplexRooms.firstOrNull { it.id == roomId } ?: return
        room.messages.removeAll { it.id == messageId }
        room.lastMessage = room.messages.lastOrNull()?.text ?: ""
    }

    /**
     * Запускает стандартный диалог отправки Android Share Sheet (Intent.ACTION_SEND).
     */
    fun shareChatMessage(msg: ChatMessage, lang: String = "RU") {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (msg.attachmentType) {
                    "IMAGE" -> "image/*"
                    "AUDIO" -> "audio/*"
                    "FILE" -> "*/*"
                    else -> "text/plain"
                }
                val body = buildString {
                    if (msg.attachmentName.isNotBlank()) {
                        append("[${msg.attachmentType}: ${msg.attachmentName}]\n")
                    }
                    if (msg.text.isNotBlank()) {
                        append(msg.text)
                    }
                }
                putExtra(Intent.EXTRA_SUBJECT, "SimpleX Vault / Chat")
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(
                sendIntent,
                if (lang == "RU") "Поделиться через..." else "Share via..."
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть меню отправки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Сохраняет вложение или текст сообщения в память устройства / буфер обмена.
     */
    fun saveChatMessageToDevice(msg: ChatMessage, lang: String = "RU") {
        try {
            if (msg.attachmentName.isNotBlank()) {
                val file = File(context.filesDir, "saved_${msg.attachmentName}")
                file.writeText(if (msg.attachmentUrl.isNotBlank()) msg.attachmentUrl else msg.text)
                Toast.makeText(
                    context,
                    if (lang == "RU") "Вложение '${msg.attachmentName}' сохранено в память" else "Attachment '${msg.attachmentName}' saved",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SimpleX Note", msg.text))
                Toast.makeText(
                    context,
                    if (lang == "RU") "Текст скопирован в буфер обмена и сохранён" else "Text copied to clipboard and saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Добавляет элемент или заметку в персональное хранилище «Избранное».
     */
    fun addSavedVaultItem(
        text: String,
        attachmentType: String = "NONE",
        attachmentName: String = "",
        attachmentUrl: String = "",
        attachmentSize: String = "",
        lang: String = "RU"
    ) {
        val room = simplexRooms.firstOrNull { it.id == "SAVED_MESSAGES" } ?: run {
            val newRoom = SimpleXRoom(
                id = "SAVED_MESSAGES",
                title = if (lang == "RU") "Избранное" else "Saved Messages",
                lastMessage = text,
                roomType = "SAVED_MESSAGES"
            )
            simplexRooms.add(0, newRoom)
            newRoom
        }
        val newMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = simplexUserHandle,
            text = text,
            timestamp = System.currentTimeMillis(),
            attachmentType = attachmentType,
            attachmentName = attachmentName,
            attachmentUrl = attachmentUrl,
            attachmentSize = attachmentSize
        )
        room.messages.add(newMsg)
        room.lastMessage = if (attachmentName.isNotBlank()) "[$attachmentName] $text" else text
    }

    fun addSimpleXReaction(roomId: String, messageId: String, reaction: String) {
        val room = simplexRooms.firstOrNull { it.id == roomId } ?: return
        val msg = room.messages.firstOrNull { it.id == messageId } ?: return
        msg.reactions.add(reaction)
    }

    fun toggleDeliveryReceipts() {
        sendDeliveryReceipts = !sendDeliveryReceipts
        prefs.edit().putBoolean("simplex_delivery_receipts", sendDeliveryReceipts).apply()
    }

    fun toggleReadReceipts() {
        sendReadReceipts = !sendReadReceipts
        prefs.edit().putBoolean("simplex_read_receipts", sendReadReceipts).apply()
    }

    fun toggleTypingIndicators() {
        sendTypingIndicators = !sendTypingIndicators
        prefs.edit().putBoolean("simplex_typing_indicators", sendTypingIndicators).apply()
    }

    fun updateSimplexHandle(name: String) {
        simplexUserHandle = name
        prefs.edit().putString("simplex_handle", name).apply()
    }

    fun addContact(name: String, handle: String, isAnonymous: Boolean = false) {
        if (simplexContacts.none { it.handle == handle }) {
            simplexContacts.add(SimpleXContact(name, handle, isOnline = true, isAnonymous = isAnonymous, rating = 0))
        }
    }
}
