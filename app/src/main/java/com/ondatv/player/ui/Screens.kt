package com.ondatv.player.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ondatv.player.AppViewModel
import com.ondatv.player.PlayRequest
import com.ondatv.player.data.Item
import com.ondatv.player.data.Kind
import kotlinx.coroutines.delay

// Special category ids used in routes.
const val G_FAV = "__fav"
const val G_RECENT = "__recent"
const val G_NEW = "__new"
const val G_TREND = "__trend"
// Home "Em Alta": this year's releases.
const val G_RELEASES = "__releases"

fun Item.toRequest() = PlayRequest(key, name, url, logo, live = kind == Kind.LIVE)

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun LoadingBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    val loading by vm.loading.collectAsStateWithLifecycle()
    if (loading) LinearProgressIndicator(modifier.fillMaxWidth(), color = Color.White, trackColor = Color(0x33FFFFFF))
}

// ---------- Início ----------

private val homeSections = listOf(
    "sports" to "Jogos dos seus times",
    "resume" to "Continue assistindo",
    "live" to "Canais assistidos recentemente",
    "trendSeries" to "Séries em Alta",
    "trendMovies" to "Filmes em Alta",
    "newMovies" to "Adicionados recentemente - Filmes",
    "newSeries" to "Adicionadas recentemente - Séries",
    "favorites" to "Favoritos",
)

