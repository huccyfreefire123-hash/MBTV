package com.ondatv.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small "Próximo episódio" button for the last 60 seconds of an episode. [label] null hides it.
 * It takes the focus when it appears, so the remote control's OK presses it.
 */
@Composable
fun NextEpisodeButton(label: String?, secondsLeft: Int, onClick: () -> Unit) {
    // Keep the last title while the exit animation runs.
    var shown by remember { mutableStateOf("") }
    if (label != null) shown = label
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    AnimatedVisibility(
        visible = label != null,
        enter = fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 3 },
        exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 3 },
    ) {
        Row(
            Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(Color(0xD91C1D24))
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0x40FFFFFF), shape)
                .focusRequester(focus)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 10.dp)) {
                Text("Próximo episódio", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "$shown" + if (secondsLeft > 0) DOT + "em $secondsLeft s" else "",
                    color = Color(0xB3FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}
