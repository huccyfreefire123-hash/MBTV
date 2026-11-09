package com.ondatv.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private const val MAX_DIGITS = 8

/**
 * Full-screen PIN entry with an on-screen number pad (touch, remote D-pad + OK, or the number
 * keys of a remote or keyboard). [check] says whether the PIN is right; a wrong one shakes the
 * dots and shows "Senha incorreta. Tente novamente."
 */
@Composable
fun PinPad(title: String, subtitle: String, check: (String) -> Boolean, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val firstKey = remember { FocusRequester() }

    fun submit() {
        if (pin.isEmpty()) return
        if (check(pin)) {
            onSuccess()
        } else {
            wrong = true
            pin = ""
            scope.launch {
                for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
            }
        }
    }

    fun type(d: Char) {
        if (pin.length < MAX_DIGITS) {
            pin += d
            wrong = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Onda.listBackground)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = when (e.key) {
                        Key.Zero, Key.NumPad0 -> '0'; Key.One, Key.NumPad1 -> '1'; Key.Two, Key.NumPad2 -> '2'
                        Key.Three, Key.NumPad3 -> '3'; Key.Four, Key.NumPad4 -> '4'; Key.Five, Key.NumPad5 -> '5'
                        Key.Six, Key.NumPad6 -> '6'; Key.Seven, Key.NumPad7 -> '7'; Key.Eight, Key.NumPad8 -> '8'
                        Key.Nine, Key.NumPad9 -> '9'; else -> null
                    }
                    when {
                        digit != null -> { type(digit); true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { pin = pin.dropLast(1); true }
                        e.key == Key.NumPadEnter -> { submit(); true }
                        else -> false
                    }
                },
        ) {
            Box(Modifier.statusBarsPadding().padding(16.dp)) {
                GlassCircle(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Cancelar", onDismiss)
            }
            val header: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Onda.glass).border(1.dp, Onda.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Text(
                        title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        subtitle, color = Onda.textDim, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        Modifier.padding(top = 26.dp).height(18.dp).offset(x = shake.value.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(maxOf(4, pin.length)) { i ->
                            Box(
                                Modifier.size(14.dp).clip(CircleShape)
                                    .background(if (i < pin.length) Color.White else Color.Transparent)
                                    .border(1.5.dp, if (wrong) Color(0xFFFF6B6B) else Color(0x99FFFFFF), CircleShape)
                            )
                        }
                    }
                    Text(
                        if (wrong) "Senha incorreta. Tente novamente." else " ",
                        color = Color(0xFFFF8A80), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            val pad: @Composable (Dp, Dp) -> Unit = { key, gap ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    listOf("123", "456", "789").forEachIndexed { r, keys ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap + 4.dp)) {
                            keys.forEachIndexed { c, d ->
                                PadKey(
                                    label = d.toString(), size = key,
                                    modifier = if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier,
                                ) { type(d) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap + 4.dp)) {
                        PadKey(icon = Icons.AutoMirrored.Filled.Backspace, description = "Apagar", size = key) { pin = pin.dropLast(1) }
                        PadKey(label = "0", size = key) { type('0') }
                        PadKey(icon = Icons.Filled.Check, description = "Confirmar", strong = true, size = key) { submit() }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxHeight < 560.dp && maxWidth > maxHeight) {
                    // Landscape phone: the title on the left, the whole pad on the right, all on screen.
                    val key = ((maxHeight - 72.dp) / 4.6f).coerceIn(44.dp, 68.dp)
                    Row(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { header() }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { pad(key, key * 0.18f) }
                    }
                } else {
                    Column(
                        Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        header()
                        Spacer(Modifier.height(18.dp))
                        pad(68.dp, 14.dp)
                    }
                }
            }
        }
    }
    // Remote controls start on the pad; touch screens are not affected.
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
}

@Composable
private fun PadKey(
    label: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    strong: Boolean = false,
    size: Dp = 68.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (strong) Color.White else Onda.glass)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Onda.glassBorder, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (strong) Color.Black else Color.White
        if (label != null) {
            Text(label, color = tint, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        } else if (icon != null) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}
