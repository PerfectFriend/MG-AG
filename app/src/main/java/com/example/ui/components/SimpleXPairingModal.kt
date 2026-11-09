package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SimpleXPairingModal(
    localUserName: String,
    smpRelayAddress: String,
    onDismiss: () -> Unit,
    onPairWithContactUri: (contactUri: String) -> Unit,
    lang: String = "RU"
) {
    val context = LocalContext.current
    var isScanningMode by remember { mutableStateOf(false) }
    var inputContactUri by remember { mutableStateOf("") }

    // Generate deterministic yet ephemeral pairing identifier
    val ephemeralQueueId = remember {
        val rand = ByteArray(16)
        java.security.SecureRandom().nextBytes(rand)
        rand.joinToString("") { "%02x".format(it) }
    }

    val contactUri = remember(smpRelayAddress, ephemeralQueueId) {
        val relay = smpRelayAddress.ifBlank { "smp.simplex.im" }
        "simplex://contact?v=1&addr=$relay&queue=$ephemeralQueueId&name=$localUserName"
    }

    // Generate QR bitmap
    val qrBitmap = remember(contactUri) {
        val matrix = QrGenerator.generate(contactUri)
        val size = 220
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(AndroidColor.WHITE)
        val paint = Paint().apply { color = AndroidColor.BLACK }
        val matrixSize = matrix.size
        if (matrixSize > 0) {
            val unit = size.toFloat() / matrixSize
            for (r in matrix.indices) {
                for (c in matrix[r].indices) {
                    if (matrix[r][c]) {
                        canvas.drawRect(c * unit, r * unit, (c + 1) * unit, (r + 1) * unit, paint)
                    }
                }
            }
        }
        bmp.asImageBitmap()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10131A),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (lang == "RU") "SIMPLEX СОПРЯЖЕНИЕ" else "SIMPLEX P2P PAIRING",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (lang == "RU") "Бессерверное сквозное шифрование" else "Serverless E2EE Rendezvous",
                                fontSize = 9.sp,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle between Showing My QR vs Scanning Friend's QR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161A22))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { isScanningMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isScanningMode) Color(0xFF00E676) else Color.Transparent,
                            contentColor = if (!isScanningMode) Color.Black else Color.LightGray
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "RU") "Мой QR-код" else "My QR Link", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { isScanningMode = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanningMode) Color(0xFF00E676) else Color.Transparent,
                            contentColor = if (isScanningMode) Color.Black else Color.LightGray
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "RU") "Сканировать" else "Scan Peer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isScanningMode) {
                    // QR Code Presentation View
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "SimpleX QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (lang == "RU") "Покажите этот QR другу или скопируйте ссылку:" else "Show this QR to your friend or copy link:",
                        fontSize = 9.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = contactUri,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF00E676),
                            unfocusedTextColor = Color(0xFF00E676),
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color(0xFF00E676).copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SimpleX Contact", contactUri)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(
                                    context,
                                    if (lang == "RU") "Ссылка SimpleX скопирована!" else "SimpleX link copied!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "RU") "Копировать" else "Copy Link", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, contactUri)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "SimpleX Contact Invite")
                                context.startActivity(shareIntent)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "RU") "Поделиться" else "Share", fontSize = 11.sp)
                        }
                    }
                } else {
                    // Scanning or Entering Remote SimpleX Link View
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3748))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (lang == "RU") "Вставьте ссылку SimpleX друга:" else "Paste friend's SimpleX contact link:",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = inputContactUri,
                                onValueChange = { inputContactUri = it },
                                placeholder = { Text("simplex://contact?v=1...", fontSize = 10.sp, color = Color.Gray) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedBorderColor = Color(0xFF00E676),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (inputContactUri.isNotBlank()) {
                                        onPairWithContactUri(inputContactUri.trim())
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Text(if (lang == "RU") "ПОДКЛЮЧИТЬСЯ И НАЧАТЬ ИГРУ" else "CONNECT & START MATCH", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Embedded Camera Scanner View
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        QrCodeScannerView(
                            onQrScanned = { scanned ->
                                if (scanned.startsWith("simplex://") || scanned.contains("contact?")) {
                                    inputContactUri = scanned
                                    onPairWithContactUri(scanned.trim())
                                    onDismiss()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Security Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00E676).copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                    Text(
                        text = if (lang == "RU") "Сессионные ключи уничтожаются сразу после завершения матча."
                        else "Session keys are wiped from memory immediately upon match conclusion.",
                        fontSize = 8.5.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
