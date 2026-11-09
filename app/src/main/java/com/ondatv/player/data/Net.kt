package com.ondatv.player.data

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Net {
    const val USER_AGENT = "OndaTV/0.1 (Android)"

    /**
     * Some IPTV servers refuse unknown players and answer with a web page instead of the
     * list. These are tried in turn; the one that works is kept for the rest of the session
     * (the video player uses it too).
     */
    private val AGENTS = listOf(
        USER_AGENT,
        "IPTVSmartersPro",
        "VLC/3.0.20 LibVLC/3.0.20",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
    )

    @Volatile
    var userAgent: String = USER_AGENT
        private set

    private val refresh = Regex("""(?i)http-equiv=["']?refresh["']?[^>]*content=["']?\s*\d+\s*;\s*url=([^"'>\s]+)""")

    init {
        // Challenge pages often set a cookie and send the client back to the same address.
        if (CookieHandler.getDefault() == null) CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
    }

    /** Opens a URL, following redirects across http/https, and un-gzips when needed. */
    fun open(address: String): InputStream {
        var error: IOException? = null
        val tries = listOf(userAgent) + AGENTS.filter { it != userAgent }
        for (agent in tries) {
            try {
                var s = fetch(URL(address), agent)
                // A web page with a <meta refresh> (simple anti-bot check): follow it once.
                if (looksLikeHtml(s)) {
                    val page = peek(s, 4096)
                    s.close()
                    val target = refresh.find(page)?.groupValues?.get(1)?.replace("&amp;", "&")
                    if (target == null) throw HtmlAnswer()
                    s = fetch(URL(URL(address), target), agent)
                    if (looksLikeHtml(s)) { s.close(); throw HtmlAnswer() }
                }
                userAgent = agent
                return s
            } catch (e: HtmlAnswer) {
                error = e
            } catch (e: HttpRefused) {
                // 4xx: the server may be refusing this player; try the next name.
                error = e
            }
        }
        if (error is HtmlAnswer) {
            throw IOException(
                "O servidor respondeu com uma página da web em vez da lista. " +
                    "Confira o endereço do servidor, o usuário e a senha, ou tente mais tarde.",
            )
        }
        throw error ?: IOException("Falha de conexão")
    }

    private class HtmlAnswer : IOException("html")
    private class HttpRefused(code: Int) : IOException("O servidor respondeu HTTP $code")

    private fun fetch(start: URL, agent: String): BufferedInputStream {
        var url = start
        repeat(6) {
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", agent)
            c.setRequestProperty("Accept", "*/*")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrEmpty()) throw IOException("Redirecionamento sem destino")
                url = URL(url, location)
                return@repeat
            }
            if (code !in 200..299) {
                c.disconnect()
                if (code in 400..499) throw HttpRefused(code)
                throw IOException("O servidor respondeu HTTP $code")
            }
            return BufferedInputStream(gunzipIfNeeded(BufferedInputStream(c.inputStream)), 64 * 1024)
        }
        throw IOException("Redirecionamentos demais")
    }

    /** The first bytes of the answer, without consuming them. */
    private fun peek(s: BufferedInputStream, max: Int): String {
        s.mark(max + 1)
        val buf = ByteArray(max)
        var n = 0
        while (n < max) {
            val r = s.read(buf, n, max - n)
            if (r <= 0) break
            n += r
        }
        s.reset()
        return String(buf, 0, n, Charsets.UTF_8)
    }

    private fun looksLikeHtml(s: BufferedInputStream): Boolean {
        val head = peek(s, 512).trimStart('﻿', ' ', '\n', '\r', '\t').lowercase()
        return head.startsWith("<html") || head.startsWith("<!doctype html") || head.startsWith("<head")
    }

    fun text(address: String): String = open(address).bufferedReader().use { it.readText() }

    private fun gunzipIfNeeded(s: BufferedInputStream): InputStream {
        s.mark(2)
        val b1 = s.read()
        val b2 = s.read()
        s.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(s) else s
    }
}
