package com.ondatv.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.layout.heightIn
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ondatv.player.AppViewModel
import com.ondatv.player.PlayRequest
import com.ondatv.player.data.Episode
import com.ondatv.player.data.Item
import com.ondatv.player.data.Kind
import com.ondatv.player.data.Source
import com.ondatv.player.data.SourceType

// ---------- Categorias de Filmes / Séries ----------

@Composable
fun CategoriesScreen(vm: AppViewModel, kind: Kind, onBack: () -> Unit, openGrid: (String) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val adultByKind by vm.adultVodGroups.collectAsStateWithLifecycle()
    val unlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    val parentalOn by vm.parentalOn.collectAsStateWithLifecycle()
    // Adult categories stay in the list (last), even while locked; only their content is hidden.
    val normal = remember(items, kind) {
        items.asSequence().filter { it.kind == kind && !vm.isAdult(it) }.map { it.group }.distinct().toList()
    }
    val adultOnly = remember(normal, adultByKind, kind) { adultByKind[kind].orEmpty().filterNot { it in normal } }
    var askPinFor by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Onda.listBackground)) {
        CenterBar(if (kind == Kind.MOVIE) "Filmes" else "Séries", onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item { MenuRow(Icons.Filled.Favorite, "Favoritos") { openGrid(G_FAV) } }
            item { MenuRow(Icons.Filled.Whatshot, "Em Alta") { openGrid(G_TREND) } }
            item { MenuRow(Icons.Filled.EditCalendar, "Adicionados Recentemente") { openGrid(G_NEW) } }
            items(normal + adultOnly, key = { it }) { g ->
                val locked = g in adultOnly && parentalOn && !unlocked
                MenuRow(if (locked) Icons.Filled.Lock else Icons.Filled.Folder, g) {
                    if (locked) askPinFor = g else openGrid(g)
                }
            }
            item { BottomSpace() }
        }
    }
    askPinFor?.let { g ->
        AdultPinPad(vm, onSuccess = { askPinFor = null; openGrid(g) }, onDismiss = { askPinFor = null }, title = "Conteúdo adulto")
    }
}

// ---------- Grade de pôsteres ----------

@Composable
fun GridScreen(
    vm: AppViewModel,
    kind: Kind,
    group: String,
    play: (PlayRequest) -> Unit,
    openSeries: (String) -> Unit,
    onBack: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val adultByKind by vm.adultVodGroups.collectAsStateWithLifecycle()
    val unlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    val parentalOn by vm.parentalOn.collectAsStateWithLifecycle()
    var sortAz by rememberSaveable { mutableStateOf(false) }
    val list = remember(items, kind) { items.filter { it.kind == kind } }
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val shown = remember(list, group, favorites, sortAz, details) {
        val base = when (group) {
            G_FAV -> list.filter { it.key in favorites }
            // Highlight lists never carry adult content.
            G_TREND -> trending(list.filterNot { vm.isAdult(it) })
            G_RELEASES -> releases(list.filterNot { vm.isAdult(it) }, details, year, max = 200)
            G_NEW -> list.filterNot { vm.isAdult(it) }.sortedByDescending { it.added }.take(200)
            else -> list.filter { it.group == group }
        }
        if (sortAz) base.sortedBy { it.name.lowercase() } else base
    }
    // An adult category opened while locked (for example after Back) asks for the PIN again.
    val lockedHere = parentalOn && !unlocked && kind != Kind.LIVE && group in adultByKind[kind].orEmpty() &&
        list.none { it.group == group && !vm.isAdult(it) }
    val width = posterWidth()

    Column(Modifier.fillMaxSize().background(Onda.listBackground)) {
        CenterBar(groupTitle(group), onBack, action = {
            GlassCircle(Icons.AutoMirrored.Filled.Sort, if (sortAz) "Ordem da lista" else "Ordenar A-Z", { sortAz = !sortAz })
        })
        if (shown.isEmpty()) EmptyState("Nada aqui ainda.")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(width),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.key }) { item ->
                Poster(
                    item.name, item.logo, width,
                    onClick = { if (kind == Kind.LIVE) play(item.toRequest()) else openSeries(item.key) },
                    rank = if ((group == G_TREND || group == G_RELEASES) && !sortAz) shown.indexOf(item) + 1 else null,
                    rating = item.rating,
                )
            }
        }
    }
    if (lockedHere) {
        AdultPinPad(vm, onSuccess = {}, onDismiss = onBack, title = "Conteúdo adulto")
    }
}

// ---------- Detalhe da série ----------

