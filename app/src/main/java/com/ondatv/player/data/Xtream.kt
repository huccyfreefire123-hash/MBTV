package com.ondatv.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Client for the Xtream Codes "player_api.php" interface. */
class Xtream(private val source: Source) {
    private val base = source.server.trim().trimEnd('/')
        .let { if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it" }
    private val user = source.username.trim()
    private val pass = source.password.trim()

    val epgUrl: String get() = "$base/xmltv.php?username=${enc(user)}&password=${enc(pass)}"

    /** The whole catalogue: the three lists download at the same time, each read as it streams in. */
    suspend fun load(): Loaded = coroutineScope {
        checkLogin()
        val liveCats = async(Dispatchers.IO) { categories("get_live_categories") }
        val vodCats = async(Dispatchers.IO) { categories("get_vod_categories") }
        val seriesCats = async(Dispatchers.IO) { categories("get_series_categories") }

        val live = async(Dispatchers.IO) {
            val out = ArrayList<Item>()
            val cats = liveCats.await()
            scan("get_live_streams") { f ->
                val id = f["stream_id"].orEmpty()
                out += Item(
                    source.id, Kind.LIVE, id, f["name"].orEmpty(),
                    cats[f["category_id"]] ?: "Sem categoria",
                    f["stream_icon"]?.ifEmpty { null },
                    "$base/live/$user/$pass/$id.ts",
                    epgId = f["epg_channel_id"]?.ifEmpty { null },
                    added = f["added"]?.toLongOrNull() ?: 0,
                    adult = f["is_adult"] == "1" || f["is_adult"] == "true",
                )
            }
            out
        }
        val movies = async(Dispatchers.IO) {
            val out = ArrayList<Item>()
            val cats = vodCats.await()
            scan("get_vod_streams") { f ->
                val id = f["stream_id"].orEmpty()
                val ext = f["container_extension"]?.ifEmpty { null } ?: "mp4"
                out += Item(
                    source.id, Kind.MOVIE, id, f["name"].orEmpty(),
                    cats[f["category_id"]] ?: "Sem categoria",
                    f["stream_icon"]?.ifEmpty { null },
                    "$base/movie/$user/$pass/$id.$ext",
                    rating = f.rating(),
                    added = f["added"]?.toLongOrNull() ?: 0,
                    year = f.releaseYear(),
                    adult = f.isAdultFlag(),
                )
            }
            out
        }
        val series = async(Dispatchers.IO) {
            val out = ArrayList<Item>()
            val tmdb = HashMap<String, String>()
            val cats = seriesCats.await()
            scan("get_series") { f ->
                f.tmdbId()?.let { tmdb[f["series_id"].orEmpty()] = it }
                out += Item(
                    source.id, Kind.SERIES, f["series_id"].orEmpty(), f["name"].orEmpty(),
                    cats[f["category_id"]] ?: "Sem categoria",
                    f["cover"]?.ifEmpty { null },
                    "",
                    rating = f.rating(),
                    added = f["last_modified"]?.toLongOrNull() ?: 0,
                    backdrop = f["backdrop_path"]?.ifBlank { null },
                    plot = f["plot"]?.ifBlank { null },
                    genre = f["genre"]?.ifBlank { null },
                    year = f.releaseYear(),
                    adult = f.isAdultFlag(),
                )
            }
            // One series per show, even when the panel sends each season as its own entry.
            SeriesGroups.joinXtream(out, tmdb)
        }
        Loaded(live.await() + movies.await() + series.await(), epgUrl)
    }

    /** Banner information for one film (get_vod_info). */
    fun movieDetails(vodId: String): Details? {
        val info = JSONObject(Net.text(api("get_vod_info", "&vod_id=${enc(vodId)}"))).optJSONObject("info")
            ?: return null
        val minutes = info.optString("duration_secs").toLongOrNull()?.let { it / 60 }
            ?: info.optString("duration").split(':').takeIf { it.size == 3 }
                ?.let { (h, m, _) -> (h.toLongOrNull() ?: 0) * 60 + (m.toLongOrNull() ?: 0) }
            ?: info.optString("episode_run_time").toLongOrNull()
        return Details(
            backdrop = info.firstImage("backdrop_path") ?: info.optString("movie_image").ifBlank { null },
            plot = info.optString("plot").ifBlank { info.optString("description") }.ifBlank { null },
            genre = info.optString("genre").ifBlank { null },
            year = info.optString("releasedate").ifBlank { info.optString("release_date") }.take(4).ifBlank { null },
            duration = minutes?.takeIf { it > 0 }?.let { "$it min" },
            age = info.optString("mpaa_rating").ifBlank { info.optString("age") }.ifBlank { null },
            rating = info.rating(),
        )
    }

    /**
     * Rating on a 0-10 scale as panels send it: "7.5", "7,5", 7.5, "7.5/10", or only
     * rating_5based (doubled). Null when there is none.
     */
    private fun JSONObject.rating(): Float? {
        fun parse(v: String) = Regex("""\d+(?:[.,]\d+)?""").find(v)?.value?.replace(',', '.')?.toFloatOrNull()
        val ten = parse(optString("rating"))?.takeIf { it > 0f }
        if (ten != null) return ten.coerceAtMost(10f)
        return parse(optString("rating_5based"))?.takeIf { it > 0f }?.let { (it * 2).coerceAtMost(10f) }
    }

    /**
     * Ratings of films (by stream id) and series (by name) from this server, used to fill in
     * a get.php M3U list, which carries none.
     */
    /** Rating, release year and adult flag of each film, by stream id, to fill in a get.php M3U. */
    fun movieMeta(): Map<String, MovieMeta> {
        val movies = HashMap<String, MovieMeta>()
        scan("get_vod_streams") { f ->
            val id = f["stream_id"] ?: return@scan
            movies[id] = MovieMeta(f.rating(), f.releaseYear(), f.isAdultFlag())
        }
        return movies
    }

    data class MovieMeta(val rating: Float?, val year: String?, val adult: Boolean)

    fun seriesMeta(): Map<String, SeriesMeta> {
        val series = HashMap<String, SeriesMeta>()
        scan("get_series") { f ->
            val name = f["name"] ?: return@scan
            // By show, as the M3U joins its seasons: the first entry (usually season 1) gives the details.
            series.putIfAbsent(
                nameKey(SeriesGroups.split(name).first),
                SeriesMeta(
                    rating = f.rating(),
                    plot = f["plot"]?.ifBlank { null },
                    backdrop = f["backdrop_path"]?.ifBlank { null },
                    genre = f["genre"]?.ifBlank { null },
                    year = f.releaseYear(),
                    adult = f.isAdultFlag(),
                ),
            )
        }
        return series
    }

    /** What the panel says about a series, to fill in the same series of a get.php M3U. */
    data class SeriesMeta(
        val rating: Float?, val plot: String?, val backdrop: String?, val genre: String?, val year: String?,
        val adult: Boolean = false,
    )

    /** Release year as the panel sends it (year, releaseDate or release_date); null when it has none. */
    private fun Map<String, String>.releaseYear(): String? =
        listOf("year", "releaseDate", "release_date", "releasedate")
            .firstNotNullOfOrNull { k -> get(k)?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) } }

    private fun Map<String, String>.isAdultFlag(): Boolean = get("is_adult").let { it == "1" || it == "true" }

    /** The TMDB id some panels send with each series ("tmdb" or "tmdb_id"); null when there is none. */
    private fun Map<String, String>.tmdbId(): String? =
        (get("tmdb") ?: get("tmdb_id"))?.trim()?.takeIf { it.isNotEmpty() && it != "0" && it != "null" }

    /** Guide channel id of each live channel, by stream id (a plain M3U has no tvg-id). */
    fun liveGuideIds(): Map<String, String> {
        val out = HashMap<String, String>()
        scan("get_live_streams") { f ->
            val id = f["stream_id"]
            val epg = f["epg_channel_id"]?.trim()
            if (id != null && !epg.isNullOrEmpty()) out[id] = epg
        }
        return out
    }

    /**
     * Reads a big list (real panels send tens of MB) one entry at a time, keeping only the
     * plain fields, so it never has to hold the whole answer in memory.
     */
    private fun scan(action: String, block: (Map<String, String>) -> Unit) {
        android.util.JsonReader(Net.open(api(action)).bufferedReader()).use { r ->
            r.isLenient = true
            if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) return
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                val f = HashMap<String, String>()
                r.beginObject()
                while (r.hasNext()) {
                    val key = r.nextName()
                    when (r.peek()) {
                        android.util.JsonToken.STRING, android.util.JsonToken.NUMBER -> f[key] = r.nextString()
                        android.util.JsonToken.BOOLEAN -> f[key] = r.nextBoolean().toString()
                        // backdrop_path and similar come as arrays: the first text is enough.
                        android.util.JsonToken.BEGIN_ARRAY -> {
                            r.beginArray()
                            if (r.hasNext() && r.peek() == android.util.JsonToken.STRING) f[key] = r.nextString()
                            while (r.hasNext()) r.skipValue()
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                block(f)
            }
            r.endArray()
        }
    }

    private fun Map<String, String>.rating(): Float? {
        fun parse(v: String?) = v?.let { Regex("""\d+(?:[.,]\d+)?""").find(it)?.value?.replace(',', '.')?.toFloatOrNull() }
        val ten = parse(get("rating"))?.takeIf { it > 0f }
        if (ten != null) return ten.coerceAtMost(10f)
        return parse(get("rating_5based"))?.takeIf { it > 0f }?.let { (it * 2).coerceAtMost(10f) }
    }

    private fun JSONObject.firstImage(key: String): String? = when (val v = opt(key)) {
        is JSONArray -> v.optString(0)
        is String -> v
        else -> null
    }?.ifBlank { null }

    fun episodes(seriesId: String): List<Episode> {
        val info = JSONObject(Net.text(api("get_series_info", "&series_id=${enc(seriesId)}")))
        val bySeason = info.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        bySeason.keys().forEach { seasonKey ->
            bySeason.optJSONArray(seasonKey)?.forEachObject { e ->
                val id = e.optString("id")
                val ext = e.optString("container_extension").ifEmpty { "mp4" }
                // "info" is an object with the episode's picture and synopsis (an empty array when there are none).
                val info = e.optJSONObject("info")
                out += Episode(
                    id = id,
                    title = e.optString("title"),
                    season = e.optString("season").toIntOrNull() ?: seasonKey.toIntOrNull() ?: 1,
                    number = e.optString("episode_num").toIntOrNull() ?: 0,
                    url = "$base/series/$user/$pass/$id.$ext",
                    image = info?.optString("movie_image")?.ifBlank { null } ?: info?.firstImage("backdrop_path"),
                    plot = info?.optString("plot")?.ifBlank { null },
                )
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }

    /**
     * The account's expiry as the server reports it (user_info.exp_date, Unix seconds).
     * Null when the server sends none, which providers use for accounts without an end date.
     */
    fun expiry(): Long? {
        val info = JSONObject(Net.text(api(null))).optJSONObject("user_info")
            ?: throw IOException("Resposta inesperada do servidor Xtream")
        return when (val raw = info.opt("exp_date")) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
            else -> null
        }?.takeIf { it > 0 }
    }

    private fun checkLogin() {
        val info = JSONObject(Net.text(api(null))).optJSONObject("user_info")
            ?: throw IOException("Resposta inesperada do servidor Xtream")
        if (info.optInt("auth", 0) != 1) throw IOException("Usuario ou senha recusados")
    }

    private fun categories(action: String): Map<String, String> {
        val map = HashMap<String, String>()
        array(action).forEachObject { map[it.optString("category_id")] = it.optString("category_name") }
        return map
    }

    private fun array(action: String): JSONArray {
        val text = Net.text(api(action)).trim()
        return if (text.startsWith("[")) JSONArray(text) else JSONArray()
    }

    private fun api(action: String?, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}$a$extra"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/**
 * An M3U address of the form http://host:port/get.php?username=..&password=.. comes from an
 * Xtream server, which can be asked for the account's expiry. Null for any other address.
 */
fun xtreamBehindM3u(url: String): Xtream? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    if (uri.path?.endsWith("get.php") != true) return null
    val params = (uri.rawQuery ?: return null).split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }.toMap()
    val user = params["username"] ?: return null
    val pass = params["password"] ?: return null
    val port = if (uri.port > 0) ":${uri.port}" else ""
    return Xtream(Source("", "", SourceType.XTREAM, server = "${uri.scheme}://${uri.host}$port", username = user, password = pass))
}

/**
 * Key to match a series of a get.php list with the same series in the panel: the M3U
 * names often differ only by a year, a "[L]" tag or punctuation, e.g. "Show (2008)".
 */
fun nameKey(name: String): String =
    Sports.norm(name).replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
        .replace(Regex("""\b(19|20)\d{2}\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(block)
}
