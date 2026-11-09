package com.tensei.ytxtube

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Which video or song the "Salvar em…" sheet is open for; any screen can open it. */
object SaveToPlaylist {
    var video by mutableStateOf<VideoItem?>(null)
        private set

    fun open(item: VideoItem) {
        video = item
    }

    fun close() {
        video = null
    }
}

private val PRIVACY = listOf("PRIVATE" to "Particular", "UNLISTED" to "Não listada", "PUBLIC" to "Pública")

private fun privacyLabel(value: String?): String? = PRIVACY.firstOrNull { it.first == value }?.second

/**
 * Shows the sheet while [SaveToPlaylist] has a video. [onWatchLater] tells the player when the
 * video entered or left Watch later; [onPlaylistsChanged] after a playlist was created.
 */
@Composable
fun SaveToPlaylistHost(
    onSignIn: () -> Unit,
    onWatchLater: (videoId: String, saved: Boolean) -> Unit,
    onPlaylistsChanged: () -> Unit,
) {
    val video = SaveToPlaylist.video ?: return
    SaveToPlaylistSheet(video, onSignIn, onWatchLater, onPlaylistsChanged, onDismiss = SaveToPlaylist::close)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveToPlaylistSheet(
    video: VideoItem,
    onSignIn: () -> Unit,
    onWatchLater: (String, Boolean) -> Unit,
    onPlaylistsChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn = Account.signedIn
    var targets by remember(video.id) { mutableStateOf<List<SaveTarget>?>(null) }
    var covers by remember(video.id) { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var failed by remember(video.id) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(video.id, signedIn) {
        if (!signedIn) return@LaunchedEffect
        coroutineScope {
            // The add-to list has no covers; the library list has them, so both load together.
            val list = async { runCatching { InnerTube.saveTargets(video.id) } }
            val library = async { runCatching { InnerTube.playlists() }.getOrDefault(emptyList()) }
            list.await().onSuccess { targets = it }.onFailure { failed = true }
            covers = library.await().associate { it.id to it.thumbnail }
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun toggle(target: SaveTarget) {
        val add = !target.contains
        targets = targets?.map { if (it.id == target.id) it.copy(contains = add) else it }
        scope.launch {
            runCatching { InnerTube.setInPlaylist(target.id, video.id, add) }
                .onSuccess {
                    if (target.id == "WL") onWatchLater(video.id, add)
                    toast(if (add) "Salvo em ${target.title}" else "Removido de ${target.title}")
                }
                .onFailure {
                    targets = targets?.map { if (it.id == target.id) it.copy(contains = !add) else it }
                    toast("Não foi possível atualizar a playlist.")
                }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Salvar em…", fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(14.dp))
            when {
                !signedIn -> {
                    Text(
                        "Entre na sua conta do YouTube para salvar vídeos e músicas nas suas playlists.",
                        color = c.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    PillButton("Entrar com o Google", { onDismiss(); onSignIn() })
                }
                failed -> Text("Não foi possível carregar suas playlists. Tente de novo.", color = c.onSurfaceVariant, fontSize = 14.sp)
                targets == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                }
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(targets.orEmpty(), key = { it.id }) { t -> TargetRow(t, covers[t.id]) { toggle(t) } }
                }
            }
            if (signedIn) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (TvMode.isTv) Modifier.tvFocusRing(radius = 26.dp) else Modifier)
                        .clip(RoundedCornerShape(26.dp))
                        .background(c.surfaceContainerHighest)
                        .clickable { creating = true }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onSurface, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nova playlist", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                }
            }
        }
    }

    if (creating) {
        NewPlaylistDialog(onDismiss = { creating = false }) { title, privacy ->
            creating = false
            scope.launch {
                runCatching { InnerTube.createPlaylist(title, privacy, video.id) }
                    .onSuccess { id ->
                        targets = listOf(SaveTarget(id, title, privacy, contains = true)) + targets.orEmpty()
                        onPlaylistsChanged()
                        toast("Playlist \"$title\" criada")
                    }
                    .onFailure { toast("Não foi possível criar a playlist.") }
            }
        }
    }
}

@Composable
private fun TargetRow(target: SaveTarget, cover: String?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (TvMode.isTv) Modifier.tvFocusRing(radius = 14.dp) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover != null) {
            Thumbnail(cover, Modifier.width(104.dp).aspectRatio(16f / 9f), radius = 10)
        } else {
            Box(
                Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (target.id == "WL") Icons.Rounded.WatchLater else Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(target.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            privacyLabel(target.privacy)?.let { Text(it, color = c.onSurfaceVariant, fontSize = 13.sp) }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            if (target.contains) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = if (target.contains) "Salvo" else "Salvar",
            tint = if (target.contains) c.primary else c.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (title: String, privacy: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("PRIVATE") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(150) },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Visibilidade", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                PRIVACY.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { privacy = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = privacy == value, onClick = { privacy = value })
                        Text(label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim(), privacy) }, enabled = title.isNotBlank()) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