@Composable
fun SeriesScreen(vm: AppViewModel, key: String, play: (PlayRequest) -> Unit, onBack: () -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val item = remember(key) { vm.item(key) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(item) {
        if (item == null) return@LaunchedEffect
        try {
            episodes = vm.episodes(item)
        } catch (e: Exception) {
            error = e.message ?: "Erro ao carregar episódios"
            episodes = emptyList()
        }
    }

    // Inside a season of a series with several, "back" returns to the list of seasons.
    val inSeason = season != null && (episodes?.map { it.season }?.distinct()?.size ?: 0) > 1
    val back = { if (inSeason) season = null else onBack() }
    BackHandler(enabled = inSeason) { season = null }

    // The remote's "down" from the buttons over the picture goes into the seasons and episodes.
    val listFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(Onda.heroBg)) {
        LazyColumn(Modifier.fillMaxSize().focusRequester(listFocus).focusGroup()) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(1.1f)) {
                    AsyncImage(
                        model = item?.backdrop ?: item?.logo, contentDescription = item?.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0.4f to Color.Transparent, 1f to Onda.heroBg,
                            )
                        )
                    )
                    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            item?.name ?: "Série", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(item?.year, item?.genre).joinToString(DOT)
                        if (meta.isNotEmpty()) Text(meta, color = Onda.textDim, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item?.plot?.let {
                    Text(it, color = Onda.textDim, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            val eps = episodes
            when {
                item == null -> item { EmptyState("Série não encontrada. Atualize as listas.") }
                eps == null -> item { LoadingBar(vm) }
                eps.isEmpty() -> item { EmptyState(error ?: "Nenhum episódio encontrado.") }
                else -> {
                    val seasons = eps.map { it.season }.distinct().sorted()
                    // Several seasons: the list of seasons first; one: its episodes at once.
                    val open = season?.takeIf { it in seasons } ?: seasons.singleOrNull()
                    if (open == null) {
                        item { SectionHeader("Temporadas") }
                        items(seasons, key = { "season$it" }) { s ->
                            val count = eps.count { it.season == s }
                            SeasonRow("Temporada $s", if (count == 1) "1 episódio" else "$count episódios") { season = s }
                        }
                    } else {
                        // The whole series in order, so the player moves on to the next episode
                        // (and from the last of a season to the first of the next) by itself.
                        val queue = eps.map { ep ->
                            PlayRequest("${item.key}:${ep.id}", "${item.name} - T${ep.season}E${ep.number}", ep.url, item.logo, live = false)
                        }
                        item { SectionHeader("Temporada $open") }
                        items(eps.filter { it.season == open }.sortedBy { it.number }, key = { it.id }) { ep ->
                            val epKey = "${item.key}:${ep.id}"
                            val p = progress[epKey]
                            EpisodeRow(
                                number = ep.number, title = ep.title, plot = ep.plot,
                                image = ep.image ?: item.backdrop ?: item.logo,
                                progress = p?.takeIf { it.duration > 0 }?.let { it.position.toFloat() / it.duration },
                            ) {
                                val start = queue.first { it.key == epKey }
                                play(start.copy(queue = queue))
                            }
                        }
                    }
                }
            }
            item { BottomSpace() }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
            CompositionLocalProvider(LocalContentFocus provides listFocus, LocalInOverlay provides true) {
                GlassCircle(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Voltar", back)
                Spacer(Modifier.weight(1f))
                if (item != null) {
                    val fav = item.key in favorites
                    GlassCircle(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito", { vm.toggleFavorite(item.key) })
                }
            }
        }
    }
}

// ---------- Buscar ----------

@Composable
fun SearchScreen(vm: AppViewModel, play: (PlayRequest) -> Unit, openSeries: (String) -> Unit, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(items, query) {
        val q = query.trim()
        if (q.length < 2) emptyList() else items.filter { it.name.contains(q, ignoreCase = true) }.take(300)
    }
    Column(Modifier.fillMaxSize().background(Onda.listBackground)) {
        CenterBar("Buscar", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Canal, filme ou série") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (query.trim().length >= 2 && results.isEmpty()) EmptyState("Nada encontrado.")
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(results, key = { it.key }) { item -> SearchRow(item) {
                if (item.kind == Kind.LIVE) play(item.toRequest()) else openSeries(item.key)
            } }
            item { BottomSpace() }
        }
    }
}

@Composable
private fun SearchRow(item: Item, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 44.dp, height = if (item.kind == Kind.LIVE) 44.dp else 64.dp)
                .clip(RoundedCornerShape(8.dp)).background(Onda.card),
            contentAlignment = Alignment.Center,
        ) {
            Initials(item.name, size = 14)
            AsyncImage(
                model = item.logo, contentDescription = item.name,
                contentScale = if (item.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(item.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (item.kind) { Kind.LIVE -> "Canal"; Kind.MOVIE -> "Filme"; Kind.SERIES -> "Série" } + DOT + item.group,
                color = Onda.textFaint, fontSize = 13.sp, maxLines = 1,
            )
        }
    }
}
