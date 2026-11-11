package com.ondatv.player.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ondatv.player.AppViewModel
import com.ondatv.player.PlayRequest
import com.ondatv.player.data.Kind

// [soon]: shown in the bar but without a screen yet ("Em breve").
// [opensScreen]: opens another screen instead of becoming the selected tab.
internal data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val soon: Boolean = false,
    val opensScreen: Boolean = false,
)

internal val tabs = listOf(
    Tab("home", "Início", Icons.Filled.Home),
    Tab("live", "TV", Icons.Outlined.Tv),
    Tab("series", "Séries", Icons.Filled.VideoLibrary),
    Tab("movies", "Filmes", Icons.Filled.LocalMovies),
    Tab("mbtv", "MBTV", Icons.Filled.PlayCircle, opensScreen = true),
)

@Composable
fun App(vm: AppViewModel, play: (PlayRequest) -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val kindArg = entry?.arguments?.getString("kind")
    var lastTab by remember { mutableStateOf("home") }
    val selected = when {
        route == "home" -> "home"
        route == "live" || route.startsWith("channels") -> "live"
        route == "movies" || kindArg == Kind.MOVIE.name -> "movies"
        route.startsWith("show") -> if (showKind(vm, entry?.arguments?.getString("key")) == Kind.MOVIE) "movies" else "series"
        route == "series" || kindArg == Kind.SERIES.name -> "series"
        // Settings belongs to no tab: none is marked while it is open.
        route.startsWith("settings") -> ""
        else -> lastTab
    }
    SideEffect { if (selected.isNotEmpty()) lastTab = selected }

    val openSeries: (String) -> Unit = { key -> nav.navigate("show/" + Uri.encode(key)) }
    val openSearch: () -> Unit = { nav.navigate("search") }
    val back: () -> Unit = { nav.popBackStack() }
    val grid: (Kind, String) -> Unit = { k, g -> nav.navigate("grid/${k.name}/" + Uri.encode(g)) }

    val contentFocus = remember { FocusRequester() }
    // What the screens draw, seen through the glass tab bar.
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalContentFocus provides contentFocus) {
    Box(Modifier.fillMaxSize().background(Onda.heroBg)) {
        NavHost(
            nav, startDestination = "home",
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(Onda.heroBg).focusRequester(contentFocus).focusGroup(),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable("home") {
                HomeScreen(
                    vm, play, openSeries, openSearch,
                    openEditList = { nav.navigate("settings?edit=1") },
                    openProfiles = { nav.navigate("profiles?locked=0") },
                    openSports = { nav.navigate("sports") },
                    openGrid = grid,
                )
            }
            composable("sports") { SportsScreen(vm, play, back) }
            composable("multiview") { MultiviewScreen(vm, back) }
            composable("profiles?locked={locked}") { e ->
                val locked = e.arguments?.getString("locked") == "1"
                ProfilesScreen(vm, locked, openSettings = {
                    // Settings take the place of this screen, so "back" returns to where it was opened.
                    nav.popBackStack()
                    nav.navigate("settings?edit=0")
                }) { nav.popBackStack() }
            }
            composable("live") {
                LiveMenuScreen(vm, openSearch, openMultiview = { nav.navigate("multiview") }) { g -> nav.navigate("channels/" + Uri.encode(g)) }
            }
            composable("channels/{group}") { e ->
                ChannelListScreen(vm, Uri.decode(e.arguments?.getString("group").orEmpty()), play, back)
            }
            composable("movies") {
                BrowseScreen(vm, Kind.MOVIE, play, openSeries, openSearch,
                    openCategories = { nav.navigate("cats/MOVIE") }, openGrid = { grid(Kind.MOVIE, it) },
                    openEditList = { nav.navigate("settings?edit=1") })
            }
            composable("series") {
                BrowseScreen(vm, Kind.SERIES, play, openSeries, openSearch,
                    openCategories = { nav.navigate("cats/SERIES") }, openGrid = { grid(Kind.SERIES, it) },
                    openEditList = { nav.navigate("settings?edit=1") })
            }
            composable("cats/{kind}") { e ->
                val kind = Kind.valueOf(e.arguments?.getString("kind") ?: Kind.MOVIE.name)
                CategoriesScreen(vm, kind, back) { grid(kind, it) }
            }
            composable("grid/{kind}/{group}") { e ->
                val kind = Kind.valueOf(e.arguments?.getString("kind") ?: Kind.MOVIE.name)
                GridScreen(vm, kind, Uri.decode(e.arguments?.getString("group").orEmpty()), play, openSeries, back)
            }
            composable("settings?edit={edit}") { e ->
                SettingsScreen(vm, back, openProfiles = { nav.navigate("profiles?locked=0") }, startEditing = e.arguments?.getString("edit") == "1")
            }
            composable("search") { SearchScreen(vm, play, openSeries, back) }
            // A film opens its details page, a series its episodes.
            composable("show/{key}") { e ->
                val key = Uri.decode(e.arguments?.getString("key").orEmpty())
                if (vm.item(key)?.kind == Kind.MOVIE) MovieScreen(vm, key, play, back)
                else SeriesScreen(vm, key, play, back)
            }
        }
        if (!route.startsWith("profiles") && route != "multiview") {
            TabBar(selected, backdrop, Modifier.align(Alignment.BottomCenter)) {
                // MBTV is the YTXTube app in its own screen; Back there returns here.
                if (it == "mbtv") context.startActivity(Intent(context, com.tensei.ytxtube.MainActivity::class.java))
                else nav.goTab(it)
            }
        }
    }
    }

    // At start, ask who is watching when there are several profiles or the current one has a PIN.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val needPin = !vm.currentProfile().pin.isNullOrEmpty()
        if (vm.profiles.value.size > 1 || needPin) {
            nav.navigate("profiles?locked=" + if (needPin) "1" else "0")
        }
    }
}

private fun showKind(vm: AppViewModel, raw: String?): Kind? = raw?.let { vm.item(Uri.decode(it))?.kind }

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
