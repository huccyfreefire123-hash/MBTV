package com.ondatv.player.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Fill in the list on a phone instead of typing it with the TV remote. The TV shows a QR code with
 * a link to a small form that this class serves from the TV itself, on the home network. The
 * phone opens the link with its camera, fills the form and sends it back.
 *
 * Nothing leaves the house, and the link is protected: it carries a random code that is in the QR
 * only, it works for one submission, and it stops by itself after [LIFETIME_MS] or after too many
 * wrong guesses. The connection is plain http inside the home network, so it must not stay open
 * longer than needed: [stop] is called as soon as the screen closes.
 *
 * @param type fixed type of the list (M3U or Xtream), or null to let the phone choose
 * @param prefill list being edited: its name, addresses and user are in the form (never the password)
 */
class PhoneLink(
    private val context: Context,
    private val type: SourceType?,
    private val prefill: Source?,
    private val onReceived: (Source) -> Unit,
    private val onExpired: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val token = newToken()

    @Volatile private var server: ServerSocket? = null
    @Volatile private var stopped = false
    private var wrongGuesses = 0

    /** The link for the QR code, or null when the TV has no network address or no free port. */
    fun start(): String? {
        val ip = localAddress(context) ?: return null
        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = socket
        thread(isDaemon = true, name = "phone-link") { serve(socket) }
        return "http://${ip.hostAddress}:${socket.localPort}/$token"
    }

    fun stop() {
        stopped = true
        runCatching { server?.close() }
    }

    private fun serve(socket: ServerSocket) {
        val deadline = System.currentTimeMillis() + LIFETIME_MS
        runCatching { socket.soTimeout = 1000 }
        while (!stopped) {
            if (System.currentTimeMillis() > deadline) return expire()
            val client = try {
                socket.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                return
            }
            thread(isDaemon = true, name = "phone-link-client") {
                runCatching { handle(client) }
                runCatching { client.close() }
            }
        }
    }

    private fun expire() {
        stop()
        main.post { onExpired() }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5000
        val request = readRequest(client.getInputStream()) ?: return reply(client, 400, page("Pedido inválido."))
        val path = request.path.substringBefore('?')
        if (path != "/$token") {
            // Anyone on the network can knock; only the code opens the door.
            val locked = synchronized(this) { ++wrongGuesses >= MAX_WRONG_GUESSES }
            reply(client, 404, page("Link inválido ou expirado. Gere um novo código na TV."))
            if (locked) expire()
            return
        }
        when (request.method) {
            "GET" -> reply(client, 200, form(null, prefillValues()))
            "POST" -> {
                val values = parseForm(request.body)
                val (source, error) = build(values)
                if (source == null) {
                    reply(client, 200, form(error, values))
                    return
                }
                // Answers the phone first, then hands the list to the app (one submission only).
                reply(client, 200, page("Pronto! A lista foi enviada para a TV. Pode olhar a tela dela."))
                stop()
                main.post { onReceived(source) }
            }
            else -> reply(client, 405, page("Pedido não aceito."))
        }
    }

    // ---------- The list from the form ----------

    private fun prefillValues(): Map<String, String> = prefill?.let {
        mapOf("name" to it.name, "url" to it.url, "epg" to it.epgUrl, "server" to it.server, "user" to it.username)
    } ?: emptyMap()

    private fun build(f: Map<String, String>): Pair<Source?, String?> {
        fun field(name: String) = f[name].orEmpty().trim()
        val kind = type ?: if (field("type") == "xtream") SourceType.XTREAM else SourceType.M3U
        val name = field("name").take(60)
        val id = prefill?.id.orEmpty()
        if (kind == SourceType.M3U) {
            val url = field("url")
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                return null to "O endereço da lista precisa começar com http:// ou https://"
            }
            return Source(id = id, name = name.ifEmpty { "Minha lista" }, type = kind, url = url, epgUrl = field("epg")) to null
        }
        val server = field("server")
        val user = field("user")
        if (server.isEmpty() || user.isEmpty()) return null to "Informe o servidor e o usuário."
        // Left empty while editing, the password stays as it was.
        val pass = f["pass"].orEmpty().ifEmpty { prefill?.password.orEmpty() }
        val fallback = server.substringAfter("://").substringBefore('/')
        return Source(id = id, name = name.ifEmpty { fallback }, type = kind, server = server, username = user, password = pass) to null
    }

    // ---------- HTTP, just enough for one form ----------

    private class Request(val method: String, val path: String, val body: String)

    private fun readRequest(input: InputStream): Request? {
        val head = ByteArrayOutputStream()
        var state = 0 // how many bytes of "\r\n\r\n" have been seen in a row
        while (head.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            state = when {
                b == '\r'.code && (state == 0 || state == 2) -> state + 1
                b == '\n'.code && (state == 1 || state == 3) -> state + 1
                else -> 0
            }
            if (state == 4) break
        }
        if (state != 4) return null
        val lines = head.toString(Charsets.ISO_8859_1.name()).split("\r\n")
        val first = lines.firstOrNull()?.split(' ') ?: return null
        if (first.size < 2) return null
        val length = lines.firstNotNullOfOrNull { l ->
            l.takeIf { it.startsWith("content-length:", ignoreCase = true) }?.substringAfter(':')?.trim()?.toIntOrNull()
        } ?: 0
        if (length !in 0..MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return Request(first[0].uppercase(), first[1], String(body, Charsets.UTF_8))
    }

    private fun parseForm(body: String): Map<String, String> = body.split('&').mapNotNull { pair ->
        val i = pair.indexOf('=')
        if (i < 0) null else URLDecoder.decode(pair.substring(0, i), "UTF-8") to URLDecoder.decode(pair.substring(i + 1), "UTF-8")
    }.toMap()

    private fun reply(client: Socket, status: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "Method Not Allowed" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "Connection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(body)
            flush()
        }
    }

    // ---------- The page the phone shows ----------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(message: String): String =
        shell("<h1>MBTV</h1><p>${esc(message)}</p>")

    private fun form(error: String?, v: Map<String, String>): String {
        fun value(name: String) = esc(v[name].orEmpty())
        val first = type ?: if (v["type"] == "xtream") SourceType.XTREAM else SourceType.M3U
        val choice = if (type == null) {
            """<div class="tabs">
               <label><input type="radio" name="type" value="m3u"${if (first == SourceType.M3U) " checked" else ""}><span>Lista M3U</span></label>
               <label><input type="radio" name="type" value="xtream"${if (first == SourceType.XTREAM) " checked" else ""}><span>Xtream Codes</span></label>
               </div>"""
        } else {
            ""
        }
        val intro = if (prefill == null) "Preencha aqui e envie: a lista aparece na TV." else "Mude o que precisar e envie: a TV atualiza a lista."
        val body = """
            <h1>MBTV</h1>
            <p>$intro</p>
            ${if (error != null) """<div class="error">${esc(error)}</div>""" else ""}
            <form method="post" accept-charset="UTF-8" autocomplete="off">
              $choice
              <label for="name">Nome (opcional)</label>
              <input id="name" name="name" type="text" value="${value("name")}" autocapitalize="sentences">
              <div id="m3u">
                <label for="url">Endereço da lista (http...)</label>
                <input id="url" name="url" type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" value="${value("url")}">
                <label for="epg">Guia EPG XMLTV (opcional)</label>
                <input id="epg" name="epg" type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" value="${value("epg")}">
              </div>
              <div id="xtream">
                <label for="server">Servidor (http://host:porta)</label>
                <input id="server" name="server" type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" value="${value("server")}">
                <label for="user">Usuário</label>
                <input id="user" name="user" type="text" autocapitalize="off" autocorrect="off" spellcheck="false" value="${value("user")}">
                <label for="pass">Senha${if (prefill != null) " (deixe vazio para manter a atual)" else ""}</label>
                <input id="pass" name="pass" type="password" autocapitalize="off" autocorrect="off">
              </div>
              <button type="submit">Enviar para a TV</button>
            </form>
            <script>
              function pick(t) {
                document.getElementById('m3u').style.display = t === 'm3u' ? 'block' : 'none';
                document.getElementById('xtream').style.display = t === 'xtream' ? 'block' : 'none';
              }
              var radios = document.querySelectorAll('input[name=type]');
              for (var i = 0; i < radios.length; i++) { radios[i].onchange = function () { pick(this.value); }; }
              pick('${if (first == SourceType.XTREAM) "xtream" else "m3u"}');
            </script>
        """.trimIndent()
        return shell(body)
    }

    private fun shell(body: String): String = """
        <!doctype html>
        <html lang="pt-BR"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>MBTV - Adicionar lista</title>
        <style>
          body { margin: 0; background: #000; color: #f5f5f5; font: 16px/1.45 system-ui, sans-serif; }
          main { max-width: 480px; margin: 0 auto; padding: 28px 20px 56px; }
          h1 { font-size: 24px; margin: 0 0 6px; }
          p { color: #9a9a9a; margin: 0 0 18px; }
          label { display: block; margin: 16px 0 6px; font-size: 14px; color: #cfcfcf; }
          input[type=text], input[type=password] { width: 100%; box-sizing: border-box; padding: 13px; border-radius: 10px; border: 1px solid #333; background: #141414; color: #fff; font-size: 16px; }
          .tabs { display: flex; gap: 8px; }
          .tabs label { flex: 1; margin: 0; padding: 0; }
          .tabs input { position: absolute; opacity: 0; }
          .tabs span { display: block; text-align: center; padding: 13px; border-radius: 10px; border: 1px solid #333; background: #141414; color: #fff; }
          .tabs input:checked + span { background: #fff; color: #000; font-weight: 600; }
          button { width: 100%; margin-top: 26px; padding: 15px; border: 0; border-radius: 26px; background: #179ef8; color: #fff; font-size: 17px; font-weight: 600; }
          .error { background: #2a1214; color: #ff8a80; padding: 12px 14px; border-radius: 10px; margin-bottom: 8px; }
        </style></head>
        <body><main>$body</main></body></html>
    """.trimIndent()

    companion object {
        /** How long the link works. */
        const val LIFETIME_MS = 5 * 60_000L
        private const val MAX_WRONG_GUESSES = 30
        private const val MAX_HEAD = 8 * 1024
        private const val MAX_BODY = 8 * 1024

        // Without look-alike characters (0/o, 1/l/i).
        private const val CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"

        private fun newToken(): String {
            val random = SecureRandom()
            return String(CharArray(8) { CODE_CHARS[random.nextInt(CODE_CHARS.length)] })
        }

        /** The address of this device in the home network (Wi-Fi or cable), or null without a network. */
        fun localAddress(context: Context): Inet4Address? {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val active = cm?.activeNetwork?.let { cm.getLinkProperties(it) }?.linkAddresses
                ?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress }
            if (active != null) return active
            return runCatching {
                NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }
            }.getOrNull()
        }
    }
}
