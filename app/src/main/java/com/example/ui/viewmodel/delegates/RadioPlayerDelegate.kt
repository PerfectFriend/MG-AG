package com.example.ui.viewmodel.delegates

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.audio.DiceSoundPlayer
import com.example.audio.RadioManager
import com.example.ui.GameViewModel.MatchAudioMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Делегат управления аудиосистемой, интернет-радио и звуковыми эффектами игры.
 *
 * Отвечает за:
 * - Воспроизведение звуков броска зариков и перемещения фишек.
 * - Управление потоковым радио через [RadioManager].
 * - Переключение режимов аудио матча (тишина, только звуки доски, фоновое радио, рация).
 * - Безопасное освобождение медиа-ресурсов при выходе из приложения.
 *
 * @param context Контекст приложения Android.
 * @param scope CoroutineScope ViewModel для выполнения асинхронного воспроизведения аудио.
 */
class RadioPlayerDelegate(
    context: Context,
    private val scope: CoroutineScope
) {
    val radioManager = RadioManager(context)
    val soundPlayer = DiceSoundPlayer()

    var matchAudioMode by mutableStateOf(MatchAudioMode.RADIO)
        private set

    var isVoiceCommentaryEnabled by mutableStateOf(false)
    var isAutoPlayVoiceEnabled by mutableStateOf(false)

    /**
     * Воспроизводит реалистичный звук перекатывания костей (зариков).
     */
    fun playRollSound() {
        scope.launch {
            try {
                soundPlayer.playRollSound()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Воспроизводит звук удара деревянной фишки о пункт доски.
     */
    fun playMoveSound() {
        scope.launch {
            try {
                soundPlayer.playMoveSound()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Переключает режим аудиосопровождения матча.
     */
    fun updateMatchAudioMode(mode: MatchAudioMode) {
        matchAudioMode = mode
        when (mode) {
            MatchAudioMode.SILENCE -> {
                radioManager.stop()
            }
            MatchAudioMode.RADIO -> {
                val channel = radioManager.currentChannel ?: radioManager.channels.firstOrNull()
                if (channel != null && !radioManager.isPlaying) {
                    radioManager.play(channel)
                }
            }
            MatchAudioMode.WALKIE_TALKIE -> {
                radioManager.stop()
            }
        }
    }

    /**
     * Синтезирует голосовой комментарий хода (отключено по запросу пользователя).
     */
    fun speakCommentary(text: String) {
        // Disabled per user requirements
    }

    fun toggleVoiceCommentary() {
        isVoiceCommentaryEnabled = false
    }

    /**
     * Освобождает все аудиоресурсы и останавливает потоки воспроизведения.
     */
    fun release() {
        try {
            radioManager.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            soundPlayer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
