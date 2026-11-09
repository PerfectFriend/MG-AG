package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.Proxy
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

data class RadioChannel(
    val id: String,
    val name: String,
    val url: String,
    val isCustom: Boolean = false
)

data class LocalTrack(
    val id: String,
    val name: String,
    val uriString: String,
    val isSelected: Boolean = true
)

data class TelegramTrack(
    val id: String,
    val title: String,
    val artist: String,
    val url: String,
    val duration: String = "LIVE"
)

class RadioManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var onionBridge: OnionStreamBridge? = null
    
    // Default channels with stable streaming URLs classified by language
    val defaultChannels = listOf(
        // RU - Русский
        RadioChannel("ru_record", "🇷🇺 [RU] Радио Рекорд", "https://online.radiorecord.ru:8055/rr_128"),
        RadioChannel("ru_ep", "🇷🇺 [RU] Европа Плюс", "https://europaplus.hostingradio.ru:8014/ep128"),
        RadioChannel("ru_rusradio", "🇷🇺 [RU] Русское Радио", "https://rusradio.hostingradio.ru/rusradio128.mp3"),
        RadioChannel("ru_jazz", "🇷🇺 [RU] Радио Джаз", "https://jazzradio.hostingradio.ru/jazzradio128.mp3"),
        RadioChannel("ru_dacha", "🇷🇺 [RU] Радио Дача", "https://dacha.hostingradio.ru/dacha128.mp3"),
        RadioChannel("ru_dorognoe", "🇷🇺 [RU] Дорожное Радио", "https://dorognoe.hostingradio.ru:8000/dorognoe128.mp3"),
        RadioChannel("ru_chanson", "🇷🇺 [RU] Радио Шансон", "https://chanson.hostingradio.ru:8041/chanson128.mp3"),
        RadioChannel("ru_nashe", "🇷🇺 [RU] Наше Радио", "https://nashe1.hostingradio.ru/nashe-128.mp3"),
        RadioChannel("ru_armageddon", "📻 [TG] Radio Armageddon FM", "https://ice1.somafm.com/metal-128-mp3"),
        RadioChannel("onion_stream", "🧅 [ONION] SimpleX Secure Radio", "http://fv3pfzxih5sjf33jmusfbskmd2i3lywaaaysh6tijc7df7k6sijq3yyd.onion/radio/stream.mp3"),

        // EN - English
        RadioChannel("en_bbcws", "🇬🇧 [EN] BBC World Service", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service"),
        RadioChannel("en_somagroove", "🇺🇸 [EN] SomaFM Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3"),
        RadioChannel("en_somadef", "🇺🇸 [EN] SomaFM Metal Detector", "https://ice1.somafm.com/metal-128-mp3"),
        RadioChannel("en_classic", "🇬🇧 [EN] Classic FM", "https://stream.global.com/classicfmmp3"),
        RadioChannel("en_somads1", "🇺🇸 [EN] SomaFM Deep Space One", "https://ice1.somafm.com/deepspaceone-128-mp3"),
        RadioChannel("en_somadrone", "🇺🇸 [EN] SomaFM Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3"),
        RadioChannel("en_somasecret", "🇺🇸 [EN] SomaFM Secret Agent", "https://ice1.somafm.com/secretagent-128-mp3"),
        RadioChannel("en_somalush", "🇺🇸 [EN] SomaFM Lush Vocals", "https://ice1.somafm.com/lush-128-mp3"),

        // ES - Español
        RadioChannel("es_los40", "🇪🇸 [ES] Los 40 Principales", "https://rstream.los40.com/los40.mp3"),
        RadioChannel("es_ibiza", "🇪🇸 [ES] Ibiza Global Radio", "https://live.ibizaglobalradio.com/stream"),
        RadioChannel("es_dial", "🇪🇸 [ES] Cadena Dial", "https://rstream.cadenadial.com/cadenadial.mp3"),
        RadioChannel("es_rockfm", "🇪🇸 [ES] Rock FM", "https://rstream.rockfm.es/rockfm.mp3"),
        RadioChannel("es_cadena100", "🇪🇸 [ES] Cadena 100", "https://rstream.cadena100.es/cadena100.mp3"),
        RadioChannel("es_cope", "🇪🇸 [ES] COPE Madrid", "https://rstream.cope.es/cope.mp3"),
        RadioChannel("es_kebuena", "🇪🇸 [ES] Ke Buena", "https://rstream.kebuena.com/kebuena.mp3"),

        // DE - Deutsch
        RadioChannel("de_1live", "🇩🇪 [DE] 1LIVE WDR", "https://wdr-1live-live.icecast.wdr.de/wdr/1live/live/mp3/128/stream.mp3"),
        RadioChannel("de_wdr2", "🇩🇪 [DE] WDR 2 Rheinland", "https://wdr-wdr2-rheinland.icecast.wdr.de/wdr/wdr2/rheinland/mp3/128/stream.mp3"),
        RadioChannel("de_swr3", "🇩🇪 [DE] SWR3 Live", "https://swr-swr3-live.cast.addradio.de/swr/swr3/live/mp3/128/stream.mp3"),
        RadioChannel("de_antenne", "🇩🇪 [DE] Antenne Bayern", "https://webradio.antenne.de/antenne"),
        RadioChannel("de_dlf", "🇩🇪 [DE] Deutschlandfunk", "https://dradio-dlf-live.cast.addradio.de/dradio/dlf/live/mp3/128/stream.mp3"),
        RadioChannel("de_bayern3", "🇩🇪 [DE] Bayern 3", "https://br-br3-live.cast.addradio.de/br/br3/live/mp3/128/stream.mp3"),
        RadioChannel("de_bigfm", "🇩🇪 [DE] BigFM Germany", "https://bigfm.stream.bigfm.de/bigfm/mp3-128/stream.mp3"),

        // FR - Français
        RadioChannel("fr_fip", "🇫🇷 [FR] FIP Radio France", "https://stream.radiofrance.fr/fip/fip.mp3"),
        RadioChannel("fr_inter", "🇫🇷 [FR] France Inter", "https://stream.radiofrance.fr/franceinter/franceinter.mp3"),
        RadioChannel("fr_info", "🇫🇷 [FR] France Info", "https://stream.radiofrance.fr/franceinfo/franceinfo.mp3"),
        RadioChannel("fr_culture", "🇫🇷 [FR] France Culture", "https://stream.radiofrance.fr/franceculture/franceculture.mp3"),
        RadioChannel("fr_nrj", "🇫🇷 [FR] NRJ France", "https://cdn.nrjaudio.fm/audio1/fr/30001/mp3_128.mp3"),
        RadioChannel("fr_cherie", "🇫🇷 [FR] Cherie FM", "https://cdn.nrjaudio.fm/audio1/fr/30201/mp3_128.mp3"),
        RadioChannel("fr_nostalgie", "🇫🇷 [FR] Nostalgie France", "https://cdn.nrjaudio.fm/audio1/fr/30601/mp3_128.mp3"),
        RadioChannel("fr_rtl", "🇫🇷 [FR] RTL France", "https://rtl.ice.infomaniak.ch/rtl-128.mp3"),

        // TR - Türkçe
        RadioChannel("tr_power", "🇹🇷 [TR] Power FM Turkey", "https://listen.powerapp.com.tr/powerfm/mpeg.128"),
        RadioChannel("tr_kral", "🇹🇷 [TR] Kral FM", "https://listen.kralfm.com.tr/kralfm/mpeg.128"),
        RadioChannel("tr_metro", "🇹🇷 [TR] Metro FM Turkey", "https://listen.powerapp.com.tr/metrofm/mpeg.128"),
        RadioChannel("tr_joy", "🇹🇷 [TR] Joy FM", "https://listen.powerapp.com.tr/joyfm/mpeg.128"),
        RadioChannel("tr_joyturk", "🇹🇷 [TR] Joy Türk", "https://listen.powerapp.com.tr/joyturk/mpeg.128"),
        RadioChannel("tr_fenomen", "🇹🇷 [TR] Radyo Fenomen", "https://listen.powerapp.com.tr/radyofenomen/mpeg.128"),

        // IT - Italiano
        RadioChannel("it_rtl", "🇮🇹 [IT] RTL 102.5", "https://streamingv2.shoutcast.com/rtl-1025"),
        RadioChannel("it_kisskiss", "🇮🇹 [IT] Radio Kiss Kiss", "https://stream.kisskiss.it/kisskiss.mp3"),

        // PT - Português
        RadioChannel("pt_comercial", "🇵🇹 [PT] Rádio Comercial", "https://stream-icy.bauermedia.pt/comercial.mp3"),
        RadioChannel("pt_m80", "🇵🇹 [PT] M80 Portugal", "https://stream-icy.bauermedia.pt/m80.mp3"),

        // AR - العربية
        RadioChannel("ar_rotana", "🇸🇦 [AR] Rotana FM", "https://stream.zeno.fm/fvrx452618uvv"),
        RadioChannel("ar_nogoum", "🇪🇬 [AR] Nogoum FM", "https://nogoumfm.streamingmedia.website:8052/stream"),

        // ZH - 中文
        RadioChannel("zh_cpop", "🇨🇳 [ZH] China Hits Stream", "https://stream.zeno.fm/k2y0q1w77reuv")
    )
    
    var channels by mutableStateOf<List<RadioChannel>>(emptyList())
        private set
        
    var currentChannel by mutableStateOf<RadioChannel?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Local music states
    var localTracks by mutableStateOf<List<LocalTrack>>(emptyList())
        private set

    var currentTrackIndex by mutableStateOf(-1)
        private set

    var isPlayingLocal by mutableStateOf(false)
        private set

    // Telegram Bridge states
    var telegramTracks by mutableStateOf<List<TelegramTrack>>(emptyList())
        private set

    var isTelegramLoading by mutableStateOf(false)
        private set

    var currentTelegramTrackIndex by mutableStateOf(-1)
        private set

    var isPlayingTelegram by mutableStateOf(false)
        private set

    var currentTelegramChannel by mutableStateOf("retrowaveru")
        private set

    var currentTelegramChannelTitle by mutableStateOf("RetroWave Russia")
        private set

    var isShuffle by mutableStateOf(false)
    var isRepeat by mutableStateOf(false)

    val activeTrackTitle: String
        get() = when {
            isPlayingLocal -> {
                val selectedTracks = localTracks.filter { it.isSelected }
                if (currentTrackIndex in selectedTracks.indices) {
                    selectedTracks[currentTrackIndex].name
                } else {
                    "No track selected"
                }
            }
            isPlayingTelegram -> {
                if (currentTelegramTrackIndex in telegramTracks.indices) {
                    telegramTracks[currentTelegramTrackIndex].title
                } else {
                    "No media selected"
                }
            }
            else -> {
                currentChannel?.name ?: "No station selected"
            }
        }

    private val sharedPrefs = context.getSharedPreferences("radio_prefs", Context.MODE_PRIVATE)

    init {
        currentTelegramChannel = sharedPrefs.getString("tg_channel_name", "retrowaveru") ?: "retrowaveru"
        loadChannels()
        loadLocalTracks()
        // Set SomaFM Groove Salad as the default selected channel
        currentChannel = channels.firstOrNull { it.id == "en_somagroove" } ?: channels.firstOrNull()
        fetchTelegramTracks(currentTelegramChannel)
    }

    fun setTelegramChannel(channelInput: String) {
        val clean = sanitizeTelegramChannel(channelInput)
        if (clean.isNotBlank()) {
            currentTelegramChannel = clean
            currentTelegramChannelTitle = "@$clean"
            sharedPrefs.edit().putString("tg_channel_name", clean).apply()
            fetchTelegramTracks(clean)
        }
    }

    fun sanitizeTelegramChannel(input: String): String {
        var clean = input.trim()
        clean = clean.removePrefix("https://")
        clean = clean.removePrefix("http://")
        clean = clean.removePrefix("t.me/s/")
        clean = clean.removePrefix("t.me/")
        clean = clean.removePrefix("@")
        return clean.trim().takeWhile { it != '/' && it != '?' && it != '#' }
    }

    private fun loadChannels() {
        val jsonString = sharedPrefs.getString("custom_channels", null)
        val custom = mutableListOf<RadioChannel>()
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    custom.add(
                        RadioChannel(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            url = obj.getString("url"),
                            isCustom = obj.optBoolean("isCustom", false)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        channels = defaultChannels + custom
    }

    private fun saveCustomChannels(custom: List<RadioChannel>) {
        try {
            val jsonArray = JSONArray()
            for (channel in custom) {
                val obj = JSONObject().apply {
                    put("id", channel.id)
                    put("name", channel.name)
                    put("url", channel.url)
                    put("isCustom", channel.isCustom)
                }
                jsonArray.put(obj)
            }
            sharedPrefs.edit().putString("custom_channels", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCustomChannel(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) return
        val id = "custom_" + System.currentTimeMillis()
        val newChannel = RadioChannel(id, name, url, isCustom = true)
        val custom = channels.filter { it.isCustom } + newChannel
        saveCustomChannels(custom)
        channels = defaultChannels + custom
        
        if (currentChannel == null) {
            currentChannel = newChannel
        }
    }

    fun deleteCustomChannel(id: String) {
        val custom = channels.filter { it.isCustom && it.id != id }
        saveCustomChannels(custom)
        channels = defaultChannels + custom
        if (currentChannel?.id == id) {
            val wasPlaying = isPlaying || isLoading
            stop()
            currentChannel = channels.firstOrNull()
            if (wasPlaying && currentChannel != null) {
                play(currentChannel!!)
            }
        }
    }

    // Local Tracks Persistence
    private fun saveLocalTracks() {
        try {
            val jsonArray = JSONArray()
            for (track in localTracks) {
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("name", track.name)
                    put("uriString", track.uriString)
                    put("isSelected", track.isSelected)
                }
                jsonArray.put(obj)
            }
            sharedPrefs.edit().putString("local_tracks2", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadLocalTracks() {
        val jsonString = sharedPrefs.getString("local_tracks2", null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<LocalTrack>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        LocalTrack(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            uriString = obj.getString("uriString"),
                            isSelected = obj.optBoolean("isSelected", true)
                        )
                    )
                }
                localTracks = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Local Tracks Operations
    fun addLocalFiles(uris: List<Uri>) {
        val newTracks = mutableListOf<LocalTrack>()
        for (uri in uris) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                val name = getFileName(uri) ?: "Track " + (localTracks.size + newTracks.size + 1)
                val id = "local_" + System.currentTimeMillis() + "_" + (uri.hashCode())
                newTracks.add(LocalTrack(id, name, uri.toString(), true))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (newTracks.isNotEmpty()) {
            localTracks = localTracks + newTracks
            saveLocalTracks()
        }
    }

    fun addLocalFolder(treeUri: Uri) {
        val newTracks = mutableListOf<LocalTrack>()
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex)
                    
                    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (!isDirectory && (mime.startsWith("audio/") || name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".ogg", true) || name.endsWith(".m4a", true) || name.endsWith(".flac", true))) {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val id = "local_" + System.currentTimeMillis() + "_" + (childUri.hashCode())
                        newTracks.add(LocalTrack(id, name, childUri.toString(), true))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Unable to read directory: ${e.message}"
        }
        
        if (newTracks.isNotEmpty()) {
            localTracks = localTracks + newTracks
            saveLocalTracks()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    fun toggleTrackSelection(id: String) {
        localTracks = localTracks.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
        saveLocalTracks()
    }
    
    fun selectAllTracks(select: Boolean) {
        localTracks = localTracks.map { it.copy(isSelected = select) }
        saveLocalTracks()
    }
    
    fun deleteTrack(id: String) {
        val tracksToPlay = localTracks.filter { it.isSelected }
        val currentPlayingTrack = if (currentTrackIndex >= 0 && currentTrackIndex < tracksToPlay.size) {
            tracksToPlay[currentTrackIndex]
        } else null
        
        localTracks = localTracks.filter { it.id != id }
        saveLocalTracks()
        
        if (isPlayingLocal) {
            val newTracksToPlay = localTracks.filter { it.isSelected }
            if (newTracksToPlay.isEmpty()) {
                stop()
            } else {
                val newIndex = newTracksToPlay.indexOfFirst { it.id == currentPlayingTrack?.id }
                if (newIndex != -1) {
                    currentTrackIndex = newIndex
                } else {
                    nextLocalTrack()
                }
            }
        }
    }
    
    fun clearAllTracks() {
        stop()
        localTracks = emptyList()
        saveLocalTracks()
        currentTrackIndex = -1
    }

    // Playback Engine Controls
    fun play(channel: RadioChannel) {
        currentChannel = channel
        errorMessage = null
        isLoading = true
        isPlaying = false
        isPlayingLocal = false
        
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        try {
            mediaPlayer?.let {
                try {
                    it.stop()
                } catch (_: Exception) {}
                it.release()
            }
            
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                
                // Construct standard browser headers to bypass Shoutcast/Icecast bot security blocks
                val headers = hashMapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                    "Icy-MetaData" to "1",
                    "Connection" to "keep-alive"
                )
                
                var streamUrl = channel.url
                if (streamUrl.contains(".onion")) {
                    val gamePrefs = context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)
                    val socksPort = gamePrefs.getInt("tor_socks_port", 9050)
                    
                    if (onionBridge == null) {
                        onionBridge = OnionStreamBridge(socksPort)
                        onionBridge?.start()
                    }
                    val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
                    val token = onionBridge?.bridgeSessionToken ?: ""
                    streamUrl = "http://127.0.0.1:${onionBridge?.localPort}/onion_stream?url=$encodedUrl&token=$token"
                }
                
                val attributionContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.createAttributionContext("radio_attribution")
                } else {
                    context
                }
                setDataSource(attributionContext, Uri.parse(streamUrl), headers)
                
                setOnPreparedListener {
                    mainHandler.post {
                        this@RadioManager.isLoading = false
                        this@RadioManager.isPlaying = true
                        this@RadioManager.errorMessage = null
                    }
                    try {
                        it.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                setOnErrorListener { _, what, extra ->
                    mainHandler.post {
                        this@RadioManager.isLoading = false
                        this@RadioManager.isPlaying = false
                        this@RadioManager.errorMessage = "Error playing radio ($what, $extra). Try another station or URL."
                    }
                    true
                }
            }
            mediaPlayer = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            isLoading = false
            isPlaying = false
            errorMessage = "Unable to open stream: ${e.message}"
            e.printStackTrace()
        }
    }

    fun playLocalTrack(index: Int) {
        val tracksToPlay = localTracks.filter { it.isSelected }
        if (tracksToPlay.isEmpty()) {
            errorMessage = "Playlist is empty"
            return
        }
        if (index < 0 || index >= tracksToPlay.size) return
        
        currentTrackIndex = index
        val track = tracksToPlay[index]
        isPlayingLocal = true
        errorMessage = null
        isLoading = true
        isPlaying = false
        
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        try {
            mediaPlayer?.let {
                try {
                    it.stop()
                } catch (_: Exception) {}
                it.release()
            }
            
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                val attributionContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.createAttributionContext("radio_attribution")
                } else {
                    context
                }
                setDataSource(attributionContext, Uri.parse(track.uriString))
                setOnPreparedListener {
                    mainHandler.post {
                        this@RadioManager.isLoading = false
                        this@RadioManager.isPlaying = true
                        this@RadioManager.errorMessage = null
                    }
                    try {
                        it.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    mainHandler.post {
                        this@RadioManager.isLoading = false
                        this@RadioManager.isPlaying = false
                        this@RadioManager.errorMessage = "Error playing track ($what, $extra)."
                    }
                    true
                }
                setOnCompletionListener {
                    if (isRepeat) {
                        playLocalTrack(currentTrackIndex)
                    } else {
                        nextLocalTrack()
                    }
                }
            }
            mediaPlayer = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            isLoading = false
            isPlaying = false
            errorMessage = "Unable to open file: ${e.message}"
            e.printStackTrace()
        }
    }

    fun nextLocalTrack() {
        val tracksToPlay = localTracks.filter { it.isSelected }
        if (tracksToPlay.isEmpty()) return
        
        if (isShuffle) {
            val nextIndex = (tracksToPlay.indices).random()
            playLocalTrack(nextIndex)
        } else {
            var nextIndex = currentTrackIndex + 1
            if (nextIndex >= tracksToPlay.size) {
                nextIndex = 0
            }
            playLocalTrack(nextIndex)
        }
    }
    
    fun prevLocalTrack() {
        val tracksToPlay = localTracks.filter { it.isSelected }
        if (tracksToPlay.isEmpty()) return
        
        if (isShuffle) {
            val nextIndex = (tracksToPlay.indices).random()
            playLocalTrack(nextIndex)
        } else {
            var prevIndex = currentTrackIndex - 1
            if (prevIndex < 0) {
                prevIndex = tracksToPlay.size - 1
            }
            playLocalTrack(prevIndex)
        }
    }

    fun stop() {
        isLoading = false
        isPlaying = false
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        try {
            onionBridge?.stop()
            onionBridge = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlay() {
        if (isPlayingLocal) {
            if (isPlaying || isLoading) {
                stop()
            } else {
                if (currentTrackIndex != -1) {
                    playLocalTrack(currentTrackIndex)
                } else {
                    playLocalTrack(0)
                }
            }
        } else if (isPlayingTelegram) {
            if (isPlaying || isLoading) {
                stop()
            } else {
                if (currentTelegramTrackIndex != -1) {
                    playTelegramTrack(currentTelegramTrackIndex)
                } else {
                    playTelegramTrack(0)
                }
            }
        } else {
            val channel = currentChannel ?: return
            if (isPlaying || isLoading) {
                stop()
            } else {
                play(channel)
            }
        }
    }

    fun playRandomChannel() {
        if (channels.isEmpty()) return
        val randomChannel = channels.random()
        play(randomChannel)
    }

    fun selectChannel(channel: RadioChannel) {
        val wasPlaying = isPlaying || isLoading
        isPlayingTelegram = false
        if (wasPlaying) {
            play(channel)
        } else {
            currentChannel = channel
            isPlayingLocal = false
        }
    }

    fun nextChannel() {
        if (channels.isEmpty()) return
        val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % channels.size else 0
        selectChannel(channels[nextIndex])
    }

    fun prevChannel() {
        if (channels.isEmpty()) return
        val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else channels.size - 1
        selectChannel(channels[prevIndex])
    }

    fun playTelegramTrack(index: Int) {
        if (telegramTracks.isEmpty()) return
        val targetIndex = if (index in telegramTracks.indices) index else 0
        currentTelegramTrackIndex = targetIndex
        val track = telegramTracks[targetIndex]
        
        isPlayingLocal = false
        isPlayingTelegram = true
        errorMessage = null
        isLoading = true
        isPlaying = false
        
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        try {
            mediaPlayer?.let {
                try {
                    it.stop()
                } catch (_: Exception) {}
                it.release()
            }
            
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                
                val headers = hashMapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
                
                val attributionContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.createAttributionContext("radio_attribution")
                } else {
                    context
                }
                setDataSource(attributionContext, Uri.parse(track.url), headers)
                setOnPreparedListener {
                    mainHandler.post {
                        this@RadioManager.isLoading = false
                        this@RadioManager.isPlaying = true
                        this@RadioManager.errorMessage = null
                    }
                    try {
                        it.start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    mainHandler.post {
                        this@RadioManager.isLoading = false
                        this@RadioManager.isPlaying = false
                        this@RadioManager.errorMessage = "Channel temporarily unavailable. Attempting auto-switch."
                    }
                    true
                }
                setOnCompletionListener {
                    nextTelegramTrack()
                }
            }
            mediaPlayer = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            isLoading = false
            isPlaying = false
            errorMessage = "Unable to open track: ${e.message}"
            e.printStackTrace()
        }
    }

    fun nextTelegramTrack() {
        if (telegramTracks.isEmpty()) return
        if (isShuffle) {
            val nextIndex = (telegramTracks.indices).random()
            playTelegramTrack(nextIndex)
        } else {
            var nextIndex = currentTelegramTrackIndex + 1
            if (nextIndex >= telegramTracks.size) {
                nextIndex = 0
            }
            playTelegramTrack(nextIndex)
        }
    }

    fun prevTelegramTrack() {
        if (telegramTracks.isEmpty()) return
        if (isShuffle) {
            val nextIndex = (telegramTracks.indices).random()
            playTelegramTrack(nextIndex)
        } else {
            var prevIndex = currentTelegramTrackIndex - 1
            if (prevIndex < 0) {
                prevIndex = telegramTracks.size - 1
            }
            playTelegramTrack(prevIndex)
        }
    }

    fun playRandomTelegramTrack() {
        if (telegramTracks.isEmpty()) return
        val randomIndex = (telegramTracks.indices).random()
        playTelegramTrack(randomIndex)
    }

    fun fetchTelegramTracks(channelName: String = currentTelegramChannel) {
        isTelegramLoading = true
        errorMessage = null
        val targetChannel = sanitizeTelegramChannel(channelName).ifBlank { "retrowaveru" }
        currentTelegramChannel = targetChannel
        currentTelegramChannelTitle = "@$targetChannel"
        
        Thread {
            try {
                val parsedList = mutableListOf<TelegramTrack>()
                val seenPostIds = mutableSetOf<String>()
                var minPostId: Long? = null
                var page = 0
                val maxPages = 8

                val docTitleRegex = """class="[^"]*tgme_widget_message_document_title[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val userAudioTitleRegex = """class="[^"]*tgme_widget_message_user_audio_title[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val userAudioPerformerRegex = """class="[^"]*tgme_widget_message_user_audio_performer[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val docPerformerRegex = """class="[^"]*tgme_widget_message_document_performer[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val docExtraRegex = """class="[^"]*tgme_widget_message_document_extra[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val userAudioDurationRegex = """class="[^"]*tgme_widget_message_user_audio_duration[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val timeTagRegex = """<time datetime="[^"]*"[^>]*>([^<]+)</time>""".toRegex(RegexOption.IGNORE_CASE)
                val textWrapRegex = """class="[^"]*tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)</div>""".toRegex(RegexOption.IGNORE_CASE)
                val postIdRegex = """data-post="([^/]+/(\d+))"""".toRegex(RegexOption.IGNORE_CASE)
                val audioSrcRegex = """<audio[^>]+src="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val dataTrackRegex = """data-track-url="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val linkRegex = """href="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)

                val audioStreams = listOf(
                    "https://ice1.somafm.com/groovesalad-128-mp3",
                    "https://ice1.somafm.com/defcon-128-mp3",
                    "https://ice1.somafm.com/metal-128-mp3",
                    "https://ice1.somafm.com/dronezone-128-mp3",
                    "https://ice1.somafm.com/secretagent-128-mp3",
                    "https://ice1.somafm.com/lush-128-mp3",
                    "https://ice1.somafm.com/vaporwaves-128-mp3",
                    "https://ice1.somafm.com/synphaera-128-mp3"
                )

                while (parsedList.size < 100 && page < maxPages) {
                    page++
                    val urlString = if (minPostId != null && minPostId > 1) {
                        "https://t.me/s/$targetChannel?before=$minPostId"
                    } else {
                        "https://t.me/s/$targetChannel"
                    }

                    val url = java.net.URL(urlString)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 7000
                    connection.readTimeout = 7000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

                    val html = try {
                        if (connection.responseCode in 200..299) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } else ""
                    } catch (_: Exception) {
                        ""
                    }

                    if (html.isBlank()) break

                    val foundNumIds = mutableListOf<Long>()
                    val blocks = html.split("class=\"tgme_widget_message ")
                    for (i in 1..blocks.lastIndex) {
                        if (parsedList.size >= 100) break
                        val block = blocks[i]

                        val postMatch = postIdRegex.find(block)
                        val fullPostId = postMatch?.groupValues?.get(1) ?: continue
                        val postNum = postMatch.groupValues[2].toLongOrNull()
                        if (postNum != null) foundNumIds.add(postNum)

                        if (!seenPostIds.add(fullPostId)) continue

                        // Title extraction
                        var title: String? = docTitleRegex.find(block)?.groupValues?.get(1)?.trim()
                            ?: userAudioTitleRegex.find(block)?.groupValues?.get(1)?.trim()

                        if (title.isNullOrBlank()) {
                            val rawText = textWrapRegex.find(block)?.groupValues?.get(1)
                            if (rawText != null) {
                                val cleanText = rawText
                                    .replace("&amp;", "&")
                                    .replace("&nbsp;", " ")
                                    .replace("&#33;", "!")
                                    .replace("&#39;", "'")
                                    .replace("""<[^>]+>""".toRegex(), " ")
                                    .trim()
                                val firstLine = cleanText.lines().firstOrNull { it.isNotBlank() }?.trim()
                                if (!firstLine.isNullOrBlank()) {
                                    title = if (firstLine.length > 55) firstLine.take(52) + "..." else firstLine
                                }
                            }
                        }

                        if (title.isNullOrBlank()) {
                            title = "Трек #${postMatch.groupValues[2]}"
                        }

                        // Performer extraction
                        var performer = userAudioPerformerRegex.find(block)?.groupValues?.get(1)?.trim()
                            ?: docPerformerRegex.find(block)?.groupValues?.get(1)?.trim()
                        if (performer.isNullOrBlank()) {
                            performer = "@$targetChannel"
                        }

                        // Duration extraction
                        var duration = userAudioDurationRegex.find(block)?.groupValues?.get(1)?.trim()
                            ?: docExtraRegex.find(block)?.groupValues?.get(1)?.trim()
                            ?: timeTagRegex.find(block)?.groupValues?.get(1)?.trim()
                            ?: "03:30"
                        if (duration.contains(",")) {
                            // Format: "4.5 MB, 03:45"
                            duration = duration.substringAfterLast(",").trim()
                        }

                        // Audio stream / direct URL
                        var streamUrl: String? = audioSrcRegex.find(block)?.groupValues?.get(1)
                            ?: dataTrackRegex.find(block)?.groupValues?.get(1)

                        if (streamUrl == null) {
                            for (match in linkRegex.findAll(block)) {
                                val href = match.groupValues[1]
                                if (href.contains(".mp3") || href.contains(".m4a") || href.contains(".ogg") || href.contains("/audio/")) {
                                    streamUrl = href
                                    break
                                }
                            }
                        }

                        val finalAudioUrl = streamUrl ?: audioStreams[parsedList.size % audioStreams.size]

                        parsedList.add(
                            TelegramTrack(
                                id = "tg_${targetChannel}_${postMatch.groupValues[2]}",
                                title = title,
                                artist = performer,
                                url = finalAudioUrl,
                                duration = duration
                            )
                        )
                    }

                    val newMinId = foundNumIds.minOrNull()
                    if (newMinId == null || newMinId == minPostId) {
                        break
                    }
                    minPostId = newMinId
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isTelegramLoading = false
                    if (parsedList.isEmpty()) {
                        telegramTracks = emptyList()
                        errorMessage = "На канале @$targetChannel нет публичных аудиотреков или доступ ограничен."
                    } else {
                        telegramTracks = parsedList.take(100)
                        errorMessage = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isTelegramLoading = false
                    errorMessage = "Ошибка подключения к каналу: ${e.localizedMessage ?: "Network error"}"
                }
            }
        }.start()
    }
}

class OnionStreamBridge(private val socksPort: Int = 9050) {
    private var serverSocket: ServerSocket? = null
    var localPort: Int = 0
        private set
    private var isRunning = false
    val bridgeSessionToken: String = UUID.randomUUID().toString().replace("-", "")

    fun start() {
        if (isRunning) return
        try {
            // Strictly bind to 127.0.0.1 loopback only to prevent external SSRF
            serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            localPort = serverSocket!!.localPort
            isRunning = true
            thread(name = "OnionProxyBridgeThread") {
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        thread {
            try {
                val inputReader = client.getInputStream().bufferedReader()
                val output = client.getOutputStream()
                val firstLine = inputReader.readLine() ?: return@thread
                
                // Expected: GET /onion_stream?url=ENCODED_URL&token=TOKEN HTTP/1.1
                if (!firstLine.startsWith("GET ")) {
                    try { client.close() } catch (_: Exception) {}
                    return@thread
                }
                
                val parts = firstLine.split(" ")
                if (parts.size < 2) return@thread
                val path = parts[1]
                
                val uri = Uri.parse("http://127.0.0.1$path")
                val token = uri.getQueryParameter("token") ?: ""
                if (token != bridgeSessionToken) {
                    output.write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n".toByteArray())
                    output.flush()
                    try { client.close() } catch (_: Exception) {}
                    return@thread
                }

                val queryParam = uri.getQueryParameter("url") ?: ""
                if (queryParam.isEmpty()) {
                    try { client.close() } catch (_: Exception) {}
                    return@thread
                }
                
                val targetUrl = queryParam
                val targetUri = Uri.parse(targetUrl)
                val host = targetUri.host ?: ""
                val isTor = host.endsWith(".onion")
                
                // Check if the local Tor SOCKS proxy port is active/available
                var useLocalSocks = false
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress("127.0.0.1", socksPort), 400)
                    s.close()
                    useLocalSocks = true
                } catch (e: Exception) {
                    useLocalSocks = false
                }
                
                if (isTor && !useLocalSocks) {
                    val errBody = "HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nTor SOCKS proxy unavailable. Cannot route .onion stream without active Tor daemon."
                    output.write(errBody.toByteArray())
                    output.flush()
                    try { client.close() } catch (_: Exception) {}
                    return@thread
                }
                
                val proxyToUse = if (useLocalSocks) {
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
                } else {
                    Proxy.NO_PROXY
                }
                
                val request = Request.Builder()
                    .url(targetUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
                    .addHeader("Accept", "*/*")
                    .addHeader("Icy-MetaData", "1")
                    .addHeader("Connection", "keep-alive")
                    .build()
                
                val okHttpClient = OkHttpClient.Builder()
                    .proxy(proxyToUse)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val body = response.body
                if (body == null) {
                    output.write("HTTP/1.1 500 Internal Server Error\r\n\r\n".toByteArray())
                    try { client.close() } catch (_: Exception) {}
                    return@thread
                }
                
                // Write HTTP headers back to MediaPlayer to keep handshake smooth
                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: ${body.contentType() ?: "audio/mpeg"}\r\n".toByteArray())
                output.write("Connection: close\r\n\r\n".toByteArray())
                
                val instream = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (instream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }
    }
}
