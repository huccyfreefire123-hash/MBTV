package com.ondatv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ondatv.player.AppViewModel
import com.ondatv.player.data.ListError
import com.ondatv.player.data.SourceType

/** A pill button with a focus outline, for cards that hold actions (works with a touch or a remote). */
@Composable
private fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.tvFocusScale(focused, 1.05f)
            .clip(shape)
            .background(if (primary) Color.White else Onda.glass)
            .then(if (focused) Modifier.border(3.dp, Onda.accent, shape) else Modifier.border(1.dp, if (primary) Color.White else Onda.glassBorder, shape))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Why the list did not load, in plain words, with the two things that usually fix it. */
@Composable
fun ListErrorCard(error: ListError, onRetry: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Onda.card).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(22.dp))
            Text(
                "Não foi possível carregar a lista", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(error.text, color = Onda.textDim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        if (error.detail != null) {
            Text(
                "Detalhe: " + error.detail, color = Onda.textFaint, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Tentar de novo", primary = true, onClick = onRetry)
            PillButton("Editar lista", primary = false, onClick = onEdit)
        }
    }
}

/**
 * First screen of an app without a list: what to do in one sentence and the two ways to connect
 * one, each opening its form right here.
 */
@Composable
fun WelcomeAddList(vm: AppViewModel) {
    var adding by remember { mutableStateOf<SourceType?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp).padding(top = if (wide) 72.dp else 96.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Conecte a sua lista", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "O MBTV é só um player: escolha como a sua lista de canais, filmes e séries chega até ele. " +
                "Use apenas listas que você tem direito de assistir.",
            color = Onda.textDim, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp).widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(28.dp))
        val options = listOf(
            Triple(SourceType.M3U, "Lista M3U" to Icons.Filled.Link, "Cole o endereço (link) da lista, o que termina em .m3u ou tem get.php."),
            Triple(SourceType.XTREAM, "Xtream Codes" to Icons.Filled.VpnKey, "Informe o servidor, o usuário e a senha que o seu provedor deu."),
        )
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                options.forEach { (type, title, text) -> OptionCard(title.first, title.second, text, Modifier.width(300.dp)) { adding = type } }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.forEach { (type, title, text) -> OptionCard(title.first, title.second, text, Modifier.fillMaxWidth()) { adding = type } }
            }
        }
    }
    adding?.let { type ->
        SourceDialog(type, onDismiss = { adding = null }) {
            vm.addSource(it)
            adding = null
        }
    }
}

@Composable
private fun OptionCard(title: String, icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.tvFocusScale(focused, 1.04f).clip(shape).background(Onda.card)
            .then(if (focused) Modifier.border(3.dp, Onda.accent, shape) else Modifier.border(1.dp, Onda.glassBorder, shape))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
        Text(text, color = Onda.textDim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