@Composable
fun HomeScreen(
    vm: AppViewModel,
    play: (PlayRequest) -> Unit,
    openSeries: (String) -> Unit,
    openSearch: () -> Unit,
    openSettings: () -> Unit,
    openProfiles: () -> Unit,
    openSports: () -> Unit,
    openGrid: (Kind, String) -> Unit,
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val recentLive by vm.recentLive.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    vm.epg.collectAsStateWithLifecycle().value // refresh when the guide arrives
    val now = rememberNow()
    var editing by remember { mutableStateOf(false) }

    // Home rows and banner never show adult content: it stays inside its category, behind the PIN.
    val movies = remember(items) { items.filter { it.kind == Kind.MOVIE && !vm.isAdult(it) } }
    val series = remember(items) { items.filter { it.kind == Kind.SERIES && !vm.isAdult(it) } }
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    // "Em Alta": releases of this year or later (last year too when there are too few).
    val trendMovies = remember(movies, details, year) { releases(movies, details, year) }
    val trendSeries = remember(series, details, year) { releases(series, details, year) }
    val newMovies = remember(movies) { recentlyAdded(movies) }
    val newSeries = remember(series) { recentlyAdded(series) }
    // Banner: the best recent titles, only those the server gave a synopsis for.
    val heroPool = remember(movies, series) { interleave(trending(movies).take(12), trending(series).take(12)) }
    val heroItems = remember(heroPool, details) { bannerItems(heroPool, details, 10) }
    val liveRecent = remember(recentLive, items) { recentLive.mapNotNull { vm.item(it) } }
    // Re-checked when the list changes (lock / unlock), so locked adult progress never shows.
    val resume = remember(progress, items) {
        progress.values.filter { it.inProgress() && !vm.isLockedAdultKey(it.key) }.sortedByDescending { it.updated }.take(20)
    }
    val favShows = remember(items, favorites) { items.filter { it.key in favorites && it.kind != Kind.LIVE } }
    LaunchedEffect(heroPool) { vm.loadDetails(heroPool) }

    // Films and series open their page; channels play at once.
    val open: (Item) -> Unit = { if (it.kind == Kind.LIVE) play(it.toRequest()) else openSeries(it.key) }
    val scrollFocus = remember { FocusRequester() }
    // The buttons over the banner: the remote reaches them with "up" from the banner.
    val topFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(Onda.heroBg)) {
        Column(Modifier.fillMaxSize().focusRequester(scrollFocus).focusGroup().verticalScroll(rememberScrollState())) {
            if (sources.isEmpty()) {
                Spacer(Modifier.statusBarsPadding().height(120.dp))
                EmptyState("Nenhuma lista ainda.\nAdicione sua lista M3U ou conta Xtream Codes para começar.")
                Button(
                    onClick = openSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Adicionar lista") }
                return@Column
            }
            Hero(heroItems, details, open, topFocus)
            LoadingBar(vm)
            message?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(16.dp)) }

            if ("sports" !in hidden) SportsRow(vm, play, openSports)

            if ("resume" !in hidden && resume.isNotEmpty()) {
                SectionHeader("Continue assistindo")
                val w = posterWidth()
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(resume, key = { it.key }) { p ->
                        Poster(
                            p.title, p.logo, w,
                            onClick = { play(PlayRequest(p.key, p.title, p.url, p.logo, live = false)) },
                            progress = p.position.toFloat() / p.duration,
                            subtitle = remaining(p),
                        )
                    }
                }
            }
            if ("live" !in hidden && liveRecent.isNotEmpty()) {
                SectionHeader("Canais assistidos recentemente")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(liveRecent, key = { it.key }) { ch ->
                        LiveCard(ch, vm.nowNext(ch, now).first, now) { play(ch.toRequest()) }
                    }
                }
            }
            if ("trendSeries" !in hidden && trendSeries.isNotEmpty()) {
                SectionHeader("Séries em Alta", onMore = { openGrid(Kind.SERIES, G_RELEASES) })
                PosterRow(trendSeries, ranked = true, onClick = open)
            }
            if ("trendMovies" !in hidden && trendMovies.isNotEmpty()) {
                SectionHeader("Filmes em Alta", onMore = { openGrid(Kind.MOVIE, G_RELEASES) })
                PosterRow(trendMovies, ranked = true, onClick = open)
            }
            if ("newMovies" !in hidden && newMovies.isNotEmpty()) {
                SectionHeader("Adicionados recentemente", "Filmes")
                PosterRow(newMovies, onClick = open)
            }
            if ("newSeries" !in hidden && newSeries.isNotEmpty()) {
                SectionHeader("Adicionadas recentemente", "Séries")
                PosterRow(newSeries, onClick = open)
            }
            if ("favorites" !in hidden && favShows.isNotEmpty()) {
                SectionHeader("Favoritos")
                PosterRow(favShows, onClick = open)
            }
            Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Editar", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF2A2B33))
                        .clickable { editing = true }.padding(horizontal = 34.dp, vertical = 12.dp),
                )
            }
            BottomSpace()
        }
        CompositionLocalProvider(LocalContentFocus provides scrollFocus) { TopControls(
            focus = topFocus,
            left = { AvatarButton(vm, openProfiles) },
            right = {
                GlassPill {
                    PillIcon(Icons.Filled.Search, "Buscar", openSearch)
                    PillIcon(Icons.Filled.Stadium, "Esportes", openSports)
                }
            },
        ) }
    }

    if (editing) {
        var draft by remember { mutableStateOf(hidden) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Seções do Início") },
            text = {
                Column {
                    homeSections.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { draft = if (id in draft) draft - id else draft + id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = id !in draft, onCheckedChange = { draft = if (it) draft - id else draft + id })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.setHidden(draft); editing = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
        )
    }
}

// ---------- Filmes / Séries ----------

@Composable
fun BrowseScreen(
    vm: AppViewModel,
    kind: Kind,
    play: (PlayRequest) -> Unit,
    openSeries: (String) -> Unit,
    openSearch: () -> Unit,
    openCategories: () -> Unit,
    openGrid: (String) -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }

    // Banner and rows leave adult content out; it is reached through its category, behind the PIN.
    val list = remember(items, kind) { items.filter { it.kind == kind && !vm.isAdult(it) } }
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val trend = remember(list) { trending(list) }
    // "Em Alta": releases of this year or later (last year too when there are too few).
    val hot = remember(list, details, year) { releases(list, details, year) }
    val recent = remember(list) { recentlyAdded(list) }
    val byGroup = remember(list) { list.groupBy { it.group } }
    val favs = remember(list, favorites) { list.filter { it.key in favorites } }
    // Banner: the best recent titles, only those the server gave a synopsis for.
    val heroPool = remember(trend) { if (kind == Kind.MOVIE) trend.take(12) else trend }
    val heroItems = remember(heroPool, details) { bannerItems(heroPool, details, if (kind == Kind.MOVIE) 5 else 10) }
    LaunchedEffect(heroPool) { vm.loadDetails(heroPool) }

    val open: (Item) -> Unit = { if (it.kind == Kind.LIVE) play(it.toRequest()) else openSeries(it.key) }
    val scrollFocus = remember { FocusRequester() }
    // The buttons over the banner: the remote reaches them with "up" from the banner.
    val topFocus = remember { FocusRequester() }
    val noun = if (kind == Kind.MOVIE) "Filmes" else "Séries"

    Box(Modifier.fillMaxSize().background(Onda.heroBg)) {
        Column(Modifier.fillMaxSize().focusRequester(scrollFocus).focusGroup().verticalScroll(rememberScrollState())) {
            Hero(heroItems, details, open, topFocus)
            LoadingBar(vm)
            if (list.isEmpty()) EmptyState("Nenhum conteúdo aqui ainda.")
            if (hot.isNotEmpty()) {
                SectionHeader("$noun em Alta", onMore = { openGrid(G_RELEASES) })
                PosterRow(hot, ranked = true, onClick = open)
            }
            if (recent.isNotEmpty()) {
                SectionHeader("Adicionados Recentemente", onMore = { openGrid(G_NEW) })
                PosterRow(recent, onClick = open)
            }
            if (favs.isNotEmpty()) {
                SectionHeader("Favoritos", onMore = { openGrid(G_FAV) })
                PosterRow(favs, onClick = open)
            }
            byGroup.entries.take(15).forEach { (group, inGroup) ->
                SectionHeader(group, onMore = { openGrid(group) })
                PosterRow(inGroup.take(20), onClick = open)
            }
            BottomSpace()
        }
        CompositionLocalProvider(LocalContentFocus provides scrollFocus) { TopControls(
            focus = topFocus,
            left = { GlassCircle(Icons.Filled.Menu, "Categorias", openCategories) },
            right = {
                GlassPill {
                    Box {
                        PillIcon(Icons.Filled.MoreHoriz, "Mais", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Categorias") }, onClick = { menu = false; openCategories() })
                            DropdownMenuItem(text = { Text("Atualizar listas") }, onClick = { menu = false; vm.refresh() })
                        }
                    }
                    PillIcon(Icons.Filled.Search, "Buscar", openSearch)
                }
            },
        ) }
    }
}

