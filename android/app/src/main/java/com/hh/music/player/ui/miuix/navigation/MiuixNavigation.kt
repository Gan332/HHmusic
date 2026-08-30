package com.hh.music.player.ui.miuix.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hh.music.player.ui.Routes

data class MiuixTabItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun MiuixBottomNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val tabs = listOf(
        MiuixTabItem(Routes.DISCOVER, "首页") { Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(24.dp)) },
        MiuixTabItem(Routes.SEARCH, "发现") { Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(24.dp)) },
        MiuixTabItem(Routes.LIBRARY, "收藏") { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(24.dp)) },
        MiuixTabItem(Routes.SETTINGS, "设置") { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(24.dp)) }
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        tabs.forEach { tab ->
            val route = if (tab.route == Routes.SEARCH) Routes.search() else tab.route
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onNavigate(route) },
                icon = tab.icon,
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
