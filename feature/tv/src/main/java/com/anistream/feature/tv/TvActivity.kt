package com.anistream.feature.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.anistream.feature.home.HomeViewModel
import com.anistream.feature.movies.MoviesHomeViewModel
import com.anistream.feature.movies.isMediaKey
import com.anistream.feature.player.WebEmbedPlayer
import dagger.hilt.android.AndroidEntryPoint

/** Launcher activity of the `tv` flavor (Android TV / Fire TV). D-pad only, no touch assumptions. */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReelmTvTheme { TvApp() } }
    }
}

internal object TvRoutes {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val ANIME = "anime"
    const val SEARCH = "search"
    const val LIVE = "live"
    const val LIBRARY = "library"
    const val MEDIA_PLAYER = "media_player"

    fun anime(slug: String) = "anime/${Uri.encode(slug)}"
    fun movie(id: String) = "movie/${Uri.encode(id)}"
    fun detail(id: String) = if (isMediaKey(id)) movie(id) else anime(id)
    fun player(slug: String, episode: Int) = "player/${Uri.encode(slug)}/$episode"
    fun web(imdb: String, season: Int, episode: Int) = "web/${Uri.encode(imdb)}/$season/$episode"
}

private data class RailEntry(val route: String, val label: String, val icon: ImageVector)

private val rail = listOf(
    RailEntry(TvRoutes.HOME, "Home", Icons.Outlined.Home),
    RailEntry(TvRoutes.SEARCH, "Search", Icons.Outlined.Search),
    RailEntry(TvRoutes.MOVIES, "Movies & TV", Icons.Outlined.Movie),
    RailEntry(TvRoutes.ANIME, "Anime", Icons.Outlined.AutoAwesome),
    RailEntry(TvRoutes.LIVE, "Live TV", Icons.Outlined.LiveTv),
    RailEntry(TvRoutes.LIBRARY, "Library", Icons.Outlined.VideoLibrary)
)

@Composable
private fun TvApp() {
    val nav = rememberNavController()
    // activity-scoped so Home, Movies & TV and Anime share one load
    val homeVm: HomeViewModel = hiltViewModel()
    val moviesVm: MoviesHomeViewModel = hiltViewModel()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showRail = rail.any { it.route == current }

    val host = @Composable {
        NavHost(nav, startDestination = TvRoutes.HOME) {
            composable(TvRoutes.HOME) { TvHomeScreen(HomeFilter.ALL, homeVm, moviesVm, nav) }
            composable(TvRoutes.MOVIES) { TvHomeScreen(HomeFilter.MEDIA, homeVm, moviesVm, nav) }
            composable(TvRoutes.ANIME) { TvHomeScreen(HomeFilter.ANIME, homeVm, moviesVm, nav) }
            composable(TvRoutes.SEARCH) { TvSearchScreen(onOpen = { nav.navigate(TvRoutes.detail(it)) }) }
            composable(TvRoutes.LIVE) { TvLiveScreen(onPlay = { nav.navigate(TvRoutes.MEDIA_PLAYER) }) }
            composable(TvRoutes.LIBRARY) { TvLibraryScreen(onOpen = { nav.navigate(TvRoutes.detail(it)) }) }
            composable("anime/{slug}") {
                TvAnimeDetailScreen(
                    onPlay = { slug, ep -> nav.navigate(TvRoutes.player(slug, ep)) },
                    onOpen = { nav.navigate(TvRoutes.anime(it)) }
                )
            }
            composable("movie/{id}") {
                TvMovieDetailScreen(onPlayer = { nav.navigate(TvRoutes.MEDIA_PLAYER) }, onWebPlayer = { tt, s, e -> nav.navigate(TvRoutes.web(tt, s, e)) })
            }
            composable("player/{slug}/{episode}") { TvAnimePlayerScreen(onExit = { nav.popBackStack() }) }
            composable(TvRoutes.MEDIA_PLAYER) {
                TvMediaPlayerScreen(onExit = { nav.popBackStack() }, onWebPlayer = { tt, s, e -> nav.navigate(TvRoutes.web(tt, s, e)) })
            }
            composable("web/{imdb}/{season}/{episode}") { entry ->
                val a = entry.arguments
                WebEmbedPlayer(
                    imdbId = a?.getString("imdb").orEmpty(),
                    season = a?.getString("season")?.toIntOrNull() ?: 0,
                    episode = a?.getString("episode")?.toIntOrNull() ?: 0,
                    onBack = { nav.popBackStack() },
                    tv = true
                )
            }
        }
    }

    // Icons only; the drawer expands with labels while it holds focus. Hidden (not removed) on detail/player
    // screens so the NavHost keeps its place in the composition.
    NavigationDrawer(
        drawerContent = {
            if (showRail) Column(
                Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 27.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, androidx.compose.ui.Alignment.CenterVertically)
            ) {
                rail.forEach { r ->
                    NavigationDrawerItem(
                        selected = current == r.route,
                        onClick = { nav.navigateTop(r.route) },
                        leadingContent = { Icon(r.icon, contentDescription = r.label) }
                    ) { Text(r.label) }
                }
            }
        }
    ) { host() }
}

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