// ---------- Direto ----------

@Composable
fun LiveMenuScreen(vm: AppViewModel, openSearch: () -> Unit, openMultiview: () -> Unit, openGroup: (String) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    vm.sources.collectAsStateWithLifecycle().value
    val adultGroups by vm.adultGroups.collectAsStateWithLifecycle()
    val unlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    val parentalOn by vm.parentalOn.collectAsStateWithLifecycle()
    // Adult categories always come last; they ask for the PIN while locked.
    val groups = remember(items, adultGroups) { vm.liveGroups(items) }
    var askPinFor by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Onda.listBackground)) {
        LargeTitle(vm.liveTitle()) {
            GlassCircle(Icons.Filled.Search, "Buscar", openSearch)
        }
        LoadingBar(vm)
        LazyColumn(Modifier.fillMaxSize()) {
            item { MenuRow(Icons.Filled.Favorite, "Favoritos") { openGroup(G_FAV) } }
            item { MenuRow(Icons.Filled.GridView, "Multitela") { openMultiview() } }
            item { MenuRow(Icons.Filled.History, "Vistos recentemente") { openGroup(G_RECENT) } }
            items(groups, key = { it }) { g ->
                val adult = g in adultGroups
                val locked = adult && parentalOn && !unlocked
                MenuRow(if (locked) Icons.Filled.Lock else Icons.Filled.Folder, g) {
                    if (locked) askPinFor = g else openGroup(g)
                }
            }
            item { BottomSpace() }
        }
    }
    askPinFor?.let { g ->
        AdultPinPad(vm, onSuccess = { askPinFor = null; openGroup(g) }, onDismiss = { askPinFor = null })
    }
}

/**
 * PIN screen for adult content; opens it for the rest of the session. Before the user ever
 * created a PIN it asks to create one first (and that opens the content too).
 */
