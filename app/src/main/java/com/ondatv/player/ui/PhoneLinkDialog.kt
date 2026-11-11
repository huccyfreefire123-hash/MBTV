package com.ondatv.player.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ondatv.player.data.PhoneLink
import com.ondatv.player.data.Source
import com.ondatv.player.data.SourceType

/**
 * "Adicionar pelo celular": a QR code that opens, on a phone in the same network, a form to fill in
 * the list (see data/PhoneLink.kt). [type] fixes M3U or Xtream, or null lets the phone choose;
 * [prefill] is the list being edited. [onReceived] gets what the phone sent.
 */
@Composable
fun PhoneLinkDialog(type: SourceType?, prefill: Source?, onReceived: (Source) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // A new round starts a new code (the previous one expired or could not start).
    var round by remember { mutableIntStateOf(0) }
    var address by remember(round) { mutableStateOf<String?>(null) }
    var expired by remember(round) { mutableStateOf(false) }
    val received by rememberUpdatedState(onReceived)

    DisposableEffect(round) {
        val link = PhoneLink(context, type, prefill, onReceived = { received(it) }, onExpired = { expired = true })
        address = link.start()
        // The link stops the moment this screen leaves: it is not meant to stay open.
        onDispose { link.stop() }
    }
    val qr = remember(address) { address?.let(::qrBitmap) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar pelo celular") },
        text = {
            when {
                address == null -> Text(
                    "A TV não está conectada a uma rede. Conecte a TV ao Wi-Fi ou ao cabo de rede e tente de novo.",
                    fontSize = 15.sp,
                )
                expired -> Text(
                    "O código expirou. Gere um novo e escaneie de novo.",
                    fontSize = 15.sp,
                )
                else -> {
                    val picture: @Composable () -> Unit = {
                        if (qr != null) {
                            Image(
                                qr.asImageBitmap(), "QR code para abrir o formulário no celular",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.size(if (wide) 220.dp else 240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp),
                            )
                        }
                    }
                    val steps: @Composable () -> Unit = {
                        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1. Aponte a câmera do celular para o código.", fontSize = 15.sp)
                            Text("2. Abra o link e preencha o formulário.", fontSize = 15.sp)
                            Text("3. Toque em Enviar: a lista aparece aqui.", fontSize = 15.sp)
                            Text(
                                "O celular precisa estar na mesma rede Wi-Fi da TV.",
                                fontSize = 13.sp, color = Onda.textFaint,
                            )
                            Text(address.orEmpty(), fontSize = 12.sp, color = Onda.textFaint, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Text("Aguardando o celular...", fontSize = 14.sp)
                            }
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            picture()
                            steps()
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            picture()
                            steps()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (expired || address == null) TextButton(onClick = { round++ }) { Text("Gerar novo código") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** The QR code of [text] as black modules on white, [size] pixels wide (a sharp square for scaling without blur). */
private fun qrBitmap(text: String, size: Int = 480): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
