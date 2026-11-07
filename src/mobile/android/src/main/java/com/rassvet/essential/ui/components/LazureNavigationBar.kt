package com.rassvet.essential.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.rassvet.essential.R
import com.rassvet.essential.ui.theme.LazureIcons

/** Standard Material 3 navigation; this component owns the navigation-bar system inset. */
@Composable
fun LazureNavigationBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val destinations = listOf(
        Triple("home", R.string.nav_home, LazureIcons.Home),
        Triple("vault", R.string.nav_vault, LazureIcons.FolderOpen),
        Triple("ai", R.string.nav_ai, LazureIcons.Chat),
        Triple("settings", R.string.nav_settings, LazureIcons.Settings),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        destinations.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("nav_$route"),
            )
        }
    }
}
