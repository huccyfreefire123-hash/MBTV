package com.ondatv.player.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Streams an XMLTV guide and keeps programmes from 3 h ago to 24 h ahead. */
object Epg {
    private const val HOUR = 3_600_000L

    fun parse(input: InputStream, wanted: Set<String>): Map<String, List<Programme>> {
        val now = System.currentTimeMillis()
        val from = now - 3 * HOUR
        val to = now + 24 * HOUR
        val out = HashMap<String, MutableList<Programme>>()

        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)

        var channel: String? = null
        var start = 0L
        var stop = 0L
        val title = StringBuilder()
        val desc = StringBuilder()
        var field: StringBuilder? = null

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "programme" -> {
                        channel = p.getAttributeValue(null, "channel")?.lowercase()
                        start = time(p.getAttributeValue(null, "start"))
                        stop = time(p.getAttributeValue(null, "stop"))
                        title.setLength(0)
                        desc.setLength(0)
                    }
                    "title" -> field = if (channel != null && title.isEmpty()) title else null
                    "desc" -> field = if (channel != null && desc.isEmpty()) desc else null
                    else -> field = null
                }
                XmlPullParser.TEXT -> field?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "title", "desc" -> field = null
                    "programme" -> {
                        val ch = channel
                        if (ch != null && (wanted.isEmpty() || ch in wanted) && stop > from && start < to) {
                            out.getOrPut(ch) { ArrayList() }
                                .add(Programme(start, stop, title.toString().trim(), desc.toString().trim()))
                        }
                        channel = null
                    }
                }
            }
            event = p.next()
        }
        out.values.forEach { list -> list.sortBy { it.start } }
        return out
    }

    private val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val utc = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun time(value: String?): Long {
        val s = value?.trim() ?: return 0L
        return try {
            if (s.length > 14 && s.contains(' ')) withZone.parse(s)?.time ?: 0L
            else utc.parse(s.take(14))?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
