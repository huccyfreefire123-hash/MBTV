package com.ondatv.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ondatv.player.data.Item
import com.ondatv.player.data.Programme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DOT = " · "
const val DASH = " – "

private val textShadow = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f))

// ---------- Sizes that follow the screen width ----------

@Composable
fun posterWidth(): Dp {
    val w = LocalConfiguration.current.screenWidthDp
    return (w * 0.29f).coerceIn(104f, 170f).dp
}

@Composable
fun liveCardWidth(): Dp {
    val w = LocalConfiguration.current.screenWidthDp
    return (w * 0.35f).coerceIn(128f, 220f).dp
}

/** Room left at the end of a scrolling screen for the floating tab bar. */
@Composable
fun BottomSpace() {
    Spacer(Modifier.navigationBarsPadding().height(104.dp))
}

// ---------- Remote control focus ----------

/** The current screen's content area; overlay buttons and the tab bar move into it. */
val LocalContentFocus = staticCompositionLocalOf<FocusRequester?> { null }

/** True inside [TopControls], whose buttons float over the scrolling content. */
val LocalInOverlay = compositionLocalOf { false }

/**
 * Overlays sit on top of the content, so the remote's "down" would skip it; send it there.
 * Put this before the clickable it applies to.
 */
@Composable
fun Modifier.focusIntoContent(): Modifier {
    val target = LocalContentFocus.current
    if (!LocalInOverlay.current || target == null) return this
    return this.focusProperties { down = target }
}

// ---------- Frosted controls ----------

@Composable
fun GlassCircle(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Onda.glass)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Onda.glassBorder, CircleShape)
            .focusIntoContent()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun GlassPill(content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(Onda.glass)
            .border(1.dp, Onda.glassBorder, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun PillIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.size(width = 46.dp, height = 40.dp).clip(CircleShape)
            .then(if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .focusIntoContent()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Fixed row of floating buttons over the top of a screen, with a soft dark scrim behind. */
@Composable
fun TopControls(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color(0xE616171F), 0.55f to Color(0x9916171F), 1f to Color.Transparent))
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalInOverlay provides true) {
                left()
                Spacer(Modifier.weight(1f))
                right()
            }
        }
    }
}

/** Title bar of list screens: round back button, centred title, optional round action. */
@Composable
fun CenterBar(
    title: String,
    onBack: () -> Unit,
    onTitle: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircle(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Voltar", onBack)
        Row(
            Modifier.weight(1f).padding(horizontal = 8.dp)
                .then(if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (onTitle != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = Onda.textFaint, modifier = Modifier.size(22.dp),
                )
            }
        }
        if (action != null) action() else Spacer(Modifier.size(44.dp))
    }
}

/** Large left-aligned title used by tab screens without a banner. */
@Composable
fun LargeTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

// ---------- Rows and cards ----------

@Composable
fun SectionHeader(title: String, subtitle: String? = null, onMore: (() -> Unit)? = null) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
            .then(if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (subtitle != null) title + DOT + subtitle else title,
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (onMore != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun RatingChip(rating: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Onda.chip).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(String.format(Locale.US, "%.1f", rating), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, track: Color = Color(0x40FFFFFF)) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
    }
}

@Composable
fun Initials(name: String, modifier: Modifier = Modifier, size: Int = 22) {
    Text(
        name.trim().take(2).uppercase(),
        color = Onda.textFaint, fontSize = size.sp, fontWeight = FontWeight.SemiBold, modifier = modifier,
    )
}

@Composable
fun Poster(
    title: String,
    image: String?,
    width: Dp,
    onClick: () -> Unit,
    rank: Int? = null,
    rating: Float? = null,
    progress: Float? = null,
    subtitle: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.width(width).clickable(interactionSource = interaction, indication = null, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Onda.card)
                .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier),
        ) {
            Initials(title, Modifier.align(Alignment.Center), size = 28)
            AsyncImage(
                model = image, contentDescription = title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            if (rank != null) {
                Text(
                    rank.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                    style = textShadow, modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 2.dp),
                )
            }
            if (rating != null) RatingChip(rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (progress != null) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(10.dp), height = 4.dp)
            }
        }
        Text(
            title, color = Onda.textDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = if (subtitle == null) 4.dp else 0.dp),
        )
        if (subtitle != null) {
            Text(subtitle, color = Onda.textFaint, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
fun episodeThumbWidth(): Dp {
    val w = LocalConfiguration.current.screenWidthDp
    return (w * 0.4f).coerceIn(140f, 260f).dp
}

/** A season in the page of a series: its name, how many episodes, and an arrow to open it. */
@Composable
fun SeasonRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, color = Onda.textFaint, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Onda.textFaint, modifier = Modifier.size(22.dp))
    }
    HorizontalDivider(color = Onda.divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

/** Episode in the list of a season: wide picture with its number and progress, title, synopsis. */
@Composable
fun EpisodeRow(number: Int, title: String, plot: String?, image: String?, progress: Float?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(episodeThumbWidth()).aspectRatio(16f / 9f).clip(shape).background(Onda.card)
                    .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier),
            ) {
                AsyncImage(
                    model = image, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(), alpha = 0.55f,
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
                if (number > 0) {
                    Text(
                        "E$number", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, style = textShadow,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 10.dp),
                    )
                }
                if (progress != null) {
                    ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 6.dp), height = 3.dp)
                }
            }
            Text(
                title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
        }
        if (!plot.isNullOrBlank()) {
            Text(
                plot, color = Onda.textDim, fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun PosterRow(items: List<Item>, ranked: Boolean = false, onClick: (Item) -> Unit) {
    val width = posterWidth()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
            Poster(item.name, item.logo, width, { onClick(item) }, rank = if (ranked) i + 1 else null, rating = item.rating)
        }
    }
}

fun fraction(p: Programme, now: Long): Float =
    ((now - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1)).coerceIn(0f, 1f)

/** Recently watched channel: wide dark card with the logo in the middle. */
@Composable
fun LiveCard(item: Item, current: Programme?, now: Long, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.width(liveCardWidth()).clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.42f).clip(RoundedCornerShape(12.dp)).background(Onda.card)
                .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Initials(item.name)
            AsyncImage(
                model = item.logo, contentDescription = item.name,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.5f),
            )
            if (current != null) {
                ProgressLine(
                    fraction(current, now),
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp),
                    height = 4.dp,
                )
            }
        }
        Text(
            item.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            current?.title ?: "Sem informação", color = Onda.textFaint, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Channel in a category: logo, name, current programme, its times and a progress line. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(item: Item, current: Programme?, now: Long, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 76.dp, height = 56.dp), contentAlignment = Alignment.Center) {
            if (item.logo == null) Initials(item.name, size = 18)
            AsyncImage(
                model = item.logo, contentDescription = item.name,
                contentScale = ContentScale.Fit, modifier = Modifier.size(56.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                item.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (current != null) {
                Text(
                    current.title, color = Onda.textDim, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    clock(current.start) + DASH + clock(current.stop), color = Onda.textFaint, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                ProgressLine(fraction(current, now), Modifier.padding(top = 6.dp))
            } else {
                Text("Sem informação", color = Onda.textFaint, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    HorizontalDivider(color = Onda.divider, thickness = 0.5.dp)
}

/** Menu entry with a leading icon and a trailing chevron. */
@Composable
fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Onda.textDim, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            label, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Onda.textFaint, modifier = Modifier.size(22.dp))
    }
    HorizontalDivider(color = Onda.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = Onda.textFaint, fontSize = 16.sp)
    }
}

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun clock(ms: Long): String = clockFormat.format(Date(ms))
