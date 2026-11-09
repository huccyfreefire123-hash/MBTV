package com.ondatv.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ondatv.player.data.Adult
import com.ondatv.player.data.Sports
import com.ondatv.player.data.Details
import com.ondatv.player.data.Episode
import com.ondatv.player.data.Epg
import com.ondatv.player.data.Item
import com.ondatv.player.data.Kind
import com.ondatv.player.data.Loaded
import com.ondatv.player.data.M3u
import com.ondatv.player.data.Net
import com.ondatv.player.data.nameKey
import com.ondatv.player.data.Profile
import com.ondatv.player.data.Programme
import com.ondatv.player.data.Progress
import com.ondatv.player.data.Source
import com.ondatv.player.data.SourceType
import com.ondatv.player.data.Store
import com.ondatv.player.data.Xtream
import com.ondatv.player.data.xtreamBehindM3u
import com.ondatv.player.data.Cache
import com.ondatv.player.data.Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val cache = Cache(app.filesDir)

    private val _sources = MutableStateFlow(store.sources())
    val sources: StateFlow<List<Source>> = _sources

    // Everything the list has; [items] is what the screens may show (adult channels left out while locked).
    private var allItems: List<Item> = emptyList()
    private var allByKey: Map<String, Item> = emptyMap()
    private var visibleByKey: Map<String, Item> = emptyMap()
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    // ---------- Parental control ----------

    private val _parentalOn = MutableStateFlow(store.parentalEnabled())
    val parentalOn: StateFlow<Boolean> = _parentalOn

    // Unlocked for this session only: a new start of the app (or another profile) locks again.
    private val _adultUnlocked = MutableStateFlow(false)
    val adultUnlocked: StateFlow<Boolean> = _adultUnlocked

    /** Adult channel categories in the list, whether locked or not. */
    private val _adultGroups = MutableStateFlow<List<String>>(emptyList())
    val adultGroups: StateFlow<List<String>> = _adultGroups

    /** Adult film and series categories (by kind), kept in the category list behind the PIN. */
    private val _adultVodGroups = MutableStateFlow<Map<Kind, List<String>>>(emptyMap())
    val adultVodGroups: StateFlow<Map<Kind, List<String>>> = _adultVodGroups

    private val _pinCreated = MutableStateFlow(store.parentalPinCreated())
    val pinCreated: StateFlow<Boolean> = _pinCreated

    fun adultLocked(): Boolean = _parentalOn.value && !_adultUnlocked.value

    private fun publish() {
        val adult = allItems.filter { Adult.isAdult(it) }
        _adultGroups.value = adult.asSequence().filter { it.kind == Kind.LIVE }.map { it.group }.distinct().toList()
        _adultVodGroups.value = adult.asSequence().filter { it.kind != Kind.LIVE }
            .groupBy({ it.kind }, { it.group }).mapValues { (_, g) -> g.distinct() }
        // Locked: no adult cover, title or synopsis reaches any screen (rows, banner, search, grids).
        val visible = if (adultLocked() && adult.isNotEmpty()) allItems.filterNot { Adult.isAdult(it) } else allItems
        allByKey = allItems.associateBy { it.key }
        visibleByKey = if (visible === allItems) allByKey else visible.associateBy { it.key }
        _items.value = visible
    }

    /** Opens the adult channels for this session when [pin] is right. */
    fun unlockAdult(pin: String): Boolean {
        if (!store.checkParentalPin(pin)) return false
        _adultUnlocked.value = true
        publish()
        return true
    }

    fun lockAdult() {
        _adultUnlocked.value = false
        publish()
    }

    /** Turns the PIN protection on or off; needs the current PIN. */
    fun setParentalEnabled(on: Boolean, pin: String): Boolean {
        if (!store.checkParentalPin(pin)) return false
        store.setParentalEnabled(on)
        _parentalOn.value = on
        publish()
        return true
    }

    enum class PinChange { OK, WRONG_CURRENT, MISMATCH, TOO_SHORT }

    fun changeParentalPin(current: String, new: String, confirm: String): PinChange {
        if (!store.checkParentalPin(current)) return PinChange.WRONG_CURRENT
        if (new.length !in 4..8 || !new.all { it.isDigit() }) return PinChange.TOO_SHORT
        if (new != confirm) return PinChange.MISMATCH
        store.setParentalPin(new)
        _pinCreated.value = true
        return PinChange.OK
    }

    /** First PIN of the user (no current one to give); only while none was created. */
    fun createParentalPin(new: String, confirm: String): PinChange {
        if (_pinCreated.value) return PinChange.WRONG_CURRENT
        if (new.length !in 4..8 || !new.all { it.isDigit() }) return PinChange.TOO_SHORT
        if (new != confirm) return PinChange.MISMATCH
        store.setParentalPin(new)
        _pinCreated.value = true
        return PinChange.OK
    }

    /** Whether a saved progress (film or episode key) belongs to adult content that is locked now. */
    fun isLockedAdultKey(progressKey: String): Boolean {
        if (!adultLocked()) return false
        val itemKey = progressKey.split(':').take(3).joinToString(":")
        return allByKey[itemKey]?.let { Adult.isAdult(it) } == true
    }

    /** Live categories in list order, with the adult ones moved to the end. */
    fun liveGroups(items: List<Item>): List<String> {
        val normal = items.asSequence().filter { it.kind == Kind.LIVE && !Adult.isAdult(it) }.map { it.group }.distinct().toList()
        return normal + _adultGroups.value.filterNot { it in normal }
    }

    private val _epg = MutableStateFlow<Map<String, List<Programme>>>(emptyMap())
    val epg: StateFlow<Map<String, List<Programme>>> = _epg

    private val _favorites = MutableStateFlow(store.favorites())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _progress = MutableStateFlow(store.progress())
    val progress: StateFlow<Map<String, Progress>> = _progress

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _recentLive = MutableStateFlow(store.recentLive())
    val recentLive: StateFlow<List<String>> = _recentLive

    private val _hidden = MutableStateFlow(store.hiddenSections())
    val hidden: StateFlow<Set<String>> = _hidden

    private val _details = MutableStateFlow<Map<String, Details>>(emptyMap())
    val details: StateFlow<Map<String, Details>> = _details
    private val detailsRequested = HashSet<String>()

    private var refreshJob: Job? = null

    // Games of the "Jogos dos seus times" row: searching the guide is heavy, so it runs in the
    // background each time the list or the guide changes, never while a screen draws.
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches

    init {
        // One list at a time: older installs that still have several keep only the first.
        val extra = _sources.value.drop(1)
        if (extra.isNotEmpty()) {
            _sources.value = _sources.value.take(1)
            store.saveSources(_sources.value)
            extra.forEach { store.purgeSourceData(it.id) }
        }
        viewModelScope.launch {
            combine(_items, _epg) { i, e -> i to e }.collectLatest { (i, e) ->
                _matches.value = withContext(Dispatchers.Default) { Sports.find(i, e, System.currentTimeMillis()) }
            }
        }
        refresh(force = false)
    }

    /**
     * [force]: download the list again even when the saved copy is recent ("Atualizar listas").
     * On start a saved copy younger than [LIST_MAX_AGE] is used as is, so opening the app does not
     * download and read the whole list (often tens of MB) again every time.
     */
    fun refresh(force: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _loading.value = true
            _message.value = null
            val sources = _sources.value
            // First the copy kept on disk, shown at once; the fresh list replaces it when it arrives.
            var cached: List<Pair<Source, Loaded>> = emptyList()
            if (allItems.isEmpty()) {
                cached = withContext(Dispatchers.IO) { sources.mapNotNull { s -> cache.loadList(s.id)?.let { s to it } } }
                if (cached.isNotEmpty()) {
                    allItems = cached.flatMap { it.second.items }
                    publish()
                }
                if (_epg.value.isEmpty()) {
                    withContext(Dispatchers.IO) { cache.loadEpg(System.currentTimeMillis()) }
                        ?.let { _epg.value = it }
                }
                if (_details.value.isEmpty()) {
                    withContext(Dispatchers.IO) { cache.loadDetails() }?.let {
                        _details.value = it
                        detailsRequested.addAll(it.keys)
                    }
                }
            }
            val fresh = !force && sources.isNotEmpty() && cached.size == sources.size &&
                sources.all { (cache.listAge(it.id) ?: Long.MAX_VALUE) < LIST_MAX_AGE }
            if (fresh) {
                _loading.value = false
                if ((cache.epgAge() ?: Long.MAX_VALUE) >= EPG_MAX_AGE) loadGuide(cached)
                return@launch
            }
            val showedCache = allItems.isNotEmpty()
            val errors = ArrayList<String>()
            // Expiry dates as the servers report them now, by source id; a failed check keeps the last one.
            val expiries = HashMap<String, Long?>()
            val loaded = withContext(Dispatchers.IO) {
                sources.mapNotNull { s ->
                    // The expiry check runs alongside the download instead of before it.
                    val expiry = async { runCatching { expiryOf(s) } }
                    val result = try {
                        s to load(s, showEarly = !showedCache)
                    } catch (e: Exception) {
                        errors += "${s.name}: ${e.message ?: e.javaClass.simpleName}"
                        null
                    }
                    expiry.await().onSuccess { expiries[s.id] = it }
                    result
                }
            }
            if (loaded.isNotEmpty() || !showedCache) {
                allItems = loaded.flatMap { it.second.items }
                publish()
                withContext(Dispatchers.IO) { loaded.forEach { (s, l) -> cache.saveList(s.id, l) } }
            }
            if (expiries.isNotEmpty()) {
                // Matched by id, so a list removed or added meanwhile is never given another's date.
                _sources.value = _sources.value.map { s ->
                    if (s.id in expiries) s.copy(expiresAt = expiries[s.id]) else s
                }
                store.saveSources(_sources.value)
            }
            _loading.value = false
            // With the saved copy on screen a failed update stays quiet: everything still works.
            if (errors.isNotEmpty() && allItems.isEmpty()) _message.value = errors.joinToString("\n")
            if (loaded.isEmpty()) return@launch
            loadGuide(loaded)
        }
    }

    /** The guide loads after the lists so channels show up first; one that fails keeps the saved guide. */
    private suspend fun loadGuide(loaded: List<Pair<Source, Loaded>>) {
        val wanted = allItems.asSequence()
            .filter { it.kind == Kind.LIVE }
            .mapNotNull { it.epgId?.lowercase() }
            .toSet()
        val guides = loaded.mapNotNull { (s, l) -> s.epgUrl.ifBlank { null } ?: l.epgUrl }.distinct()
        val merged = HashMap<String, List<Programme>>()
        withContext(Dispatchers.IO) {
            guides.forEach { url ->
                try {
                    Net.open(url).use { merged.putAll(Epg.parse(it, wanted)) }
                } catch (e: Exception) {
                    // A broken guide should not hide the channels.
                }
            }
        }
        if (merged.isNotEmpty() || guides.isEmpty()) {
            _epg.value = merged
            withContext(Dispatchers.IO) { cache.saveEpg(merged) }
        }
    }

    private fun expiryOf(s: Source): Long? = when (s.type) {
        SourceType.XTREAM -> Xtream(s).expiry()
        SourceType.M3U -> xtreamBehindM3u(s.url)?.expiry()
    }

    /**
     * An M3U from an Xtream server has no ratings, guide ids or synopses: ask that server for them
     * (when it answers). Series get their synopsis, banner, genre and year this way.
     */
    private suspend fun withRatings(s: Source, loaded: Loaded): Loaded = coroutineScope {
        val server = xtreamBehindM3u(s.url) ?: return@coroutineScope loaded
        // The guide of that server, when the M3U names none.
        val withGuide = if (loaded.epgUrl.isNullOrBlank()) loaded.copy(epgUrl = server.epgUrl) else loaded
        // The three lists download at the same time.
        val guideIdsJob = async(Dispatchers.IO) { runCatching { server.liveGuideIds() }.getOrDefault(emptyMap()) }
        val moviesJob = async(Dispatchers.IO) { runCatching { server.movieMeta() }.getOrDefault(emptyMap()) }
        val seriesJob = async(Dispatchers.IO) { runCatching { server.seriesMeta() }.getOrDefault(emptyMap()) }
        val guideIds = guideIdsJob.await()
        val movies = moviesJob.await()
        val series = seriesJob.await()
        val streamId = Regex("""/(\d+)(?:\.\w+)?$""")
        withGuide.copy(items = withGuide.items.map { item ->
            when (item.kind) {
                Kind.LIVE -> {
                    val g = streamId.find(item.url.substringBefore('?'))?.groupValues?.get(1)?.let { guideIds[it] }
                    if (g != null) item.copy(epgId = g) else item
                }
                Kind.MOVIE -> {
                    val m = m3uMovieId(item.url)?.let { movies[it] } ?: return@map item
                    item.copy(rating = item.rating ?: m.rating, year = item.year ?: m.year, adult = item.adult || m.adult)
                }
                Kind.SERIES -> {
                    val m = series[nameKey(item.name)] ?: return@map item
                    item.copy(
                        rating = item.rating ?: m.rating,
                        plot = item.plot ?: m.plot,
                        backdrop = item.backdrop ?: m.backdrop,
                        genre = item.genre ?: m.genre,
                        year = item.year ?: m.year,
                        adult = item.adult || m.adult,
                    )
                }
            }
        })
    }

    /** Film id in a get.php address: /movie/<user>/<pass>/<id>.<ext>. */
    private fun m3uMovieId(url: String): String? = M3U_MOVIE_ID.find(url)?.groupValues?.get(1)

    /**
     * [showEarly]: nothing is on screen yet, so an M3U shows its channels as soon as it is read,
     * before the ratings and guide ids of its server arrive.
     */
    private suspend fun load(s: Source, showEarly: Boolean): Loaded = when (s.type) {
        SourceType.M3U -> {
            val raw = Net.open(s.url).use { M3u.parse(s.id, it) }
            if (showEarly && xtreamBehindM3u(s.url) != null) {
                withContext(Dispatchers.Main) { allItems = raw.items; publish() }
            }
            withRatings(s, raw)
        }
        SourceType.XTREAM -> Xtream(s).load()
    }

    /** Adds the one list of the app; refused while a list is already there. */
    fun addSource(source: Source): Boolean {
        if (_sources.value.isNotEmpty()) return false
        val withId = source.copy(id = UUID.randomUUID().toString().take(8), expiresAt = null)
        _sources.value = listOf(withId)
        store.saveSources(_sources.value)
        refresh()
        return true
    }

    /** Deletes the list and everything that came from it, so the next one starts clean. */
    fun removeSource(id: String) {
        refreshJob?.cancel()
        _sources.value = _sources.value.filterNot { it.id == id }
        store.saveSources(_sources.value)
        store.purgeSourceData(id)
        cache.clear(id)
        allItems = emptyList()
        publish()
        _epg.value = emptyMap()
        _details.value = emptyMap()
        detailsRequested.clear()
        _multiview.value = List(4) { null }
        // The profiles belong to the list: they go with it, back to the single default one.
        store.resetProfiles()
        _profiles.value = store.profiles()
        _profileId.value = store.currentProfileId()
        _favorites.value = store.favorites()
        _hidden.value = store.hiddenSections()
        _teams.value = store.teams()
        lockAdult()
        _message.value = null
        _loading.value = false
        reloadProgress()
    }

    fun toggleFavorite(key: String) {
        val now = _favorites.value
        _favorites.value = if (key in now) now - key else now + key
        store.saveFavorites(_favorites.value)
    }

    fun reloadProgress() {
        _progress.value = store.progress()
        _recentLive.value = store.recentLive()
    }

    // ---------- Profiles ----------

    private val _profiles = MutableStateFlow(store.profiles())
    val profiles: StateFlow<List<Profile>> = _profiles

    private val _profileId = MutableStateFlow(store.currentProfileId())
    val profileId: StateFlow<String> = _profileId

    fun currentProfile(): Profile =
        _profiles.value.firstOrNull { it.id == _profileId.value } ?: _profiles.value.first()

    fun selectProfile(id: String) {
        store.setCurrentProfile(id)
        _profileId.value = id
        _favorites.value = store.favorites()
        _hidden.value = store.hiddenSections()
        _teams.value = store.teams()
        reloadProgress()
        lockAdult()
    }

    /** Channel keys in the four multiview slots (null = empty); kept while the app runs. */
    private val _multiview = MutableStateFlow<List<String?>>(List(4) { null })
    val multiview: StateFlow<List<String?>> = _multiview

    fun setSlot(index: Int, key: String?) {
        _multiview.value = _multiview.value.toMutableList().also { it[index] = key }
    }

    private val _teams = MutableStateFlow(store.teams())
    val teams: StateFlow<Set<String>> = _teams

    fun toggleTeam(name: String) {
        val t = name.trim()
        if (t.isEmpty()) return
        val now = _teams.value
        _teams.value = if (t in now) now - t else now + t
        store.saveTeams(_teams.value)
    }

    fun saveProfile(profile: Profile) {
        val list = _profiles.value
        val updated = if (list.any { it.id == profile.id }) {
            list.map { if (it.id == profile.id) profile else it }
        } else {
            list + profile.copy(id = "p" + UUID.randomUUID().toString().take(6))
        }
        _profiles.value = updated
        store.saveProfiles(updated)
    }

    fun deleteProfile(id: String) {
        val rest = _profiles.value.filterNot { it.id == id }
        if (rest.isEmpty()) return
        _profiles.value = rest
        store.saveProfiles(rest)
        store.clearProfileData(id)
        if (_profileId.value == id) selectProfile(rest.first().id)
    }

    fun setHidden(set: Set<String>) {
        _hidden.value = set
        store.saveHiddenSections(set)
    }

    /**
     * Fetches banner details (synopsis, picture, duration) for films of an Xtream source, or of an
     * M3U that comes from an Xtream server; series already carry theirs.
     */
    fun loadDetails(items: List<Item>) {
        val wanted = items.filter { it.kind == Kind.MOVIE && detailsRequested.add(it.key) }
        if (wanted.isEmpty()) return
        // All at once: each banner fills in as soon as its own answer arrives.
        wanted.forEach { item ->
            val source = _sources.value.firstOrNull { it.id == item.sourceId } ?: return@forEach
            val (server, vodId) = when (source.type) {
                SourceType.XTREAM -> Xtream(source) to item.id
                SourceType.M3U -> (xtreamBehindM3u(source.url) ?: return@forEach) to (m3uMovieId(item.url) ?: return@forEach)
            }
            viewModelScope.launch {
                val d = withContext(Dispatchers.IO) {
                    runCatching { server.movieDetails(vodId) }.getOrNull()
                }
                if (d != null) {
                    _details.value = _details.value + (item.key to d)
                    saveDetailsSoon()
                }
            }
        }
    }

    private var detailsSave: Job? = null

    /** Answers arrive one by one: the file is written once they stop for a moment. */
    private fun saveDetailsSoon() {
        detailsSave?.cancel()
        detailsSave = viewModelScope.launch {
            delay(1_500)
            val snapshot = _details.value
            withContext(Dispatchers.IO) { cache.saveDetails(snapshot) }
        }
    }

    /** Title for the live TV menu: the playlist name when there is only one. */
    fun liveTitle(): String = _sources.value.singleOrNull()?.name ?: "TV em Direto"

    fun item(key: String): Item? = visibleByKey[key]

    fun isAdult(item: Item): Boolean = Adult.isAdult(item)

    /** Any item of the list, adult ones included even while locked (to know what a slot holds). */
    fun anyItem(key: String): Item? = allByKey[key]

    suspend fun episodes(item: Item): List<Episode> {
        if (item.episodes.isNotEmpty()) return item.episodes
        val source = _sources.value.firstOrNull { it.id == item.sourceId } ?: return emptyList()
        if (source.type != SourceType.XTREAM) return emptyList()
        return withContext(Dispatchers.IO) { Xtream(source).episodes(item.id) }
    }

    fun nowNext(item: Item, now: Long): Pair<Programme?, Programme?> {
        val list = item.epgId?.lowercase()?.let { _epg.value[it] } ?: return null to null
        val current = list.firstOrNull { it.start <= now && it.stop > now }
        val next = list.firstOrNull { it.start >= (current?.stop ?: now) }
        return current to next
    }
}

/** A saved list younger than this is used as is when the app opens. */
private const val LIST_MAX_AGE = 12 * 60 * 60_000L

/** The guide keeps 24 h ahead, so it is fetched again after this. */
private const val EPG_MAX_AGE = 8 * 60 * 60_000L

private val M3U_MOVIE_ID = Regex("""/movie/[^/]+/[^/]+/(\d+)""")
