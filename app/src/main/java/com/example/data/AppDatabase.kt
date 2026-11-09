package com.example.data

import android.content.Context
import androidx.room.*
import com.example.crypto.SecureCryptoVault
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "match_history")
data class MatchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long, // timestamp
    val playerColor: String, // WHITE or BLACK
    val winner: String, // WHITE or BLACK
    val scorePlayer: Int,
    val scoreOpponent: Int,
    val isAgainstBot: Boolean,
    val gameDurationSeconds: Long
)

@Entity(tableName = "encrypted_rooms")
data class EncryptedRoomEntity(
    @PrimaryKey val roomId: String,
    val profileId: String,
    val encryptedTitle: String,
    val encryptedLastMessage: String,
    val isOneTime: Boolean,
    val encryptedSimplexUrl: String,
    val roomType: String,
    val selfDestructTimerSec: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "encrypted_messages",
    indices = [Index(value = ["roomId"]), Index(value = ["profileId"])]
)
data class EncryptedMessageEntity(
    @PrimaryKey val messageId: String,
    val roomId: String,
    val profileId: String,
    val senderHandle: String,
    val encryptedTextPayload: String, // AES-256-GCM encrypted via SecureCryptoVault
    val timestamp: Long,
    val attachmentType: String = "NONE",
    val encryptedAttachmentMeta: String = "",
    val reactionsCsv: String = "",
    val selfDestructTimeLeft: Int = -1,
    val isSelfDestructed: Boolean = false
)

@Entity(tableName = "encrypted_contacts")
data class EncryptedContactEntity(
    @PrimaryKey val contactKey: String, // profileId + ":" + handle
    val profileId: String,
    val encryptedName: String,
    val handle: String,
    val isOnline: Boolean,
    val isAnonymous: Boolean,
    val rating: Int
)

@Dao
interface MatchHistoryDao {
    @Query("SELECT * FROM match_history ORDER BY date DESC")
    fun getAllMatches(): Flow<List<MatchHistory>>

    @Insert
    suspend fun insertMatch(match: MatchHistory)

    @Query("DELETE FROM match_history")
    suspend fun clearHistory()
}

@Dao
interface MessengerVaultDao {
    @Query("SELECT * FROM encrypted_rooms WHERE profileId = :profileId ORDER BY updatedAt DESC")
    fun observeRoomsForProfile(profileId: String): Flow<List<EncryptedRoomEntity>>

    @Query("SELECT * FROM encrypted_rooms WHERE profileId = :profileId ORDER BY updatedAt DESC")
    suspend fun getRoomsForProfile(profileId: String): List<EncryptedRoomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: EncryptedRoomEntity)

    @Query("SELECT * FROM encrypted_messages WHERE roomId = :roomId AND profileId = :profileId ORDER BY timestamp ASC")
    suspend fun getMessagesForRoom(roomId: String, profileId: String): List<EncryptedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: EncryptedMessageEntity)

    @Query("DELETE FROM encrypted_messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("SELECT * FROM encrypted_contacts WHERE profileId = :profileId")
    suspend fun getContactsForProfile(profileId: String): List<EncryptedContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: EncryptedContactEntity)

    @Query("DELETE FROM encrypted_rooms WHERE profileId = :profileId")
    suspend fun deleteRoomsForProfile(profileId: String)

    @Query("DELETE FROM encrypted_messages WHERE profileId = :profileId")
    suspend fun deleteMessagesForProfile(profileId: String)

    @Query("DELETE FROM encrypted_contacts WHERE profileId = :profileId")
    suspend fun deleteContactsForProfile(profileId: String)

    @Query("DELETE FROM encrypted_rooms")
    suspend fun wipeAllRooms()

    @Query("DELETE FROM encrypted_messages")
    suspend fun wipeAllMessages()

    @Query("DELETE FROM encrypted_contacts")
    suspend fun wipeAllContacts()
}

/**
 * Repository Pattern abstracting AES-256-GCM at-rest encryption for Room SQLite tables.
 */
class MessengerVaultRepository(private val dao: MessengerVaultDao) {

    private fun resolvePinKey(pin: String): Result<String> {
        return if (pin.isNotBlank()) {
            Result.success(pin)
        } else {
            Result.failure(IllegalArgumentException("Cannot access encrypted vault without an authenticated PIN"))
        }
    }

    fun encryptField(plain: String, pin: String): String {
        if (plain.isEmpty()) return ""
        val key = resolvePinKey(pin).getOrThrow()
        return SecureCryptoVault.encryptString(plain, key)
    }

    fun decryptField(cipherB64: String, pin: String): String {
        if (cipherB64.isEmpty()) return ""
        val key = resolvePinKey(pin).getOrNull() ?: return "[🔒 Заблокировано: требуется PIN]"
        return try {
            SecureCryptoVault.decryptString(cipherB64, key)
        } catch (_: Exception) {
            "[🔒 Ошибка дешифровки]"
        }
    }

    suspend fun saveEncryptedRoom(
        profileId: String,
        roomId: String,
        title: String,
        lastMessage: String,
        isOneTime: Boolean,
        simplexUrl: String,
        roomType: String,
        selfDestructTimerSec: Int,
        pin: String
    ) {
        val entity = EncryptedRoomEntity(
            roomId = "${profileId}_$roomId",
            profileId = profileId,
            encryptedTitle = encryptField(title, pin),
            encryptedLastMessage = encryptField(lastMessage, pin),
            isOneTime = isOneTime,
            encryptedSimplexUrl = encryptField(simplexUrl, pin),
            roomType = roomType,
            selfDestructTimerSec = selfDestructTimerSec,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertRoom(entity)
    }

    suspend fun saveEncryptedMessage(
        profileId: String,
        roomId: String,
        messageId: String,
        sender: String,
        text: String,
        timestamp: Long,
        attachmentType: String,
        attachmentMeta: String,
        reactions: List<String>,
        selfDestructTimeLeft: Int,
        isSelfDestructed: Boolean,
        pin: String
    ) {
        val entity = EncryptedMessageEntity(
            messageId = messageId,
            roomId = "${profileId}_$roomId",
            profileId = profileId,
            senderHandle = encryptField(sender, pin),
            encryptedTextPayload = encryptField(text, pin),
            timestamp = timestamp,
            attachmentType = attachmentType,
            encryptedAttachmentMeta = encryptField(attachmentMeta, pin),
            reactionsCsv = reactions.joinToString(","),
            selfDestructTimeLeft = selfDestructTimeLeft,
            isSelfDestructed = isSelfDestructed
        )
        dao.upsertMessage(entity)
    }

    suspend fun saveEncryptedContact(
        profileId: String,
        name: String,
        handle: String,
        isOnline: Boolean,
        isAnonymous: Boolean,
        rating: Int,
        pin: String
    ) {
        val entity = EncryptedContactEntity(
            contactKey = "$profileId:$handle",
            profileId = profileId,
            encryptedName = encryptField(name, pin),
            handle = handle,
            isOnline = isOnline,
            isAnonymous = isAnonymous,
            rating = rating
        )
        dao.upsertContact(entity)
    }

    suspend fun wipeAllEncryptedData() {
        dao.wipeAllMessages()
        dao.wipeAllRooms()
        dao.wipeAllContacts()
    }
}

@Database(
    entities = [
        MatchHistory::class,
        EncryptedRoomEntity::class,
        EncryptedMessageEntity::class,
        EncryptedContactEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matchHistoryDao(): MatchHistoryDao
    abstract fun messengerVaultDao(): MessengerVaultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backgammon_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
