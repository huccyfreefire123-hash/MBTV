package com.ondatv.player.data

import java.io.InputStream

object M3u {
    // "S01E02", "S01 E02", "S1:E2", "T01E02", "1x02", "Temporada 1 Episodio 2".
    private val episodePatterns = listOf(
        Regex("""(?<!\p{L})[SsTt](\d{1,2})[\s._:-]*[Ee][Pp]?[\s.]?(\d{1,3})"""),
        Regex("""(?<![\d\p{L}])(\d{1,2})[xX](\d{1,3})(?!\d)"""),
        Regex("""(?i)temporada\s*(\d{1,2})\D{0,6}epis[oó]dio\s*(\d{1,3})"""),
    )
    private val trailing = Regex("""[\s._:|-]+$""")
    private val epWord = Regex("""(?i)[\s._:|-]*(ep|epis[oó]dio|cap|cap[ií]tulo)\.?$""")

    /** Show name, season and episode of an episode title, or null when it has none. */
    private fun episodeOf(title: String): Triple<String, Int, Int>? {
        for (r in episodePatterns) {
            val m = r.find(title) ?: continue
            val show = trailing.replace(title.substring(0, m.range.first), "").trim()
            if (show.isEmpty()) continue
            return Triple(show, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        return null
    }
    private val videoExt = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm")

    fun parse(sourceId: String, input: InputStream): Loaded {
        val result = ArrayList<Item>()
        val seriesEpisodes = LinkedHashMap<String, MutableList<Episode>>()
        val seriesMeta = HashMap<String, Pair<String, String?>>() // name -> (group, logo)
        // Episodes of a /series/ address whose title has no SxxEyy: grouped by their cover.
        val unnamed = LinkedHashMap<String, MutableList<Pair<String, String>>>() // bucket -> (id, url)
        val unnamedTitles = HashMap<String, MutableList<String>>()
        val seen = HashSet<String>() // group + address, so a line repeated in a group shows once
        var epgUrl: String? = null
        var name: String? = null
        var attrs: Map<String, String> = emptyMap()
        var extGroup: String? = null
        var index = 0

        input.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#EXTM3U") -> {
                        val header = attributes(line)
                        epgUrl = (header["url-tvg"] ?: header["x-tvg-url"])
                            ?.split(',')?.firstOrNull()?.trim()?.ifEmpty { null }
                    }
                    line.startsWith("#EXTINF") -> {
                        attrs = attributes(line)
                        name = displayName(line).ifEmpty { attrs["tvg-name"] ?: "Sem nome" }
                    }
                    line.startsWith("#EXTGRP:") -> extGroup = line.removePrefix("#EXTGRP:").trim()
                    line.isEmpty() || line.startsWith("#") -> Unit
                    else -> {
                        val title = name ?: line.substringAfterLast('/')
                        val group = attrs["group-title"]?.ifEmpty { null } ?: extGroup ?: "Sem categoria"
                        val logo = attrs["tvg-logo"]?.ifEmpty { null }
                        val lower = line.lowercase().substringBefore('?')
                        val isVod = "/movie/" in lower || "/series/" in lower || videoExt.any { lower.endsWith(it) }
                        val ep = if (isVod) episodeOf(title) else null
                        val id = (index++).toString()
                        when {
                            !seen.add(group + "\u0000" + line) -> Unit
                            ep != null -> {
                                val (show, season, number) = ep
                                seriesEpisodes.getOrPut(show) { ArrayList() }
                                    .add(Episode(id, title, season, number, line))
                                seriesMeta.putIfAbsent(show, group to logo)
                            }
                            "/series/" in lower -> {
                                val bucket = group + "\u0000" + (logo ?: title)
                                unnamed.getOrPut(bucket) { ArrayList() }.add(id to line)
                                unnamedTitles.getOrPut(bucket) { ArrayList() }.add(title)
                                seriesMeta.putIfAbsent(bucket, group to logo)
                            }
                            isVod -> result += Item(
                                sourceId, Kind.MOVIE, id, title, group, logo, line, added = index.toLong(),
                            )
                            else -> result += Item(
                                sourceId, Kind.LIVE, id, title, group, logo, line,
                                epgId = attrs["tvg-id"]?.ifEmpty { null } ?: title,
                                added = index.toLong(),
                            )
                        }
                        name = null
                        attrs = emptyMap()
                        extGroup = null
                    }
                }
            }
        }

        unnamed.forEach { (bucket, list) ->
            val titles = unnamedTitles.getValue(bucket)
            val show = commonName(titles)
            val eps = list.mapIndexed { i, (id, url) -> Episode(id, titles[i], 1, i + 1, url) }
            seriesMeta[show] = seriesMeta.getValue(bucket)
            seriesEpisodes.getOrPut(show) { ArrayList() }.addAll(eps)
        }
        seriesEpisodes.forEach { (show, eps) ->
            val (group, logo) = seriesMeta[show] ?: ("Sem categoria" to null)
            result += Item(
                sourceId, Kind.SERIES, "s" + show.hashCode().toUInt(), show, group, logo, "",
                episodes = eps.sortedWith(compareBy({ it.season }, { it.number })),
                added = eps.maxOf { it.id.toLong() },
            )
        }
        return Loaded(result, epgUrl)
    }

    /** What the titles of one series share, e.g. "Show" for "Show 01", "Show 02". */
    private fun commonName(titles: List<String>): String {
        if (titles.size == 1) return titles[0]
        var prefix = titles[0]
        for (t in titles) while (!t.startsWith(prefix)) prefix = prefix.dropLast(1)
        val base = trailing.replace(prefix.trimEnd { it.isDigit() }, "")
        return trailing.replace(epWord.replace(base, ""), "").trim().ifEmpty { titles[0] }
    }

    /** key="value" pairs of an #EXTINF line; read by hand because it runs once per entry. */
    private fun attributes(line: String): Map<String, String> {
        val out = HashMap<String, String>(8)
        var from = 0
        while (true) {
            val eq = line.indexOf("=\"", from)
            if (eq < 0) break
            val close = line.indexOf('"', eq + 2)
            if (close < 0) break
            var start = eq
            while (start > 0 && line[start - 1].let { it.isLetterOrDigit() || it == '-' || it == '_' }) start--
            if (start < eq) out[line.substring(start, eq).lowercase()] = line.substring(eq + 2, close)
            from = close + 1
        }
        return out
    }

    /** The title is what follows the first comma that is outside quotes. */
    private fun displayName(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return line.substring(i + 1).trim()
            }
        }
        return ""
    }
}
