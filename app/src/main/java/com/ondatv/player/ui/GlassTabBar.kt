package com.ondatv.player.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.tensei.ytxtube.LiquidBottomTab
import com.tensei.ytxtube.LiquidBottomTabs

/** Bottom tab bar with the same liquid-glass look as the MBTV (YouTube) screen. */
@Composable
fun TabBar(
    selected: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val contentFocus = LocalContentFocus.current
    // -1 while the screen on top belongs to no tab (settings): then no tab is marked.
    val selIndex = tabs.indexOfFirst { it.route == selected }
    val currentSel by rememberUpdatedState(selIndex.coerceAtLeast(0))
    val selectedIndex = remember { { currentSel } }
    // A tab that doesn't become the selection (MBTV opens its own screen, "soon" only says so)
    // rebuilds the bar, so the pill goes back to the tab that is still selected.
    var resetKey by remember { mutableIntStateOf(0) }
    val choose: (Int) -> Unit = { i ->
        val t = tabs[i]
        if (t.soon) Toast.makeText(context, "Em breve", Toast.LENGTH_SHORT).show() else onSelect(t.route)
        if (t.soon || t.opensScreen) resetKey++
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tappable = WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding()
    val bottom = navBottom.coerceAtLeast(8.dp) + (if (tappable > 0.dp) 10.dp else 0.dp) + 10.dp

    Box(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = bottom),
        contentAlignment = Alignment.Center,
    ) {
        key(resetKey) {
            LiquidBottomTabs(
                selectedTabIndex = selectedIndex,
                onTabSelected = { i -> if (i != currentSel || selIndex < 0) choose(i) },
                backdrop = backdrop,
                tabsCount = tabs.size,
                modifier = Modifier.widthIn(max = 368.dp).fillMaxWidth(),
                isDarkSurface = true,
                showIndicator = selIndex >= 0,
            ) {
                tabs.forEachIndexed { i, t ->
                    LiquidBottomTab(
                        onClick = { choose(i) },
                        // A TV remote goes back up to the screen content from the bar.
                        modifier = contentFocus?.let { c -> Modifier.focusProperties { up = c } } ?: Modifier,
                    ) {
                        Icon(t.icon, contentDescription = t.label, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(
                            t.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (i == selIndex) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
