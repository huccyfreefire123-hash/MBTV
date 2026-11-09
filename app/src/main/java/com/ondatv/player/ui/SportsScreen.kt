package com.ondatv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ondatv.player.AppViewModel
import com.ondatv.player.PlayRequest
import com.ondatv.player.data.Item
import com.ondatv.player.data.Match
import com.ondatv.player.data.Sports
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ALL = "__all"
private const val MINE = "__mine"

private val dayFormat = SimpleDateFormat("EEE, dd/MM", Locale("pt", "BR"))

fun dayLabel(ms: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Hoje"
    today.add(Calendar.DAY_OF_YEAR, 1)
    if (same(day, today)) return "Amanhã"
    return dayFormat.format(Date(ms)).replaceFirstChar { it.uppercase() }
}

@Composable
fun rememberMatches(vm: AppViewModel, now: Long): List<Match> {
    // Found in the background when the list or guide changes; here only the ended ones drop out.
    val all by vm.matches.collectAsStateWithLifecycle()
    val minute = now / 60_000
    return remember(all, minute) { all.filter { it.stop > now } }
}

@Composable
fun SportsScreen(vm: AppViewModel, play: (PlayRequest) -> Unit, onBack: () -> Unit) {
    val teams by vm.teams.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val now = rememberNow()
    val matches = rememberMatches(vm, now)
    var filter by rememberSaveable { mutableStateOf(if (teams.isEmpty()) ALL else MINE) }
    var managing by remember { mutableStateOf(false) }

    val shown = remember(matches, filter, teams) {
        when (filter) {
            ALL -> matches
            MINE -> matches.filter { Sports.follows(it, teams) }
            else -> matches.filter { Sports.follows(it, listOf(filter)) }
        }
    }
    val live = shown.filter { it.isLive(now) }
    val upcoming = shown.filter { it.start > now }.groupBy { dayLabel(it.start) }

    Column(Modifier.fillMaxSize().background(Onda.listBackground)) {
        CenterBar("Esportes", onBack, action = {
            GlassCircle(Icons.Filled.StarBorder, "Meus times", { managing = true })
        })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SelectChip("Todos os jogos", filter == ALL) { filter = ALL } }
            item { SelectChip("Meus times", filter == MINE) { filter = MINE } }
            items(teams.sorted()) { t -> SelectChip(t, filter == t) { filter = t } }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                epg.isEmpty() -> item {
                    EmptyState("O guia de programação (EPG) ainda não carregou.\nOs jogos são encontrados no guia da sua lista.")
                }
                shown.isEmpty() && filter == MINE && teams.isEmpty() -> item {
                    EmptyState("Você ainda não segue nenhum time.\nToque na estrela acima para escolher.")
                }
                shown.isEmpty() -> item { EmptyState("Nenhum jogo encontrado no guia.") }
            }
            if (live.isNotEmpty()) {
                item { GroupLabel("Ao vivo agora") }
                items(live, key = { it.key }) { m -> MatchCard(m, now, teams, play, Modifier.fillMaxWidth()) }
            }
            upcoming.forEach { (day, list) ->
                item(key = "day-$day") { GroupLabel(day) }
                items(list, key = { it.key }) { m -> MatchCard(m, now, teams, play, Modifier.fillMaxWidth()) }
            }
        }
    }

    if (managing) {
        TeamsDialog(
            followed = teams,
            suggestions = Sports.teams(matches),
            onToggle = { vm.toggleTeam(it) },
            onDismiss = { managing = false },
        )
    }
}

/** Matches of followed teams for the Home screen; empty when none are coming. */
@Composable
fun SportsRow(vm: AppViewModel, play: (PlayRequest) -> Unit, openSports: () -> Unit) {
    val teams by vm.teams.collectAsStateWithLifecycle()
    if (teams.isEmpty()) return
    val now = rememberNow()
    val matches = rememberMatches(vm, now).filter { Sports.follows(it, teams) }
    if (matches.isEmpty()) return
    val width: Dp = (LocalConfiguration.current.screenWidthDp * 0.8f).coerceAtMost(360f).dp
    SectionHeader("Jogos dos seus times", onMore = openSports)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(matches.take(10), key = { it.key }) { m -> HomeMatchCard(m, now, play, Modifier.width(width)) }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White else Onda.glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Two letters for a team without a crest: "Leoes FC" -> "LF", "Uniao do Vale" -> "UV". */
private fun initials(name: String): String {
    val small = setOf("de", "da", "do", "das", "dos", "e", "la", "el", "the")
    val words = name.trim().split(Regex("""[\s.\-]+""")).filter { it.isNotEmpty() && it.lowercase() !in small }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> name.take(1)
    }.uppercase()
}

