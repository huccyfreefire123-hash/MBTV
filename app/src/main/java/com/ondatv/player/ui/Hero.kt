package com.ondatv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ondatv.player.data.Details
import com.ondatv.player.data.Item
import kotlinx.coroutines.delay
import java.util.Locale

// ---------- What goes in the rows ----------

/** Best rated among the most recently added; plain recency when the list has no ratings. */
fun trending(list: List<Item>): List<Item> {
    val recent = newest(list, 300)
    return if (recent.any { (it.rating ?: 0f) > 0f }) {
        recent.sortedByDescending { it.rating ?: 0f }.take(20)
    } else {
        recent.take(20)
    }
}

fun recentlyAdded(list: List<Item>): List<Item> = newest(list, 20)

/** Whether the server gave a synopsis for this film or series (in the list or in its details). */
fun hasSynopsis(item: Item, details: Map<String, Details>): Boolean =
    !(details[item.key]?.plot ?: item.plot).isNullOrBlank()

/**
 * Banner: the titles of [pool] with a synopsis. When the server gave none (an M3U without
 * synopses), the banner keeps the best titles rather than disappearing.
 */
fun bannerItems(pool: List<Item>, details: Map<String, Details>, n: Int): List<Item> =
    pool.filter { hasSynopsis(it, details) }.ifEmpty { pool }.take(n)

/**
 * Releases for the "Em Alta" rows: release year (as the server gives it) of [year] or later,
 * newest first, only with a cover. When this year has fewer than [min], last year counts too;
 * items without a year are left out. [list] must already be free of adult content.
 */
fun releases(list: List<Item>, details: Map<String, Details>, year: Int, min: Int = 5, max: Int = 20): List<Item> {
    // The list's year first (every title has the same source); the film's own details only fill a gap.
    fun yearOf(item: Item): Int? =
        (item.year ?: details[item.key]?.year)?.trim()?.take(4)?.takeIf { it.length == 4 }?.toIntOrNull()
    fun from(first: Int): List<Item> = list.asSequence()
        .filter { !it.logo.isNullOrBlank() }
        .mapNotNull { item -> yearOf(item)?.takeIf { it >= first }?.let { it to item } }
        .sortedWith(compareByDescending<Pair<Int, Item>> { it.first }.thenByDescending { it.second.added })
        .map { it.second }
        .toList()
    val current = from(year)
    return (if (current.size >= min) current else from(year - 1)).take(max)
}

/** The [n] most recently added, newest first, without sorting the whole list. */
private fun newest(list: List<Item>, n: Int): List<Item> {
    if (list.size <= n) return list.sortedByDescending { it.added }
    val heap = java.util.PriorityQueue<Item>(n + 1, compareBy { it.added })
    for (item in list) {
        if (heap.size < n) heap.add(item)
        else if (item.added > heap.peek()!!.added) { heap.poll(); heap.add(item) }
    }
    return heap.sortedByDescending { it.added }
}

fun interleave(a: List<Item>, b: List<Item>): List<Item> {
    val out = ArrayList<Item>()
    for (i in 0 until maxOf(a.size, b.size)) {
        a.getOrNull(i)?.let(out::add)
        b.getOrNull(i)?.let(out::add)
    }
    return out
}

// ---------- Banner ----------

private val shadowed = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 12f))

/**
 * Full-width banner that pages through items by itself, with page dots below.
 *
 * For a TV remote the whole banner is one stop: left / right change the page, OK opens the one
 * on screen, and "up" goes to [upFocus], the buttons floating over the banner (they sit inside
 * its area, so the remote would never find them by itself). The focus stays on the banner while
 * the pages change.
 */
@Composable
fun Hero(items: List<Item>, details: Map<String, Details>, onOpen: (Item) -> Unit, upFocus: FocusRequester? = null) {
    if (items.isEmpty()) {
        Spacer(Modifier.statusBarsPadding().height(72.dp))
        return
    }
    val config = LocalConfiguration.current
    val height = (config.screenHeightDp * 0.57f).coerceIn(360f, config.screenWidthDp * 1.4f).dp
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    LaunchedEffect(pager, items.size) {
        while (true) {
            delay(8_000)
            if (!pager.isScrollInProgress && items.size > 1) {
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            }
        }
    }

    Box(
        Modifier.fillMaxWidth().height(height)
            .then(if (focused) Modifier.border(3.dp, Color.White) else Modifier)
            .focusProperties { if (upFocus != null) up = upFocus }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || items.size < 2) return@onKeyEvent false
                val page = when (e.key) {
                    Key.DirectionRight -> (pager.currentPage + 1) % items.size
                    Key.DirectionLeft -> if (pager.currentPage > 0) pager.currentPage - 1 else return@onKeyEvent false
                    else -> return@onKeyEvent false
                }
                scope.launch { pager.animateScrollToPage(page) }
                true
            }
            .clickable(interactionSource = interaction, indication = null) {
                items.getOrNull(pager.currentPage)?.let(onOpen)
            }
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val item = items[page]
            HeroPage(item, details[item.key])
        }
        Dots(items.size, pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

@Composable
private fun HeroPage(item: Item, d: Details?) {
    val image = d?.backdrop ?: item.backdrop ?: item.logo
    val year = d?.year ?: item.year
    val genre = (d?.genre ?: item.genre)?.split(',', '/')?.take(3)?.joinToString(", ") { it.trim() }
    val meta = listOfNotNull(year, genre, d?.duration, d?.age).joinToString(DOT)
    val rating = d?.rating ?: item.rating
    val plot = d?.plot ?: item.plot

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = image, contentDescription = item.name,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x59000000),
                    0.22f to Color.Transparent,
                    0.5f to Color(0x4016171F),
                    0.78f to Color(0xD916171F),
                    1f to Onda.heroBg,
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                item.name.uppercase(Locale.getDefault()),
                color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp,
                lineHeight = 40.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = shadowed,
            )
            if (meta.isNotEmpty()) {
                Text(
                    meta, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = shadowed, modifier = Modifier.padding(top = 18.dp),
                )
            }
            if (rating != null) {
                Row(
                    Modifier.padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).background(Onda.chip)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, null, tint = Onda.gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        String.format(Locale.US, "%.1f", rating),
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!plot.isNullOrBlank()) {
                Text(
                    plot, color = Onda.textDim, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == current) 20.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i == current) Color.White else Color(0x73FFFFFF))
            )
        }
    }
}
