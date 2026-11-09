package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProfileManager
import com.example.model.DuressAction
import com.example.model.ProfileType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileManagerRoboTest {

    private lateinit var context: Context
    private lateinit var profileManager: ProfileManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        profileManager = ProfileManager(context)
    }

    @Test
    fun testDefaultProfileInitialization() {
        val active = profileManager.activeProfile
        assertNotNull(active)
        assertEquals(ProfileType.PRIMARY, active.profileType)
        assertFalse(active.isDecoy)
        assertTrue(profileManager.availableProfiles.isNotEmpty())
    }

    @Test
    fun testCreateNewProfileAndSwitch() {
        val created = profileManager.createProfile("Secret Ops", "ops_42", ProfileType.INCOGNITO)
        assertNotNull(created)
        assertEquals("Secret Ops", created.name)
        assertEquals("ops_42", created.handle)

        val switched = profileManager.switchProfile(created.id)
        assertNotNull(switched)
        assertEquals(created.id, profileManager.activeProfile.id)
    }

    @Test
    fun testDuressConfigurationUpdate() {
        profileManager.updateDuressConfig("999999", DuressAction.PANIC_WIPE)
        assertEquals("999999", profileManager.duressPinCode)
        assertEquals(DuressAction.PANIC_WIPE, profileManager.duressAction)
    }

    @Test
    fun testDuressPinTriggerHandling() {
        profileManager.updateDuressConfig("666666", DuressAction.DECOY_PROFILE)
        
        // Non-duress PIN returns null
        val normalResult = profileManager.checkDuressPin("123456")
        assertNull(normalResult)

        // Duress PIN triggers configured action
        val duressResult = profileManager.checkDuressPin("666666")
        assertEquals(DuressAction.DECOY_PROFILE, duressResult)
    }
}
