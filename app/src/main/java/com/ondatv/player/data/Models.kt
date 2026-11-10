package com.ondatv.player.data

enum class SourceType { M3U, XTREAM }

enum class Kind { LIVE, MOVIE, SERIES }

data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String = "",
    val epgUrl: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    // Expiry date the server reports for this account (Unix seconds); null when it gives none.
    val expiresAt: Long? = null,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    // Picture and synopsis of this episode, when the list or the server gives them.
    val image: String? = null,
    val plot: String? = null,
)

/**
 * One entry of the panel that is part of a series: panels often send each season as its own
 * series, with its own id. [season] is the season its name points to ("Elite T2" -> 2).
 */
data class SeriesPart(val id: String, val season: Int?)

data class Item(
    val sourceId: String,
    val kind: Kind,
    val id: String,
    val name: String,
    val group: String,
    val logo: String?,
    val url: String,
    val epgId: String? = null,
    // Filled for M3U series; Xtream series load their episodes on demand.
    val episodes: List<Episode> = emptyList(),
    val rating: Float? = null,
    // Unix seconds from Xtream; position in the file for M3U (higher = later).
    val added: Long = 0,
    val backdrop: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val year: String? = null,
    // Marked adult by the server (Xtream is_adult).
    val adult: Boolean = false,
    // Xtream series joined from several panel entries (one per season); empty for a single entry.
    val parts: List<SeriesPart> = emptyList(),
) {
    // Built once: lists of tens of thousands of items look it up constantly.
    val key: String = "$sourceId:${kind.name}:$id"
}

/** Extra information shown in the large banner at the top of a screen. */
data class Details(
    val backdrop: String?,
    val plot: String?,
    val genre: String?,
    val year: String?,
    val duration: String?,
    val age: String?,
    val rating: Float?,
)

data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String,
)

data class Progress(
    val key: String,
    val title: String,
    val logo: String?,
    val url: String,
    val position: Long,
    val duration: Long,
    val updated: Long,
)

const val DEFAULT_PROFILE = "p0"

/** Someone in the house: own favourites, history and Continue Watching; optional 4-digit PIN. */
data class Profile(
    val id: String,
    val name: String,
    val color: Int,
    val pin: String?,
    // Picture chosen from the bundled avatars (1..AVATAR_COUNT); 0 = the coloured initial.
    val avatar: Int = 0,
)

/** Result of loading one source: its items plus any EPG address it announced. */
data class Loaded(val items: List<Item>, val epgUrl: String?)