private fun teamHue(name: String) = (((Sports.norm(name).hashCode() % 360) + 360) % 360).toFloat()

/** Shield-shaped crest in the team's colour with its initials. */
@Composable
private fun Crest(name: String, width: Dp) {
    val hue = teamHue(name)
    val top = Color.hsv(hue, 0.62f, 0.88f)
    val bottom = Color.hsv(hue, 0.85f, 0.52f)
    val height = width * 1.13f
    Box(Modifier.width(width).height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.03f)
                lineTo(w * 0.94f, h * 0.14f)
                lineTo(w * 0.94f, h * 0.49f)
                cubicTo(w * 0.94f, h * 0.74f, w * 0.71f, h * 0.9f, w * 0.5f, h * 0.97f)
                cubicTo(w * 0.29f, h * 0.9f, w * 0.06f, h * 0.74f, w * 0.06f, h * 0.49f)
                lineTo(w * 0.06f, h * 0.14f)
                close()
            }
            translate(top = h * 0.04f) { drawPath(shield, Color.Black.copy(alpha = 0.35f)) }
            drawPath(shield, Brush.verticalGradient(listOf(top, bottom)))
            drawPath(shield, Color.White.copy(alpha = 0.55f), style = Stroke(width = w * 0.035f))
        }
        Text(
            initials(name), color = Color.White, fontWeight = FontWeight.ExtraBold,
            fontSize = (width.value * 0.36f).sp, modifier = Modifier.padding(bottom = height * 0.06f),
        )
    }
}

@Composable
private fun TeamColumn(name: String, followed: Boolean, crest: Dp, fontSize: Int, shadow: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(name, crest)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name, color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                lineHeight = (fontSize + 3).sp,
                style = if (shadow) TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 8f)) else TextStyle.Default,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (followed) {
                Icon(Icons.Filled.Star, "Seguido", tint = Onda.gold, modifier = Modifier.padding(start = 4.dp).size(13.dp))
            }
        }
    }
}

@Composable
private fun LivePill(text: String = "AO VIVO") {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(LiveRed).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp))
    }
}

private val LiveRed = Color(0xFFE53935)
private val GlowBlue = Color(0xFF4FA3FF)
private val CardShape = RoundedCornerShape(18.dp)

/** Card of the Esportes screen: competition and time, crests with VS, channels below. */
@Composable
fun MatchCard(m: Match, now: Long, teams: Set<String>, play: (PlayRequest) -> Unit, modifier: Modifier = Modifier) {
    val live = m.isLive(now)
    Column(
        modifier
            .shadow(10.dp, CardShape)
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF30323C), Color(0xFF22232B), Color(0xFF1A1B22))))
            .border(1.dp, Color.White.copy(alpha = 0.09f), CardShape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EmojiEvents, null, tint = Onda.gold, modifier = Modifier.size(15.dp))
            Text(
                (m.competition ?: "Partida").uppercase(), color = Onda.textDim, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 7.dp).weight(1f),
            )
            if (live) LivePill() else Text(
                dayLabel(m.start) + " · " + clock(m.start), color = Onda.textDim, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            TeamColumn(m.home, Sports.follows(m.copy(away = ""), teams), 60.dp, 14, false, Modifier.weight(1f))
            Box(
                Modifier.padding(top = 12.dp).size(44.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF3B3E4A), Color(0xFF1A1B22))))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("VS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
            TeamColumn(m.away, Sports.follows(m.copy(home = ""), teams), 60.dp, 14, false, Modifier.weight(1f))
        }
        if (live) {
            ProgressLine(
                ((now - m.start).toFloat() / (m.stop - m.start).coerceAtLeast(1)),
                Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                Text(clock(m.start), color = Onda.textFaint, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(clock(m.stop), color = Onda.textFaint, fontSize = 11.sp)
            }
        }
        if (m.channels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f)).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 13.dp)) {
                Text(
                    "ONDE ASSISTIR", color = Onda.textFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.channels.forEach { ch -> ChannelChip(ch) { play(ch.toRequest()) } }
                }
            }
        }
    }
}

@Composable
private fun ChannelChip(ch: Item, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape)
            .background(Color.White.copy(alpha = if (focused) 0.24f else 0.12f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.12f), shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelMark(ch)
        Text(ch.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 7.dp))
    }
}

/** Channel logo, or a white play square when the channel has none. */
@Composable
private fun ChannelMark(ch: Item) {
    if (ch.logo != null) {
        AsyncImage(
            model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp),
        )
    } else {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(14.dp))
        }
    }
}

