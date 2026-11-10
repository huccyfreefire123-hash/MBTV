package com.ondatv.player.ui

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ondatv.player.BuildConfig
import com.ondatv.player.data.UpdateInfo
import com.ondatv.player.data.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class UpdateStage { IDLE, OFFER, DOWNLOADING, ERROR }

// Plain values, so the offered version survives the trip to the Android settings screen.
private val updateSaver = listSaver<UpdateInfo?, Any>(
    save = { u -> if (u == null) emptyList() else listOf(u.versionCode, u.versionName, u.apkUrl, u.notes) },
    restore = { if (it.isEmpty()) null else UpdateInfo(it[0] as Int, it[1] as String, it[2] as String, it[3] as String) },
)

/**
 * Self-update without a store. When the app opens it reads the published version (data/Update.kt);
 * if that one is newer it offers it, downloads the APK with the DownloadManager showing the
 * progress, and hands the file to the system installer. Without internet nothing is shown.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    var checked by rememberSaveable { mutableStateOf(false) }
    var stage by rememberSaveable { mutableStateOf(UpdateStage.IDLE) }
    var update by rememberSaveable(stateSaver = updateSaver) { mutableStateOf<UpdateInfo?>(null) }
    var downloadId by rememberSaveable { mutableLongStateOf(-1L) }
    var error by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var waitingNetwork by remember { mutableStateOf(false) }

    fun fail(message: String) {
        error = message
        stage = UpdateStage.ERROR
    }

    /** Hands the downloaded file to the system installer. */
    fun install() {
        val file = Updates.apkFile(context)
        val version = file?.let { Updates.apkVersion(context, it) }
        // Only an APK of this app, newer than the one installed, goes to the installer.
        if (file == null || version == null || version <= BuildConfig.VERSION_CODE) {
            fail("O arquivo baixado não é uma versão mais nova do MBTV.")
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, Updates.APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            stage = UpdateStage.IDLE
        }.onFailure { fail("Não foi possível abrir o instalador do Android.") }
    }

    fun download() {
        val u = update ?: return
        // Already here (the installer was closed last time): no need to fetch it again.
        val ready = Updates.apkFile(context)?.let { Updates.apkVersion(context, it) }
        if (ready != null && ready >= u.versionCode) {
            install()
            return
        }
        runCatching { Updates.download(context, u) }
            .onSuccess {
                downloadId = it
                progress = null
                waitingNetwork = false
                stage = UpdateStage.DOWNLOADING
            }
            .onFailure { fail("Não foi possível iniciar o download.") }
    }

    val unknownSources = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Back from the Android settings: goes on only when installing was allowed there.
        if (context.packageManager.canRequestPackageInstalls()) download()
        else fail("Para atualizar, permita que o MBTV instale apps e toque em \"Tentar de novo\".")
    }

    /** "Atualizar agora": the permission to install first, when it is still missing. */
    fun start() {
        if (context.packageManager.canRequestPackageInstalls()) {
            download()
            return
        }
        Toast.makeText(context, "Permita que o MBTV instale apps e volte", Toast.LENGTH_LONG).show()
        runCatching {
            unknownSources.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            )
        }.onFailure { download() } // This device has no such screen: the installer itself asks.
    }

    LaunchedEffect(Unit) {
        if (checked) return@LaunchedEffect
        checked = true
        val latest = withContext(Dispatchers.IO) {
            // The file of an update that is already installed is of no more use.
            Updates.apkFile(context)?.let { file ->
                val version = Updates.apkVersion(context, file)
                if (file.exists() && (version == null || version <= BuildConfig.VERSION_CODE)) file.delete()
            }
            Updates.latest()
        } ?: return@LaunchedEffect
        if (latest.versionCode > BuildConfig.VERSION_CODE) {
            update = latest
            stage = UpdateStage.OFFER
        }
    }

    // Follows the download while its dialog is open.
    LaunchedEffect(stage, downloadId) {
        if (stage != UpdateStage.DOWNLOADING) return@LaunchedEffect
        while (true) {
            val state = withContext(Dispatchers.IO) { runCatching { Updates.state(context, downloadId) }.getOrNull() }
            when (state?.status) {
                null -> {
                    stage = UpdateStage.IDLE
                    break
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    install()
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    Updates.cancel(context, downloadId)
                    fail("O download falhou. Confira a internet e tente de novo.")
                    break
                }
                else -> {
                    progress = state.fraction
                    waitingNetwork = state.status == DownloadManager.STATUS_PAUSED
                }
            }
            delay(400)
        }
    }

    val u = update
    when (stage) {
        UpdateStage.IDLE -> Unit
        UpdateStage.OFFER -> if (u != null) {
            AlertDialog(
                onDismissRequest = { stage = UpdateStage.IDLE },
                title = { Text("Atualização disponível") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("Versão ${u.versionName}", fontWeight = FontWeight.SemiBold)
                        if (u.notes.isNotEmpty()) Text(u.notes, modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { start() }) { Text("Atualizar agora") } },
                dismissButton = { TextButton(onClick = { stage = UpdateStage.IDLE }) { Text("Depois") } },
            )
        }
        UpdateStage.DOWNLOADING -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Baixando atualização") },
            text = {
                Column {
                    val p = progress
                    if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        when {
                            waitingNetwork -> "Aguardando a internet..."
                            p != null -> "${(p * 100).toInt()}%"
                            else -> "Iniciando..."
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    Updates.cancel(context, downloadId)
                    stage = UpdateStage.IDLE
                }) { Text("Cancelar") }
            },
        )
        UpdateStage.ERROR -> AlertDialog(
            onDismissRequest = { stage = UpdateStage.IDLE },
            title = { Text("Não foi possível atualizar") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = {
                    stage = UpdateStage.OFFER
                    start()
                }) { Text("Tentar de novo") }
            },
            dismissButton = { TextButton(onClick = { stage = UpdateStage.IDLE }) { Text("Fechar") } },
        )
    }
}
