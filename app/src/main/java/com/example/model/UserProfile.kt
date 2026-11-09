package com.example.model

enum class ProfileType {
    PRIMARY,
    HIDDEN,
    INCOGNITO,
    DECOY
}

enum class DuressAction {
    DECOY_PROFILE,
    PANIC_WIPE,
    NETWORK_ERROR
}

data class UserProfile(
    val id: String,
    val name: String,
    val handle: String,
    val profileType: ProfileType = ProfileType.PRIMARY,
    val avatarColor: Long = 0xFF00FF66,
    val saltHex: String = "",
    val isDecoy: Boolean = false,
    val isSelfDestructOnExit: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
