package com.anistream.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anistream.core.ui.components.BottomNavBar
import com.anistream.core.ui.components.BottomNavItem
import com.anistream.core.ui.theme.Bg
import com.anistream.feature.anime.AnimeDetailScreen
import com.anistream.feature.downloads.DownloadsScreen
import com.anistream.feature.favorites.FavoritesScreen
import com.anistream.feature.genre.GenreBrowseScreen
import com.anistream.feature.home.HomeScreen
import com.anistream.feature.player.PlayerScreen
import com.anistream.feature.schedule.ScheduleScreen
import com.anistream.feature.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val ANIME_DETAIL = "anime_detail/{slug}"
    const val PLAYER = "player/{slug}/{episode}"
    /** path is optional: absent = genre picker, else a category path like "top-airing" or "genres/action". */
    const val GENRE_BROWSE = "genre_browse?path={path}"
    const val SCHEDULE = "schedule"

    fun animeDetail(slug: String) = "anime_detail/$slug"
    fun player(slug: String, episode: Int) = "player/$slug/$episode"
    fun browse(path: String? = null) = if (path == null) "genre_browse" else "genre_browse?path=${Uri.encode(path)}"
}

private val bottomBarRoutes = setOf(
    Routes.HOME,
    Routes.SEARCH,
    Routes.FAVORITES,
    Routes.SCHEDULE,
    Routes.DOWNLOADS
)

@Composable
fun AniStreamNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomBarRoutes

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // No system-bar insets here: the only padding the Scaffold contributes is the bottom bar
    // (which itself sits above the gesture bar). Screens decide about the status bar; see [Insetted].
    Scaffold(
        containerColor = Bg.base,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateToTab
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = {
                fadeIn(tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    tween(300)
                )
            },
            exitTransition = {
                fadeOut(tween(300))
            },
            popEnterTransition = {
                fadeIn(tween(300))
            },
            popExitTransition = {
                fadeOut(tween(300)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    tween(300)
                )
            }
        ) {
            composable(Routes.HOME) {
                // Home draws its hero behind the status bar and pads its own top bar.
                HomeScreen(
                    onAnimeClick = { slug ->
                        navController.navigate(Routes.animeDetail(slug))
                    },
                    onSeeAllClick = { path ->
                        navController.navigate(Routes.browse(path))
                    },
                    onGenresClick = {
                        navController.navigate(Routes.browse())
                    },
                    onSearchClick = { navigateToTab(Routes.SEARCH) },
                    onPlayEpisode = { slug, episode ->
                        navController.navigate(Routes.player(slug, episode))
                    }
                )
            }

            composable(Routes.SEARCH) {
                Insetted {
                    SearchScreen(
                        onAnimeClick = { slug ->
                            navController.navigate(Routes.animeDetail(slug))
                        }
                    )
                }
            }

            composable(Routes.FAVORITES) {
                Insetted {
                    FavoritesScreen(
                        onAnimeClick = { slug ->
                            navController.navigate(Routes.animeDetail(slug))
                        },
                        onDiscoverClick = { navigateToTab(Routes.HOME) }
                    )
                }
            }

            composable(Routes.DOWNLOADS) {
                Insetted { DownloadsScreen() }
            }

            composable(
                route = Routes.ANIME_DETAIL,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) {
                // Edge to edge: detail handles its own insets.
                AnimeDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAnimeClick = { slug ->
                        navController.navigate(Routes.animeDetail(slug))
                    },
                    onPlayEpisode = { slug, episode ->
                        navController.navigate(Routes.player(slug, episode))
                    },
                    onGenreClick = { genreSlug ->
                        navController.navigate(Routes.browse("genres/$genreSlug"))
                    }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("episode") { type = NavType.IntType }
                )
            ) {
                // Edge to edge: player handles its own insets.
                PlayerScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.GENRE_BROWSE,
                arguments = listOf(navArgument("path") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                Insetted {
                    GenreBrowseScreen(
                        onAnimeClick = { slug ->
                            navController.navigate(Routes.animeDetail(slug))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Routes.SCHEDULE) {
                Insetted {
                    ScheduleScreen(
                        onAnimeClick = { slug ->
                            navController.navigate(Routes.animeDetail(slug))
                        }
                    )
                }
            }
        }
    }
}

/**
 * Pads a destination below the status bar (and above the gesture bar when there is no bottom nav).
 * The insets are consumed, so inner Scaffolds/TopAppBars don't add them a second time.
 */
@Composable
private fun Insetted(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { content() }
}