/** Night stadium with floodlights, drawn so no image has to be downloaded. */
private fun DrawScope.drawStadium() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF0A1630), 0.38f to Color(0xFF13284A), 0.52f to Color(0xFF1A2E44),
            0.64f to Color(0xFF173A2A), 1f to Color(0xFF1E5A34),
        ),
    )
    // Stands.
    drawRect(Color(0xFF1E2D42), topLeft = Offset(0f, h * 0.46f), size = Size(w, h * 0.18f))
    var x = 0f
    val step = 7.dp.toPx()
    while (x < w) {
        drawRect(Color.White.copy(alpha = 0.06f), topLeft = Offset(x, h * 0.46f), size = Size(3.dp.toPx(), h * 0.18f))
        x += step
    }
    // Pitch line.
    drawOval(
        Color.White.copy(alpha = 0.18f), topLeft = Offset(-w * 0.1f, h * 0.7f), size = Size(w * 1.2f, h * 0.6f),
        style = Stroke(2.dp.toPx()),
    )
    // Floodlights and their glow.
    listOf(0.12f, 0.88f).forEach { fx ->
        val c = Offset(w * fx, h * 0.04f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xD9CFE6FF), Color(0x66BEDCFF), Color(0x00BEDCFF)), center = c, radius = w * 0.5f),
            radius = w * 0.5f, center = c,
        )
        val lw = 34.dp.toPx()
        drawRoundRect(
            Color(0xFFEAF4FF), topLeft = Offset(c.x - lw / 2, 6.dp.toPx()), size = Size(lw, 8.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.15f), 0.4f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.55f),
        ),
    )
}

/** Card of "Jogos dos seus times" on the Home screen: stadium, time on top, crests with X, channels below. */
@Composable
fun HomeMatchCard(m: Match, now: Long, play: (PlayRequest) -> Unit, modifier: Modifier = Modifier) {
    val live = m.isLive(now)
    Box(
        modifier
            .height(204.dp)
            .shadow(14.dp, CardShape, ambientColor = GlowBlue, spotColor = GlowBlue)
            .clip(CardShape)
            .drawBehind { drawStadium() }
            .border(1.5.dp, GlowBlue, CardShape),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (live) LivePill("AO VIVO · " + clock(m.start)) else Text(
                dayLabel(m.start) + " · " + clock(m.start), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x990A0E18))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
            m.competition?.let {
                Text(
                    it.uppercase(), color = Onda.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp, start = 12.dp, end = 12.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                TeamColumn(m.home, false, 46.dp, 13, true, Modifier.weight(1f))
                Text(
                    "X", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(shadow = Shadow(GlowBlue, blurRadius = 18f)),
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
                TeamColumn(m.away, false, 46.dp, 13, true, Modifier.weight(1f))
            }
        }
        if (m.channels.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                    .background(Color(0xB8080C16))
                    .drawBehind { drawLine(GlowBlue.copy(alpha = 0.35f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                m.channels.forEachIndexed { i, ch ->
                    if (i > 0) Box(Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.2f)))
                    FooterChannel(ch) { play(ch.toRequest()) }
                }
            }
        }
    }
}

@Composable
private fun FooterChannel(ch: Item, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier.height(40.dp)
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.18f)) else Modifier)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelMark(ch)
        Text(ch.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamsDialog(
    followed: Set<String>,
    suggestions: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = Sports.norm(query)
    val list = remember(suggestions, q) {
        suggestions.filter { q.isEmpty() || Sports.norm(it).contains(q) }.take(40)
    }
    val typed = query.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meus times") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (followed.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        followed.sorted().forEach { t ->
                            Row(
                                Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)
                                    .clickable { onToggle(t) }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t, color = Color.Black, fontSize = 13.sp)
                                Icon(Icons.Filled.Close, "Deixar de seguir", tint = Color.Black, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    label = { Text("Buscar ou digitar um time") },
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    if (typed.length >= 2 && list.none { Sports.norm(it) == q } && typed !in followed) {
                        item { TeamRow("Seguir \"$typed\"", false) { onToggle(typed); query = "" } }
                    }
                    items(list, key = { it }) { t -> TeamRow(t, t in followed) { onToggle(t) } }
                    if (list.isEmpty() && typed.length < 2) {
                        item { Text("Nenhum time no guia ainda. Digite o nome do seu time.", color = Onda.textFaint, fontSize = 13.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Concluído") } },
    )
}

@Composable
private fun TeamRow(name: String, followed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            if (followed) Icons.Filled.Star else Icons.Filled.StarBorder, null,
            tint = if (followed) Onda.gold else Onda.textFaint, modifier = Modifier.size(22.dp),
        )
    }
}