@Composable
fun AdultPinPad(vm: AppViewModel, onSuccess: () -> Unit, onDismiss: () -> Unit, title: String = "Canais adultos") {
    val created by vm.pinCreated.collectAsStateWithLifecycle()
    if (!created) {
        CreatePinDialog(vm, onDone = { pin -> vm.unlockAdult(pin); onSuccess() }, onDismiss = onDismiss)
        return
    }
    PinPad(
        title = title,
        subtitle = "Digite a senha do Controle Parental",
        check = { vm.unlockAdult(it) },
        onSuccess = onSuccess,
        onDismiss = onDismiss,
    )
}

/** First PIN of the Controle Parental: new PIN and confirmation, 4 to 8 digits. */
@Composable
fun CreatePinDialog(vm: AppViewModel, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    @Composable
    fun PinField(value: String, label: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value, { v -> onChange(v.filter { it.isDigit() }.take(8)); error = null },
            label = { Text(label) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar senha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Crie a senha do Controle Parental. Ela será pedida para abrir o conteúdo adulto.", fontSize = 14.sp)
                PinField(new, "Nova senha (4 a 8 números)") { new = it }
                PinField(confirm, "Confirmar senha") { confirm = it }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (vm.createParentalPin(new, confirm)) {
                    AppViewModel.PinChange.OK -> onDone(new)
                    AppViewModel.PinChange.TOO_SHORT -> error = "A senha precisa ter de 4 a 8 números."
                    AppViewModel.PinChange.MISMATCH -> error = "As senhas não conferem."
                    AppViewModel.PinChange.WRONG_CURRENT -> error = "Já existe uma senha: use Alterar senha."
                }
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

fun groupTitle(group: String): String = when (group) {
    G_FAV -> "Favoritos"
    G_RECENT -> "Vistos recentemente"
    G_NEW -> "Adicionados Recentemente"
    G_TREND, G_RELEASES -> "Em Alta"
    else -> group
}

@Composable
fun ChannelListScreen(vm: AppViewModel, startGroup: String, play: (PlayRequest) -> Unit, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recentLive by vm.recentLive.collectAsStateWithLifecycle()
    vm.epg.collectAsStateWithLifecycle().value
    val now = rememberNow()
    val context = LocalContext.current
    var group by rememberSaveable { mutableStateOf(startGroup) }
    var picking by remember { mutableStateOf(false) }

    val adultGroups by vm.adultGroups.collectAsStateWithLifecycle()
    val unlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    val parentalOn by vm.parentalOn.collectAsStateWithLifecycle()
    val live = remember(items) { items.filter { it.kind == Kind.LIVE } }
    val groups = remember(items, adultGroups) { vm.liveGroups(items) }
    val lockedHere = group in adultGroups && parentalOn && !unlocked
    val shown = remember(live, group, favorites, recentLive) {
        when (group) {
            G_FAV -> live.filter { it.key in favorites }
            G_RECENT -> live.associateBy { it.key }.let { m -> recentLive.mapNotNull { m[it] } }
            else -> live.filter { it.group == group }
        }
    }

    Column(Modifier.fillMaxSize().background(Onda.listBackground)) {
        Box {
            CenterBar(
                groupTitle(group), onBack, onTitle = { picking = true },
                action = { GlassCircle(Icons.AutoMirrored.Filled.FormatListBulleted, "Categorias", { picking = true }) },
            )
            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                (listOf(G_FAV, G_RECENT) + groups).forEach { g ->
                    DropdownMenuItem(text = { Text(groupTitle(g)) }, onClick = { group = g; picking = false })
                }
            }
        }
        if (lockedHere) {
            // Reached while locked (e.g. after the session locked again): ask before showing anything.
            AdultPinPad(vm, onSuccess = {}, onDismiss = {
                if (vm.adultLocked()) { if (startGroup == group) onBack() else group = startGroup }
            })
            return@Column
        }
        if (shown.isEmpty()) EmptyState("Nenhum canal aqui.")
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.key }) { ch ->
                ChannelRow(
                    ch, vm.nowNext(ch, now).first, now,
                    onClick = { play(ch.toRequest()) },
                    onLongClick = {
                        val wasFav = ch.key in favorites
                        vm.toggleFavorite(ch.key)
                        Toast.makeText(
                            context,
                            if (wasFav) "Removido dos favoritos" else "Adicionado aos favoritos",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
            item { BottomSpace() }
        }
    }
}
