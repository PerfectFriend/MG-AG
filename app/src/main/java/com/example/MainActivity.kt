package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ui.GameViewModel
import com.example.ui.GameViewModelFactory
import com.example.ui.screens.GameScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // After standard runtime permissions are handled, proceed to VPN checks
        checkAndRequestVpnPermission()
    }

    private val vpnPrepareLauncherAtStartup = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // VPN setup handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        // Configure system bars to be fully hidden initially
        hideSystemUI()

        // Step 1: Request standard permissions at launch (Camera, Audio, Notifications)
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())

        // Create the stateful game viewModel using local context factory
        val viewModel = ViewModelProvider(
            this,
            GameViewModelFactory(applicationContext)
        )[GameViewModel::class.java]

        // Ensure FLAG_SECURE is explicitly cleared by default so streaming emulators render video correctly
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (viewModel.isScreenSecurityEnabled) {
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        viewModel.onScreenSecurityChanged = { enabled ->
            if (enabled) {
                window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        // System starts in PORTRAIT orientation by default.
        // Landscape is only requested dynamically when entering active game mode.
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContent {
            MyApplicationTheme(themeId = viewModel.selectedTheme) {
                GameScreen(viewModel = viewModel)
            }
        }
    }

    private fun checkAndRequestVpnPermission() {
        try {
            val prepIntent = VpnService.prepare(this)
            if (prepIntent != null) {
                vpnPrepareLauncherAtStartup.launch(prepIntent)
            } else {
                // VPN is already authorized/prepared
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        try {
            val viewModel = ViewModelProvider(this)[GameViewModel::class.java]
            viewModel.radioManager.stop()
            viewModel.vpnManager.stopVpnConnectionSimulation()
            viewModel.setTorEnabledState(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
