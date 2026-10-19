package com.anistream.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink

enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Home("home", "Anime", Icons.Outlined.Home),
    Movies("movies", "Movies", Icons.Outlined.Movie),
    LiveTv("live_tv", "Live TV", Icons.Outlined.LiveTv),
    Search("search", "Search", Icons.Outlined.Search),
    Library("library", "Library", Icons.Outlined.VideoLibrary)
}

/** Black 95% bar, hairline top border; selected = gold icon + gold uppercase label + a 2dp gold bar above the icon. */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Bg.bar)) {
        HorizontalDivider(thickness = 1.dp, color = Ink.hairline)
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = Ink.body,
            tonalElevation = 0.dp
        ) {
            BottomNavItem.entries.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(width = 24.dp, height = 2.dp)
                                    .background(if (selected) Accent.main else Color.Transparent)
                            )
                            Spacer(Modifier.height(4.dp))
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    label = { Text(item.label.uppercase(), style = AppType.label.copy(color = Color.Unspecified, fontSize = 10.sp, letterSpacing = 0.4.sp), maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent.main,
                        selectedTextColor = Accent.main,
                        unselectedIconColor = Ink.muted,
                        unselectedTextColor = Ink.muted,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}
