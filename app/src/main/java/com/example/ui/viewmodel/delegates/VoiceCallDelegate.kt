package com.example.ui.viewmodel.delegates

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.model.MatchAudioMode
import com.example.model.SimplexVoiceMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Делегат голосовой рации Walkie-Talkie, P2P аудио-сообщений и управления аудиопотоками.
 */
class VoiceCallDelegate(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val simplexVoiceMessages = mutableStateListOf<SimplexVoiceMessage>()

    var matchAudioMode by mutableStateOf(MatchAudioMode.WALKIE_TALKIE)
    var isWalkieTalkieMuted by mutableStateOf(false)

    var isRecordingVoice by mutableStateOf(false)
        private set

    var recordingDurationSec by mutableIntStateOf(0)
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioPath: String? = null

    fun playVoiceMessage(msg: SimplexVoiceMessage, onBurned: () -> Unit = {}) {
        if (isWalkieTalkieMuted) return
        stopAndReleasePlayer()

        simplexVoiceMessages.forEachIndexed { idx, m ->
            if (m.isPlaying) {
                simplexVoiceMessages[idx] = m.copy(isPlaying = false, playProgress = 0f)
            }
        }

        val index = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
        if (index == -1) return

        simplexVoiceMessages[index] = msg.copy(isPlaying = true)
        val hasRealAudio = msg.audioPath != null && File(msg.audioPath).exists()

        if (hasRealAudio) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(msg.audioPath)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                val tg = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Animate 6-second walkie-talkie progress and burn message on finish
        scope.launch {
            val totalMs = 6000L
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < totalMs) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                val idx = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
                if (idx != -1 && simplexVoiceMessages[idx].isPlaying) {
                    simplexVoiceMessages[idx] = simplexVoiceMessages[idx].copy(playProgress = progress)
                } else {
                    break
                }
                delay(100)
            }

            // Roger Beep
            try {
                val tg = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 250)
            } catch (_: Exception) {}

            // Immediate Burn on play finish
            val idx = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
            if (idx != -1) {
                val msgToRemove = simplexVoiceMessages[idx]
                simplexVoiceMessages.removeAt(idx)
                msgToRemove.audioPath?.let { path ->
                    try {
                        val f = File(path)
                        if (f.exists()) f.delete()
                    } catch (_: Exception) {}
                }
                onBurned()
            }

            stopAndReleasePlayer()
        }
    }

    fun startVoiceRecording(onPermissionMissing: () -> Unit, onError: (String) -> Unit) {
        if (isRecordingVoice) return

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionMissing()
            return
        }

        isRecordingVoice = true
        recordingDurationSec = 0

        try {
            stopAndReleasePlayer()
            val audioFile = File(context.cacheDir, "walkie_talkie_${System.currentTimeMillis()}.3gp")
            currentAudioPath = audioFile.absolutePath

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile(currentAudioPath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
        } catch (e: Exception) {
            isRecordingVoice = false
            onError(e.localizedMessage ?: "Recording failed")
            return
        }

        scope.launch {
            while (isRecordingVoice) {
                delay(1000)
                if (!isRecordingVoice) break
                recordingDurationSec++
                if (recordingDurationSec >= 6) {
                    break
                }
            }
        }
    }

    fun stopRecording(): String? {
        if (!isRecordingVoice) return null
        isRecordingVoice = false
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }
        return currentAudioPath
    }

    fun stopAndReleasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
    }
}
