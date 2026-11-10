package com.ondatv.player.data

/**
 * Panels often send each season as a series of its own ("Elite - 1ª Temporada", "Elite T2",
 * each with its own id). This finds the show and the season a series name points to, joins the
 * entries of one show into a single series and puts their episodes together, season by season.
 */
object SeriesGroups {
    // A season named in a series title: "2ª Temporada", "Temporada 2", "Temp. 2", "Season 2",
    // "2nd Season", "T2", "S02". The ordinals are ª, º and °.
    private val markers = listOf(
        Regex("""(?i)(?<![\p{L}\p{N}])(\d{1,2})\s*[ªº°ao]?\s*temporada(?!\p{L})"""),
        Regex("""(?i)(?<![\p{L}\p{N}])temp(?:orada)?\.?\s*:?\s*(\d{1,2})(?!\p{N})"""),
        Regex("""(?i)(?<![\p{L}\p{N}])season\s*:?\s*(\d{1,2})(?!\p{N})"""),
        Regex("""(?i)(?<![\p{L}\p{N}])(\d{1,2})(?:st|nd|rd|th)\s+season(?!\p{L})"""),
        Regex("""(?<![\p{L}\p{N}])[STst](\d{1,2})(?![\p{L}\p{N}])"""),
    )
    private const val CUT_BEFORE = " -|:._([–—"
    private const val CUT_AFTER = " -|:._)]–—"
    private val notWord = Regex("""[^\p{L}\p{N}]+""")

    /** "Elite - 2ª Temporada" -> ("Elite", 2); a name that points to no season -> (name, null). */
    fun split(name: String): Pair<String, Int?> {
        val m = markers.mapNotNull { r -> r.findAll(name).lastOrNull() }.maxByOrNull { it.range.first }
            ?: return name to null
        val season = m.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return name to null
        val before = name.substring(0, m.range.first).trimEnd { it in CUT_BEFORE }
        val after = name.substring(m.range.last + 1).trimStart { it in CUT_AFTER }
        if (before.none { it.isLetterOrDigit() }) return name to null
        return (if (after.isEmpty()) before else "$before $after").trim() to season
    }

    /**
     * Shows with the same key are the same show: case, accents and punctuation do not count,
     * a year or a tag in the name does ("Doctor Who (1963)" is not "Doctor Who (2005)").
     */
    fun key(show: String, adult: Boolean): String =
        notWord.replace(Sports.norm(show), " ").trim() + if (adult) "\u0000adult" else ""

    /**
     * Xtream: the entries of one show become one series, which keeps the id, category and
     * pictures of its first season. Entries join when their names match once the season is
     * taken out and both are adult or not; different TMDB ids ([tmdb], by series id) mean
     * different shows with the same name, so those stay apart.
     */
    fun joinXtream(series: List<Item>, tmdb: Map<String, String>): List<Item> {
        val groups = LinkedHashMap<String, MutableList<Pair<Item, Int?>>>()
        for (item in series) {
            val (show, season) = split(item.name)
            groups.getOrPut(key(show, Adult.isAdult(item))) { ArrayList() } += item to season
        }
        val out = ArrayList<Item>(groups.size)
        for (entries in groups.values) {
            val byTmdb = entries.groupBy { tmdb[it.first.id] }
            if (byTmdb.keys.count { it != null } <= 1) {
                out += join(entries)
            } else {
                // Each TMDB id is its own show; an entry without one cannot be placed, so it stays alone.
                byTmdb.forEach { (id, list) -> if (id != null) out += join(list) else list.forEach { out += join(listOf(it)) } }
            }
        }
        return out
    }

    private fun join(entries: List<Pair<Item, Int?>>): Item {
        val (first, firstSeason) = entries[0]
        if (entries.size == 1 && firstSeason == null) return first
        // The entry without a season (the show itself) or the lowest season leads.
        val sorted = entries.sortedBy { it.second ?: 0 }
        val items = sorted.map { it.first }
        val lead = items[0]
        return lead.copy(
            name = split(lead.name).first,
            logo = lead.logo ?: items.firstNotNullOfOrNull { it.logo },
            rating = lead.rating ?: items.firstNotNullOfOrNull { it.rating },
            added = items.maxOf { it.added },
            backdrop = lead.backdrop ?: items.firstNotNullOfOrNull { it.backdrop },
            plot = lead.plot ?: items.firstNotNullOfOrNull { it.plot },
            genre = lead.genre ?: items.firstNotNullOfOrNull { it.genre },
            year = lead.year ?: items.firstNotNullOfOrNull { it.year },
            adult = items.any { it.adult },
            parts = sorted.map { (item, season) -> SeriesPart(item.id, season) },
        )
    }

    /**
     * The episodes of the parts of one series ([parts]: the season the part's name points to,
     * and its episodes), put together in order. A part numbered by the panel as a show of its
     * own (all in one season) gets the season its name points to. An episode an earlier part
     * already has (same season and number) is not repeated; nothing else is left out.
     */
    fun combine(parts: List<Pair<Int?, List<Episode>>>): List<Episode> {
        val out = ArrayList<Episode>()
        val earlier = HashSet<Long>()
        for ((season, eps) in parts) {
            val fixed = if (season != null && eps.map { it.season }.distinct().size <= 1) {
                eps.map { it.copy(season = season) }
            } else {
                eps
            }
            val mine = HashSet<Long>()
            for (e in fixed) {
                val k = (e.season.toLong() shl 32) or (e.number.toLong() and 0xFFFFFFFFL)
                if (e.number > 0 && k in earlier) continue
                out += e
                mine += k
            }
            earlier += mine
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }
}
