package com.ondatv.player.data

import java.text.Normalizer

/** A match found in the guide, with every channel that shows it. */
data class Match(
    val key: String,
    val home: String,
    val away: String,
    val competition: String?,
    val start: Long,
    val stop: Long,
    val channels: List<Item>,
) {
    fun isLive(now: Long) = start <= now && stop > now
}

/**
 * Finds matches in programme titles such as "Brasileirao: Time A x Time B (Ao Vivo)".
 * Only what the playlist's own guide lists can be found.
 */
object Sports {
    private val separator = Regex("""\s+(?:x|X|vs\.?|VS\.?|Vs\.?|v\.?)\s+""")
    private val brackets = Regex("""\s*[(\[][^)\]]*[)\]]\s*""")
    private val liveWords = Regex("""(?i)\b(ao vivo|em directo|en vivo|live|replay|reprise|vt)\b""")
    private val marks = Regex("""\p{Mn}+""")

    data class Parsed(val competition: String?, val home: String, val away: String)

    fun parse(title: String): Parsed? {
        var t = brackets.replace(title, " ").trim()
        var competition: String? = null
        val colon = t.lastIndexOf(':')
        if (colon in 1 until t.length - 1) {
            competition = t.substring(0, colon).trim().ifEmpty { null }
            t = t.substring(colon + 1).trim()
        }
        t = t.split(" - ", " | ").firstOrNull { separator.containsMatchIn(it) } ?: return null
        val m = separator.find(t) ?: return null
        val home = clean(t.substring(0, m.range.first))
        val away = clean(t.substring(m.range.last + 1))
        if (home.length !in 2..40 || away.length !in 2..40) return null
        if (home.split(' ').size > 5 || away.split(' ').size > 5) return null
        return Parsed(competition, home, away)
    }

    private fun clean(s: String) = liveWords.replace(s, "").trim(' ', '-', '|', ',', '.')

    fun norm(s: String): String =
        marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

    /** Matches that have not ended, earliest first; the same match on several channels is merged. */
    fun find(items: List<Item>, epg: Map<String, List<Programme>>, now: Long): List<Match> {
        val byGuide = items.asSequence()
            .filter { it.kind == Kind.LIVE && it.epgId != null }
            .groupBy { it.epgId!!.lowercase() }
        class Acc(val p: Parsed, val start: Long, val stop: Long) {
            val channels = LinkedHashMap<String, Item>()
        }
        val found = LinkedHashMap<String, Acc>()
        epg.forEach { (guideId, programmes) ->
            val channels = byGuide[guideId] ?: return@forEach
            programmes.forEach { p ->
                if (p.stop <= now) return@forEach
                val parsed = parse(p.title) ?: return@forEach
                val key = norm(parsed.home) + "|" + norm(parsed.away) + "|" + p.start / (30 * 60_000)
                val acc = found.getOrPut(key) { Acc(parsed, p.start, p.stop) }
                channels.forEach { acc.channels[it.key] = it }
            }
        }
        return found.map { (key, a) ->
            Match(key, a.p.home, a.p.away, a.p.competition, a.start, a.stop, a.channels.values.toList())
        }.sortedBy { it.start }
    }

    fun follows(m: Match, teams: Collection<String>): Boolean {
        val home = norm(m.home)
        val away = norm(m.away)
        return teams.any { t -> norm(t).let { it.isNotEmpty() && (home.contains(it) || away.contains(it)) } }
    }

    /** Team names seen in the guide, most frequent first. */
    fun teams(matches: List<Match>): List<String> =
        matches.flatMap { listOf(it.home, it.away) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
}
