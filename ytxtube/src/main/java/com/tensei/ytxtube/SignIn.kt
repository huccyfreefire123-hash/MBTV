package com.tensei.ytxtube

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&hl=pt-BR&prompt=select_account" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26hl%3Dpt-BR%26next%3D%252F"






@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(c.surfaceContainerHigh)
                    .pressable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Cancelar", tint = c.onSurface)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Entrar com o Google",
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = c.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Suas credenciais nunca são guardadas • Login direto e criptografado",
                        color = c.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val cookies = CookieManager.getInstance()
                    cookies.removeAllCookies(null)
                    cookies.removeSessionCookies(null)
                    cookies.flush()
                    runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }

                    WebView(context).apply {
                        clearCache(true)
                        clearFormData()
                        clearHistory()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "")

                        fun checkSignIn(view: WebView, url: String) {
                            if (finishing) return
                            val c1 = cookies.getCookie("https://www.youtube.com").orEmpty()
                            val c2 = cookies.getCookie("https://m.youtube.com").orEmpty()
                            val c3 = cookies.getCookie("https://youtube.com").orEmpty()
                            val c4 = cookies.getCookie("https://accounts.google.com").orEmpty()
                            val c5 = if (url.isNotEmpty()) cookies.getCookie(url).orEmpty() else ""
                            val header = listOf(c1, c2, c3, c4, c5).filter { it.isNotBlank() }.joinToString("; ")
                            if (!Account.looksSignedIn(header)) return
                            if (!url.contains("youtube.com") && !header.contains("LOGIN_INFO") && !header.contains("SAPISID")) return
                            finishing = true
                            view.evaluateJavascript(
                                """(function(){ try { var m = document.body.innerText.match(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/); return m ? m[0] : ''; } catch(e){ return ''; } })()"""
                            ) { jsEmail ->
                                val cleanEmail = jsEmail?.replace("\"", "")?.trim()?.takeIf { it.contains("@") && it.length > 5 }
                                scope.launch {
                                    runCatching { Account.completeSignIn(header, cleanEmail) }
                                        .onSuccess {
                                            cookies.flush()
                                            onDone()
                                        }
                                        .onFailure {
                                            finishing = false
                                            error = "Você entrou, mas o YouTube não respondeu. Verifique sua conexão e tente de novo."
                                        }
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                checkSignIn(view, url)
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                checkSignIn(view, url)
                            }
                        }
                        loadUrl(SIGN_IN_URL)
                    }
                },
            )
            if (finishing || error != null) {
                Column(
                    Modifier.fillMaxSize().background(c.surface).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    if (error == null) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Finalizando login…", fontWeight = FontWeight.Bold)
                    } else {
                        Text(error.orEmpty(), textAlign = TextAlign.Center, color = c.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        PillButton("Fechar", onCancel)
                    }
                }
            }
        }
    }
}


@Composable
fun HeaderActions(onSearch: () -> Unit, onAccount: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleAction(Icons.Rounded.Search, "Pesquisar", onSearch)
        AccountAction(onAccount)
    }
}


@Composable
fun AccountAction(onClick: () -> Unit) {
    val profile = Account.profile
    val c = MaterialTheme.colorScheme
    val backdrop = LocalGlassBackdrop.current
    val glass = if (backdrop != null) {
        Modifier.liquidGlass(
            backdrop,
            glassSpec(
                shape = CircleShape,
                tint = c.surfaceContainerHighest.copy(alpha = 0.46f),
                blur = 20.dp,
                refractionHeight = 12.dp,
                refractionAmount = 18.dp,
            ),
        )
    } else {
        Modifier.background(c.surfaceContainerHigh)
    }
    if (profile == null) {
        Box(
            Modifier.size(42.dp).pressable(onClick = onClick).clip(CircleShape).then(glass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AccountCircle, "Entrar", tint = c.onSurface, modifier = Modifier.size(23.dp))
        }
    } else {
        Box(
            Modifier.size(46.dp).pressable(onClick = onClick).clip(CircleShape).then(glass).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChannelAvatar(profile.avatar, profile.name, size = 38)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(onSignIn: () -> Unit, onSignedOut: () -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val profile = Account.profile
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (profile == null) {
                Text("Seu YouTube, sem anúncios", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Entre para ver suas recomendações, inscrições, histórico e playlists. " +
                        "Curtidas, inscrições, Assistir mais tarde e histórico são sincronizados com sua conta.\n\n" +
                        "O Google considera apps não oficiais contra os termos e pode suspender contas que os usam. " +
                        "Use uma conta secundária do Google para não arriscar a principal.",
                    color = c.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PillButton("Entrar com o Google", { onDismiss(); onSignIn() })
            } else {
                ChannelAvatar(profile.avatar, profile.name, size = 72)
                Spacer(Modifier.height(12.dp))
                Text(profile.name, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                profile.handle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 14.sp) }
                Spacer(Modifier.height(20.dp))
                PillButton("Sair", { Account.signOut(); onSignedOut(); onDismiss() }, Icons.AutoMirrored.Rounded.Logout)
            }

            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainer).padding(18.dp)) {
                AppearanceSettings()
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainer).padding(18.dp)) {
                SponsorBlockSettings()
            }
        }
    }
}
