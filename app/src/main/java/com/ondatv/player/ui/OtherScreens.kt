package com.ondatv.player.ui

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

    Box(Modifier.fillMaxSize().background(Onda.heroBg)) {
        LazyColumn(Modifier.fillMaxSize()) {
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
                    val current = season ?: seasons.first()
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasons) { s ->
                                val sel = s == current
                                Text(
                                    "Temporada $s",
                                    color = if (sel) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                                        .background(if (sel) Color.White else Onda.glass)
                                        .clickable { season = s }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    // The whole series in order, so the player moves on to the next episode
                    // (and from the last of a season to the first of the next) by itself.
                    val queue = eps.sortedWith(compareBy({ it.season }, { it.number })).map { ep ->
                        PlayRequest("${item.key}:${ep.id}", "${item.name} - T${ep.season}E${ep.number}", ep.url, item.logo, live = false)
                    }
                    item {
                        val inSeason = eps.filter { it.season == current }
                        val width = episodeCardWidth()
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(inSeason, key = { it.id }) { ep ->
                                val epKey = "${item.key}:${ep.id}"
                                val p = progress[epKey]
                                EpisodeCard(
                                    number = ep.number, title = ep.title, image = item.backdrop ?: item.logo,
                                    progress = p?.takeIf { it.duration > 0 }?.let { it.position.toFloat() / it.duration },
                                    width = width,
                                ) {
                                    val start = queue.first { it.key == epKey }
                                    play(start.copy(queue = queue))
                                }
                            }
                        }
                    }
                }
            }
            item { BottomSpace() }
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

