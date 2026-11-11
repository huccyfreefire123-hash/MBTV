package com.ondatv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ondatv.player.AppViewModel
import com.ondatv.player.PlayRequest
import com.ondatv.player.data.Progress
import com.ondatv.player.isTv

/** "Restam 62 min" / "Restam 1h 41min" for a film or episode begun and not finished. */
fun remaining(p: Progress): String {
    val min = ((p.duration - p.position).coerceAtLeast(0) + 59_999) / 60_000
    return if (min >= 60) "Restam ${min / 60}h ${min % 60}min" else "Restam $min min"
}

/** 38:10 or 1:02:05. */
private fun timeLabel(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Begun and not finished (finished = 95%). */
fun Progress.inProgress(): Boolean = duration > 0 && position > 0 && position < duration * 0.95

// ---------- Detalhe do filme ----------

@Composable
fun MovieScreen(vm: AppViewModel, key: String, play: (PlayRequest) -> Unit, onBack: () -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val item = remember(key) { vm.item(key) }
    LaunchedEffect(item) { if (item != null) vm.loadDetails(listOf(item)) }
    val d = details[key]
    // Progress only counts when it belongs to this very address (lists can change order).
    val saved = progress[key]?.takeIf { it.inProgress() && (item == null || it.url == item.url) }

    val focus = remember { FocusRequester() }
    val tv = LocalContext.current.isTv()
    LaunchedEffect(item) { if (tv && item != null) runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Onda.heroBg)) {
        if (item == null) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 80.dp)) {
                EmptyState("Filme não encontrado. Atualize as listas.")
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(detailHeaderHeight(1.03f))) {
                        AsyncImage(
                            model = d?.backdrop ?: item.backdrop ?: item.logo, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Onda.heroBg)))
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).offset(y = 36.dp).width(128.dp).aspectRatio(2f / 3f)
                            .shadow(14.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Onda.card)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    ) {
                        Initials(item.name, Modifier.align(Alignment.Center), size = 28)
                        AsyncImage(
                            model = item.logo, contentDescription = item.name,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        d?.year ?: item.year, d?.duration, d?.genre ?: item.genre, d?.age,
                    ).filter { it.isNotBlank() }.joinToString(DOT)
                    if (meta.isNotEmpty()) {
                        Text(meta, color = Onda.textDim, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                    (d?.rating ?: item.rating)?.let { RatingChip(it, Modifier.padding(top = 10.dp)) }
                    Spacer(Modifier.height(20.dp))
                    WatchNowButton(Modifier.focusRequester(focus)) { play(item.toRequest()) }
                    if (saved != null) {
                        Column(Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(top = 12.dp)) {
                            ProgressLine(saved.position.toFloat() / saved.duration, height = 4.dp)
                            Text(
                                remaining(saved) + DOT + "continua de " + timeLabel(saved.position),
                                color = Onda.textFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                (d?.plot ?: item.plot)?.let {
                    Text(
                        it, color = Onda.textDim, fontSize = 15.sp, lineHeight = 21.sp,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp),
                    )
                }
                BottomSpace()
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
            GlassCircle(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Voltar", onBack)
            Spacer(Modifier.weight(1f))
            if (item != null) {
                val fav = item.key in favorites
                GlassCircle(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito", { vm.toggleFavorite(item.key) })
            }
        }
    }
}

@Composable
private fun WatchNowButton(modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(25.dp)
    Row(
        modifier
            .widthIn(max = 320.dp).fillMaxWidth().height(50.dp)
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(28.dp)).padding(5.dp) else Modifier)
            .clip(shape).background(Color.White)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
        Text(
            "ASSISTIR AGORA", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 6.dp),
        )
    }
}
