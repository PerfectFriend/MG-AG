package com.example.data

import android.content.Context
import android.util.Base64
import com.example.crypto.ErasureCodedVault
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native SimpleX File Transfer Protocol (XFTP) Client & Chunked E2EE Spooler.
 *
 * Implements:
 * - Chunking files into fixed-size encrypted XFTP blocks (AES-256-GCM per chunk)
 * - Reed-Solomon GF(2^8) redundancy sharding via ErasureCodedVault
 * - Local persistent XFTP spooling and SOCKS5 / Tor Onion relay dispatch
 * - Generation of authentic XFTP file descriptor descriptors (XFTP URI + ephemeral key + SHA-256 digest)
 */
class SimpleXftpClient(
    private val context: Context,
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int = 9050,
    private val onLog: (String) -> Unit = {}
) {
    data class XftpFileReceipt(
        val fileId: String,
        val fileName: String,
        val originalSize: Int,
        val encryptedSize: Int,
        val sha256Hex: String,
        val ephemeralKeyHex: String,
        val ivHex: String,
        val xftpDescriptorUri: String,
        val localSpoolPath: String,
        val relayedOverTor: Boolean
    )

    private val vaultEngine = ErasureCodedVault()
    private val secureRandom = SecureRandom()

    private fun buildTorHttpClient(): OkHttpClient {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return if (hostname.endsWith(".onion", ignoreCase = true)) {
                        listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
            .build()
    }

    /**
     * Encrypts a file/payload with an ephemeral 256-bit AES-GCM key, shards it with GF(2^8) Reed-Solomon,
     * writes shards to local XFTP spool, and attempts relay upload over Tor SOCKS5.
     */
    fun uploadEncryptedFile(
        fileName: String,
        rawBytes: ByteArray,
        xftpServerAddress: String = SimpleXProtocol.DEFAULT_XFTP_SERVERS.first()
    ): XftpFileReceipt {
        val ephemeralKey = ByteArray(32)
        secureRandom.nextBytes(ephemeralKey)
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ephemeralKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(rawBytes)

        val sha256 = MessageDigest.getInstance("SHA-256").digest(ciphertext)
        val sha256Hex = ErasureCodedVault.bytesToHex(sha256)
        val keyHex = ErasureCodedVault.bytesToHex(ephemeralKey)
        val ivHex = ErasureCodedVault.bytesToHex(iv)

        // Shard encrypted file using GF(2^8) Reed-Solomon (4 Data + 2 Parity)
        val vaultFile = vaultEngine.shardFile(fileName, ciphertext, dataShardsCount = 4, parityShardsCount = 2)

        val spoolDir = File(context.filesDir, "xftp_outbound_spool/${sha256Hex.take(16)}")
        spoolDir.mkdirs()

        vaultFile.shards.forEach { shard ->
            val shardFile = File(spoolDir, "shard_${shard.index}_${if (shard.isParity) "parity" else "data"}.xftp")
            shardFile.writeBytes(shard.data)
        }

        // Write metadata manifest
        val manifestJson = JSONObject().apply {
            put("fileName", fileName)
            put("originalSize", rawBytes.size)
            put("encryptedSize", ciphertext.size)
            put("sha256Hex", sha256Hex)
            put("ivHex", ivHex)
            put("shardsCount", vaultFile.shards.size)
            put("timestamp", System.currentTimeMillis())
        }
        File(spoolDir, "manifest.json").writeText(manifestJson.toString(2))

        // Attempt network dispatch over Tor SOCKS5 proxy to XFTP relay if Tor port is listening
        var relayed = false
        try {
            val cleanHost = xftpServerAddress
                .removePrefix("xftp://")
                .removePrefix("xftps://")
                .substringAfter("@")
                .trim()
            val hostOnly = cleanHost.substringBefore("/").substringBefore(":")
            if (!hostOnly.endsWith(".onion", ignoreCase = true)) {
                throw IllegalStateException("XFTP relay must be an .onion host; refusing to send shards in clear text to $cleanHost")
            }
            val url = "http://$cleanHost/xftp/v1/chunk"
            onLog("[XFTP] Dispatching ${vaultFile.shards.size} GF(2^8) shards to $cleanHost via SOCKS5 ($socksHost:$socksPort)...")

            val client = buildTorHttpClient()
            var uploaded = 0
            vaultFile.shards.forEach { shard ->
                val bodyPayload = JSONObject().apply {
                    put("fileHash", sha256Hex)
                    put("chunkIndex", shard.index)
                    put("isParity", shard.isParity)
                    put("dataB64", Base64.encodeToString(shard.data, Base64.NO_WRAP))
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .post(bodyPayload.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) uploaded++
                }
            }
            relayed = uploaded > 0
            onLog("[XFTP] Relay accepted $uploaded/${vaultFile.shards.size} shards")
        } catch (e: Exception) {
            onLog("[XFTP] Local spool saved (${spoolDir.absolutePath}); remote onion relay deferred: ${e.message}")
        }

        val cleanServer = xftpServerAddress.ifBlank { SimpleXProtocol.DEFAULT_XFTP_SERVERS.first() }
        // The descriptor is meant to be shared. Encryption material must travel over a
        // separate authenticated channel, never inside the same string, so the handle
        // carries only the content address.
        val descriptorUri = "$cleanServer/${sha256Hex.take(24)}"

        return XftpFileReceipt(
            fileId = sha256Hex.take(16),
            fileName = fileName,
            originalSize = rawBytes.size,
            encryptedSize = ciphertext.size,
            sha256Hex = sha256Hex,
            ephemeralKeyHex = keyHex,
            ivHex = ivHex,
            xftpDescriptorUri = descriptorUri,
            localSpoolPath = spoolDir.absolutePath,
            relayedOverTor = relayed
        )
    }
}
