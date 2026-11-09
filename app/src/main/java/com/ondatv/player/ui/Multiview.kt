package com.ondatv.player.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ondatv.player.AppViewModel
import com.ondatv.player.buildPlayer
import com.ondatv.player.data.Item
import com.ondatv.player.data.Kind
import com.ondatv.player.mediaItemFor

/**
 * Up to four live channels at once in a 2x2 grid. One slot has the sound (white border);
 * tap a slot to move the sound there, double-tap or press the corner button for full screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun MultiviewScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val slots by vm.multiview.collectAsStateWithLifecycle()
    vm.epg.collectAsStateWithLifecycle().value
    val now = rememberNow()
    val byKey = remember(items) { items.associateBy { it.key } }
    val parentalOn by vm.parentalOn.collectAsStateWithLifecycle()
    // Adult channels need the PIN inside the multiview itself, every time it is opened,
    // even if they were unlocked elsewhere in this session.
    var adultOkHere by rememberSaveable { mutableStateOf(false) }
    val adultAllowed = !parentalOn || adultOkHere
    var askPin by remember { mutableStateOf(false) }

    var active by rememberSaveable { mutableIntStateOf(0) }
    var full by rememberSaveable { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var menuFor by remember { mutableStateOf<Int?>(null) }

    val players = remember { List(4) { buildPlayer(context, small = true) } }
    val loaded = remember { arrayOfNulls<String>(4) }

    // Landscape, no system bars, screen kept on; everything undone when leaving.
    DisposableEffect(Unit) {
        val window = activity?.window
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            players.forEach { it.release() }
            activity?.requestedOrientation = previous
            bars?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Pause in the background, resume on return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> players.forEach { it.pause() }
                Lifecycle.Event.ON_START -> players.forEach { if (it.mediaItemCount > 0) it.play() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Start or stop each slot when its channel changes.
    LaunchedEffect(slots, byKey, adultAllowed) {
        slots.forEachIndexed { i, key ->
            // A slot holding an adult channel without the PIN is emptied (and stops).
            val known = key?.let { byKey[it] ?: vm.anyItem(it) }
            if (known != null && vm.isAdult(known) && !adultAllowed) {
                vm.setSlot(i, null)
                return@forEachIndexed
            }
            if (loaded[i] == key) return@forEachIndexed
            val item = key?.let { byKey[it] }
            val p = players[i]
            if (item == null) {
                // Empty, or not available now: never keep the old stream running.
                p.stop()
                p.clearMediaItems()
                loaded[i] = if (key == null) null else "-"
            } else {
                loaded[i] = key
                p.setMediaItem(mediaItemFor(item.url))
                p.prepare()
                p.playWhenReady = true
            }
        }
    }
    LaunchedEffect(active) {
        players.forEachIndexed { i, p -> p.volume = if (i == active) 1f else 0f }
    }

    BackHandler(enabled = full != null) { full = null }

    @Composable
    fun Slot(i: Int, modifier: Modifier, isFull: Boolean) {
        val item = slots[i]?.let { byKey[it] }
        val selected = i == active && !isFull
        Box(
            modifier
                .padding(if (isFull) 0.dp else 1.dp)
                .then(if (selected) Modifier.border(2.dp, Color.White) else Modifier)
                .background(Color(0xFF0D0E12)),
        ) {
            if (item != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            player = players[i]
                        }
                    },
                    update = { it.player = players[i] },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Onda.glass).border(1.dp, Onda.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Text("Adicionar canal", color = Onda.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            // Gestures sit over the video.
            Box(
                Modifier.fillMaxSize().pointerInput(i, item, isFull) {
                    detectTapGestures(
                        onTap = { if (item == null) picking = i else active = i },
                        onDoubleTap = {
                            if (item != null) {
                                active = i
                                full = if (isFull) null else i
                            }
                        },
                        onLongPress = { if (item != null) menuFor = i else picking = i },
                    )
                }
            )
            if (item != null) {
                val programme = vm.nowNext(item, now).first
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (i == active) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp, "Com som", tint = Color.White,
                            modifier = Modifier.padding(end = 6.dp).size(16.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (programme != null) {
                            Text(
                                programme.title, color = Onda.textDim, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).clip(CircleShape)
                        .background(Color(0x66000000))
                        .clickable { active = i; full = if (isFull) null else i },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isFull) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        if (isFull) "Voltar à grade" else "Tela cheia",
                        tint = Color.White, modifier = Modifier.size(24.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }) {
                DropdownMenuItem(text = { Text("Trocar canal") }, onClick = { menuFor = null; picking = i })
                DropdownMenuItem(text = { Text("Tela cheia") }, onClick = { menuFor = null; active = i; full = i })
                DropdownMenuItem(text = { Text("Remover") }, onClick = {
                    menuFor = null
                    vm.setSlot(i, null)
                    if (full == i) full = null
                })
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val f = full
        if (f != null && slots[f] != null) {
            Slot(f, Modifier.fillMaxSize(), isFull = true)
        } else {
            Column(Modifier.fillMaxSize()) {
                for (r in 0..1) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0..1) Slot(r * 2 + c, Modifier.weight(1f).fillMaxHeight(), isFull = false)
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(10.dp).size(40.dp).clip(CircleShape)
                .background(Color(0x66000000)).border(1.dp, Onda.glassBorder, CircleShape)
                .clickable { if (full != null) full = null else onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Voltar", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }

    picking?.let { slot ->
        ChannelPicker(
            channels = remember(items, favorites, adultAllowed) {
                val live = items.filter { it.kind == Kind.LIVE }
                val normal = live.filterNot { vm.isAdult(it) }.sortedByDescending { it.key in favorites }
                // Adult channels last, and only once the PIN was given here.
                normal + if (adultAllowed) live.filter { vm.isAdult(it) } else emptyList()
            },
            showLockedAdult = !adultAllowed && vm.adultGroups.value.isNotEmpty(),
            onUnlock = { askPin = true },
            onPick = { ch ->
                vm.setSlot(slot, ch.key)
                active = slot
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (askPin) {
        PinPad(
            title = "Canais adultos",
            subtitle = "Digite a senha do Controle Parental",
            check = { vm.unlockAdult(it) },
            onSuccess = { askPin = false; adultOkHere = true },
            onDismiss = { askPin = false },
        )
    }
}

@Composable
private fun ChannelPicker(
    channels: List<Item>,
    showLockedAdult: Boolean,
    onUnlock: () -> Unit,
    onPick: (Item) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(channels, query) {
        val q = query.trim()
        (if (q.isEmpty()) channels else channels.filter { it.name.contains(q, ignoreCase = true) }).take(500)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.92f).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1D24)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Escolher canal", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                placeholder = { Text("Buscar canal") }, modifier = Modifier.fillMaxWidth(),
            )
            if (shown.isEmpty() && !showLockedAdult) EmptyState("Nenhum canal encontrado.")
            LazyColumn(Modifier.fillMaxSize()) {
                if (showLockedAdult) {
                    item(key = "__adult_locked") {
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onUnlock).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(width = 48.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("Canais adultos", color = Color.White, fontSize = 15.sp)
                                Text("Protegidos por senha", color = Onda.textFaint, fontSize = 12.sp)
                            }
                        }
                    }
                }
                items(shown, key = { it.key }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(ch) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(width = 48.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                            if (ch.logo == null) Initials(ch.name, size = 14)
                            AsyncImage(
                                model = ch.logo, contentDescription = null,
                                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(ch.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ch.group, color = Onda.textFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
