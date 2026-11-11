package com.ondatv.player.data

import org.json.JSONException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Why a list could not be loaded: a clear sentence for the screen, and the server's own wording for whoever asks. */
data class ListError(val text: String, val detail: String? = null)

object ListErrors {
    private val httpCode = Regex("""HTTP (\d{3})""")

    /** What a failure while downloading or reading a list means, in words the user can act on. */
    fun of(e: Throwable): ListError {
        val raw = e.message?.takeIf { it.isNotBlank() }
        val text = when {
            e is UnknownHostException || e is ConnectException || e is NoRouteToHostException ->
                "Não foi possível conectar ao servidor da lista. Confira a internet e o endereço da lista."
            e is SocketTimeoutException -> "O servidor demorou demais para responder. Tente de novo em instantes."
            e is SSLException ->
                "Falha na conexão segura com o servidor. Confira se o endereço começa com http ou https, como o seu provedor informou."
            e is JSONException ->
                "O servidor respondeu algo que o app não entendeu. Confira se o endereço é o de uma conta Xtream."
            raw != null && "recusados" in raw ->
                "O servidor recusou o usuário ou a senha. Confira os dados da lista e se a assinatura ainda vale."
            raw != null && "página da web" in raw ->
                "O servidor respondeu com uma página da web em vez da lista. Confira o endereço, o usuário e a senha."
            raw != null && "inesperada" in raw ->
                "O servidor não respondeu como uma conta Xtream. Confira o endereço do servidor (sem /player_api.php)."
            else -> httpCode.find(raw.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.let(::httpText)
                ?: "Não foi possível carregar a lista."
        }
        // The name of a system exception helps to tell cases apart ("UnknownHostException: servidor.com");
        // the app's own classes are renamed in the release build, so theirs would only be noise.
        val kind = e.javaClass.takeIf { it.name.startsWith("java") || it.name.startsWith("android") }
            ?.simpleName?.takeIf { it != "IOException" && it != "Exception" }
        return ListError(text, listOfNotNull(kind, raw).joinToString(": ").ifEmpty { null })
    }

    /** The list downloaded fine but has nothing in it. */
    val empty = ListError("A lista foi carregada, mas veio vazia. Confira o endereço e se a sua assinatura tem conteúdo.")

    private fun httpText(code: Int): String = when {
        code == 401 || code == 403 ->
            "O servidor recusou o acesso. Confira o usuário e a senha da lista e se a assinatura ainda vale."
        code == 404 -> "O servidor não encontrou a lista. O endereço pode estar errado ou a lista saiu do ar."
        code == 429 -> "O servidor limitou os acessos. Espere um pouco e tente de novo."
        code >= 500 -> "O servidor da lista está com problema agora (erro $code). Tente de novo mais tarde."
        else -> "O servidor respondeu com o erro $code. Confira o endereço da lista."
    }
}