// ---------- Definições ----------

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, openProfiles: () -> Unit) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    vm.profileId.collectAsStateWithLifecycle().value
    vm.profiles.collectAsStateWithLifecycle().value
    var adding by remember { mutableStateOf<SourceType?>(null) }
    var removing by remember { mutableStateOf<Source?>(null) }

    Column(Modifier.fillMaxSize().background(Onda.listBackground).verticalScroll(rememberScrollState())) {
        // Same as the large title of the other screens, with a way back (no longer a tab).
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircle(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Voltar", onBack)
            Text(
                "Definições", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            GlassCircle(Icons.Filled.Refresh, "Atualizar listas", { vm.refresh() })
        }
        LoadingBar(vm)
        SettingsLabel("Perfil")
        val me = vm.currentProfile()
        Row(
            Modifier.fillMaxWidth().clickable(onClick = openProfiles).height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(me, 40.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(me.name, color = Color.White, fontSize = 17.sp)
                Text("Trocar ou gerenciar perfis", color = Onda.textFaint, fontSize = 13.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = Onda.textFaint, modifier = Modifier.size(22.dp),
            )
        }
        SettingsLabel("Minha lista")
        // One list at a time: its details and "Excluir lista", or the two ways to add one.
        sources.take(1).forEach { s ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, null, tint = Onda.textDim, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text(s.name, color = Color.White, fontSize = 17.sp)
                    Text(
                        if (s.type == SourceType.M3U) "Lista M3U" else "Xtream Codes" + DOT + s.server,
                        color = Onda.textFaint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    ExpiryLine(s.expiresAt, Modifier.padding(top = 6.dp))
                }
            }
            HorizontalDivider(color = Onda.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
            DangerRow("Excluir lista") { removing = s }
        }
        if (sources.isEmpty()) {
            Text(
                "Nenhuma lista cadastrada. Escolha como conectar:",
                color = Onda.textFaint, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            MenuRow(Icons.Filled.Add, "Adicionar lista M3U") { adding = SourceType.M3U }
            MenuRow(Icons.Filled.Add, "Adicionar Xtream Codes") { adding = SourceType.XTREAM }
        }
        ParentalSection(vm)
        SettingsLabel("Sobre")
        Text(
            "MBTV é apenas um player. Ele não fornece canais, filmes nem assinaturas: " +
                "use somente listas que você tem direito de assistir.",
            color = Onda.textFaint, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        BottomSpace()
    }

    // Only reachable while there is no list; addSource refuses a second one anyway.
    adding?.takeIf { sources.isEmpty() }?.let { type ->
        AddSourceDialog(type, onDismiss = { adding = null }) {
            vm.addSource(it)
            adding = null
        }
    }
    removing?.let { s ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Excluir lista") },
            text = {
                Text("Tem certeza de que deseja excluir esta lista?\n\nOs dados de conexão de \"${s.name}\", os perfis, os favoritos e o progresso serão apagados deste aparelho.")
            },
            confirmButton = {
                TextButton(onClick = { vm.removeSource(s.id); removing = null }) { Text("Excluir", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}

/** Destructive action row in the settings list style, in red. */
@Composable
private fun DangerRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(24.dp))
        Text(label, color = Color(0xFFFF6B6B), fontSize = 17.sp, modifier = Modifier.padding(start = 18.dp))
    }
    HorizontalDivider(color = Onda.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
}

// ---------- Controle Parental ----------

@Composable
private fun ParentalSection(vm: AppViewModel) {
    val on by vm.parentalOn.collectAsStateWithLifecycle()
    val created by vm.pinCreated.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askPinToToggle by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    SettingsLabel("Controle Parental")
    Row(
        // Without a PIN of the user yet, it is created first.
        Modifier.fillMaxWidth().clickable { if (created) askPinToToggle = true else creating = true }.heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, null, tint = Onda.textDim, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            Text("Proteção por senha", color = Color.White, fontSize = 17.sp)
            Text(
                if (on) "Conteúdo adulto pede senha" else "Desativada: conteúdo adulto sem senha",
                color = Onda.textFaint, fontSize = 13.sp,
            )
        }
        Switch(
            checked = on, onCheckedChange = { if (created) askPinToToggle = true else creating = true },
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4CC38A), checkedThumbColor = Color.White),
        )
    }
    HorizontalDivider(color = Onda.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
    if (created) MenuRow(Icons.Filled.Password, "Alterar senha") { changing = true }
    else MenuRow(Icons.Filled.Password, "Criar senha") { creating = true }

    if (creating) {
        CreatePinDialog(vm, onDone = {
            creating = false
            Toast.makeText(context, "Senha criada", Toast.LENGTH_SHORT).show()
        }, onDismiss = { creating = false })
    }

    if (askPinToToggle) {
        PinPad(
            title = if (on) "Desativar proteção" else "Ativar proteção",
            subtitle = "Digite a senha atual do Controle Parental",
            check = { vm.setParentalEnabled(!on, it) },
            onSuccess = {
                askPinToToggle = false
                Toast.makeText(context, if (on) "Proteção desativada" else "Proteção ativada", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { askPinToToggle = false },
        )
    }
    if (changing) {
        ChangePinDialog(vm, onDone = {
            changing = false
            Toast.makeText(context, "Senha alterada com sucesso", Toast.LENGTH_SHORT).show()
        }, onDismiss = { changing = false })
    }
}

@Composable
private fun ChangePinDialog(vm: AppViewModel, onDone: () -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
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
        title = { Text("Alterar senha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(current, "Senha atual") { current = it }
                PinField(new, "Nova senha (4 a 8 números)") { new = it }
                PinField(confirm, "Confirmar nova senha") { confirm = it }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (vm.changeParentalPin(current, new, confirm)) {
                    AppViewModel.PinChange.OK -> onDone()
                    AppViewModel.PinChange.WRONG_CURRENT -> error = "Senha atual incorreta. Tente novamente."
                    AppViewModel.PinChange.TOO_SHORT -> error = "A nova senha deve ter de 4 a 8 números."
                    AppViewModel.PinChange.MISMATCH -> error = "A confirmação não corresponde à nova senha."
                }
            }) { Text("Salvar nova senha") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private val expiryDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
private val expiryGreen = Color(0xFF4CC38A)
private val expiryAmber = Color(0xFFF2B65A)
private val expiryRed = Color(0xFFFF6B6B)

/** Days before the end date from which a list shows "Vence em X dias" instead of "Ativa". */
private const val EXPIRY_WARNING_DAYS = 7

/**
 * The list's expiry as its server reports it: date (DD/MM/AAAA) and a coloured status
 * in the same chip style as the app's other badges.
 */
@Composable
private fun ExpiryLine(expiresAt: Long?, modifier: Modifier = Modifier) {
    if (expiresAt == null) {
        Text("Sem data de vencimento", color = Onda.textFaint, fontSize = 13.sp, modifier = modifier)
        return
    }
    val leftMs = expiresAt * 1000 - System.currentTimeMillis()
    val days = ((leftMs + 86_399_999) / 86_400_000).toInt()
    val (label, color) = when {
        leftMs <= 0 -> "Vencida" to expiryRed
        days <= EXPIRY_WARNING_DAYS -> (if (days == 1) "Vence em 1 dia" else "Vence em $days dias") to expiryAmber
        else -> "Ativa" to expiryGreen
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Text(
            (if (leftMs <= 0) "Venceu em " else "Vence em ") + expiryDate.format(Date(expiresAt * 1000)),
            color = Onda.textDim, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text.uppercase(), color = Onda.textFaint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun AddSourceDialog(type: SourceType, onDismiss: () -> Unit, onSave: (Source) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val valid = if (type == SourceType.M3U) url.isNotBlank() else server.isNotBlank() && user.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == SourceType.M3U) "Lista M3U" else "Xtream Codes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (opcional)") }, singleLine = true)
                if (type == SourceType.M3U) {
                    OutlinedTextField(
                        url, { url = it }, label = { Text("Endereco da lista (http...)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        epg, { epg = it }, label = { Text("Guia EPG XMLTV (opcional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                } else {
                    OutlinedTextField(
                        server, { server = it }, label = { Text("Servidor (http://host:porta)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Senha") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val fallback = if (type == SourceType.M3U) "Minha lista" else server.substringAfter("://").substringBefore('/')
                onSave(
                    Source(
                        id = "", name = name.ifBlank { fallback }, type = type,
                        url = url.trim(), epgUrl = epg.trim(),
                        server = server.trim(), username = user.trim(), password = pass,
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
