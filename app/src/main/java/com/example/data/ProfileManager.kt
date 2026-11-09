package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.crypto.SecureCryptoVault
import com.example.model.DuressAction
import com.example.model.ProfileType
import com.example.model.UserProfile
import com.example.security.EncryptedPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProfileManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("simplex_profiles_vault", Context.MODE_PRIVATE)

    private val securePrefs = EncryptedPrefs(context, "simplex_profiles_vault")

    var activeProfile by mutableStateOf<UserProfile>(getDefaultPrimaryProfile())
        private set

    val availableProfiles = mutableStateListOf<UserProfile>()

    var duressPinCode by mutableStateOf(securePrefs.getString("duress_pin_code"))
        private set

    var duressAction by mutableStateOf(
        try {
            DuressAction.valueOf(prefs.getString("duress_action", DuressAction.DECOY_PROFILE.name) ?: DuressAction.DECOY_PROFILE.name)
        } catch (e: Exception) {
            DuressAction.DECOY_PROFILE
        }
    )
        private set

    var isScreenSecurityEnabled by mutableStateOf(prefs.getBoolean("screen_security_flag", false))
        private set

    init {
        loadProfilesList()
    }

    private fun getDefaultPrimaryProfile(): UserProfile {
        return UserProfile(
            id = "profile_primary",
            name = "Nardy Pro (Main)",
            handle = "NardyPro_99",
            profileType = ProfileType.PRIMARY,
            avatarColor = 0xFF00FF66,
            saltHex = SecureCryptoVault.generateSaltHex(),
            isDecoy = false
        )
    }

    private fun getDefaultDecoyProfile(): UserProfile {
        return UserProfile(
            id = "profile_decoy",
            name = "Casual Player",
            handle = "Guest_Player",
            profileType = ProfileType.DECOY,
            avatarColor = 0xFF888888,
            saltHex = SecureCryptoVault.generateSaltHex(),
            isDecoy = true
        )
    }

    private fun getDefaultIncognitoProfile(): UserProfile {
        return UserProfile(
            id = "profile_incognito",
            name = "Ghost Anon",
            handle = "Anon_Ghost",
            profileType = ProfileType.INCOGNITO,
            avatarColor = 0xFF00E5FF,
            saltHex = SecureCryptoVault.generateSaltHex(),
            isDecoy = false,
            isSelfDestructOnExit = true
        )
    }

    fun loadProfilesList() {
        availableProfiles.clear()
        val rawJson = prefs.getString("profiles_metadata", null)
        if (rawJson.isNullOrBlank()) {
            val primary = getDefaultPrimaryProfile()
            val incognito = getDefaultIncognitoProfile()
            val decoy = getDefaultDecoyProfile()
            availableProfiles.addAll(listOf(primary, incognito, decoy))
            saveProfilesList()
            activeProfile = primary
        } else {
            try {
                val array = JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val p = UserProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        handle = obj.getString("handle"),
                        profileType = try { ProfileType.valueOf(obj.getString("profileType")) } catch (e: Exception) { ProfileType.PRIMARY },
                        avatarColor = obj.optLong("avatarColor", 0xFF00FF66),
                        saltHex = obj.optString("saltHex", SecureCryptoVault.generateSaltHex()),
                        isDecoy = obj.optBoolean("isDecoy", false),
                        isSelfDestructOnExit = obj.optBoolean("isSelfDestructOnExit", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    availableProfiles.add(p)
                }
                val activeId = prefs.getString("active_profile_id", "profile_primary")
                activeProfile = availableProfiles.find { it.id == activeId } ?: availableProfiles.firstOrNull() ?: getDefaultPrimaryProfile()
            } catch (e: Exception) {
                val primary = getDefaultPrimaryProfile()
                availableProfiles.add(primary)
                activeProfile = primary
            }
        }
    }

    fun saveProfilesList() {
        val array = JSONArray()
        for (p in availableProfiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("handle", p.handle)
                put("profileType", p.profileType.name)
                put("avatarColor", p.avatarColor)
                put("saltHex", p.saltHex)
                put("isDecoy", p.isDecoy)
                put("isSelfDestructOnExit", p.isSelfDestructOnExit)
                put("createdAt", p.createdAt)
            }
            array.put(obj)
        }
        prefs.edit()
            .putString("profiles_metadata", array.toString())
            .putString("active_profile_id", activeProfile.id)
            .apply()
    }

    fun createProfile(name: String, handle: String, type: ProfileType, isDecoy: Boolean = false): UserProfile {
        val newProfile = UserProfile(
            id = "profile_" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "Profile_${availableProfiles.size + 1}" },
            handle = handle.ifBlank { "user_${availableProfiles.size + 1}" },
            profileType = type,
            avatarColor = when (type) {
                ProfileType.PRIMARY -> 0xFF00FF66
                ProfileType.HIDDEN -> 0xFFFF9900
                ProfileType.INCOGNITO -> 0xFF00E5FF
                ProfileType.DECOY -> 0xFF9E9E9E
            },
            saltHex = SecureCryptoVault.generateSaltHex(),
            isDecoy = isDecoy,
            isSelfDestructOnExit = (type == ProfileType.INCOGNITO)
        )
        availableProfiles.add(newProfile)
        saveProfilesList()
        return newProfile
    }

    fun deleteProfile(profileId: String) {
        if (availableProfiles.size <= 1) return
        val toRemove = availableProfiles.find { it.id == profileId } ?: return
        availableProfiles.remove(toRemove)
        
        // Remove encrypted profile data
        prefs.edit().remove("vault_data_${toRemove.id}").apply()

        if (activeProfile.id == profileId) {
            activeProfile = availableProfiles.first()
        }
        saveProfilesList()
    }

    fun switchProfile(profileId: String): UserProfile? {
        val target = availableProfiles.find { it.id == profileId } ?: return null
        activeProfile = target
        prefs.edit().putString("active_profile_id", target.id).apply()
        return target
    }

    fun switchToDecoyProfile(): UserProfile {
        var decoy = availableProfiles.find { it.isDecoy || it.profileType == ProfileType.DECOY }
        if (decoy == null) {
            decoy = getDefaultDecoyProfile()
            availableProfiles.add(decoy)
            saveProfilesList()
        }
        activeProfile = decoy
        prefs.edit().putString("active_profile_id", decoy.id).apply()
        return decoy
    }

    fun updateDuressConfig(pin: String, action: DuressAction) {
        val cleanPin = pin.filter { it.isDigit() }.take(8)
        duressPinCode = if (cleanPin.length >= 4) cleanPin else ""
        duressAction = action
        securePrefs.putString("duress_pin_code", duressPinCode)
        prefs.edit()
            .putString("duress_action", action.name)
            .apply()
    }

    fun checkDuressPin(enteredPin: String): DuressAction? {
        if (duressPinCode.isBlank()) return null
        return if (enteredPin == duressPinCode) duressAction else null
    }

    fun setScreenSecurity(enabled: Boolean) {
        isScreenSecurityEnabled = enabled
        prefs.edit().putBoolean("screen_security_flag", enabled).apply()
    }

    /**
     * Saves encrypted payload for a specific profile using the master PIN.
     */
    fun saveProfileVaultData(profileId: String, pin: String, rawDataJson: String) {
        try {
            val profile = availableProfiles.find { it.id == profileId } ?: activeProfile
            val salt = SecureCryptoVault.hexToBytes(profile.saltHex)
            val encryptedEnvelope = SecureCryptoVault.encryptString(rawDataJson, pin, salt)
            prefs.edit().putString("vault_data_$profileId", encryptedEnvelope).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads and decrypts profile payload using master PIN.
     */
    fun loadProfileVaultData(profileId: String, pin: String): String? {
        return try {
            val encryptedEnvelope = prefs.getString("vault_data_$profileId", null) ?: return null
            SecureCryptoVault.decryptString(encryptedEnvelope, pin)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Panic Wipe: completely clears all profile storage, secrets, and caches.
     */
    fun panicWipeAll() {
        prefs.edit().clear().apply()
        context.getSharedPreferences("simplex_keys_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        availableProfiles.clear()
        val freshDecoy = getDefaultDecoyProfile()
        availableProfiles.add(freshDecoy)
        activeProfile = freshDecoy
    }
}
