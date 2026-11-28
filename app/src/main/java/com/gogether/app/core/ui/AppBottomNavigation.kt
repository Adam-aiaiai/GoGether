package com.gogether.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NavigationItem(val route: String, val label: String,
    val selectedIcon: ImageVector, val idleIcon: ImageVector)

private val navigationItems = listOf(
    NavigationItem("explore", "Explore", Icons.Filled.Home, Icons.Outlined.Home),
    NavigationItem("create", "Create", Icons.Filled.Add, Icons.Outlined.Add),
    NavigationItem("my-events", "My Events", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    NavigationItem("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
)
internal val AppTopLevelRoutes = navigationItems.map { it.route }.toSet()

/** Visual states only; the existing app owns every navigation callback. */
@Composable
internal fun AppBottomNavigation(selectedRoute: String, unreadCount: Int = 0, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = AppUi.NavigationBackground, tonalElevation = 0.dp,
        modifier = Modifier.testTag("bottom_navigation")) {
        navigationItems.forEach { item ->
            val selected = selectedRoute == item.route
            val interactions = remember { MutableInteractionSource() }
            val pressedScale = rememberPressScale(interactions)
            val selectedScale by animateFloatAsState(
                if (selected) AppMotion.SelectedIconScale else 1f,
                animationSpec = AppMotion.rebound(), label = "Selected navigation icon",
            )
            val icon = if (selected) item.selectedIcon else item.idleIcon
            val tint = if (selected) AppUi.Primary else Color(0xFF77777F)
            NavigationBarItem(
                selected = selected, onClick = { onNavigate(item.route) },
                interactionSource = interactions,
                modifier = Modifier.testTag("nav_${item.route}").graphicsLayer {
                    scaleX = pressedScale.value
                    scaleY = pressedScale.value
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppUi.Primary, selectedTextColor = AppUi.Primary,
                    unselectedIconColor = tint, unselectedTextColor = tint,
                    indicatorColor = Color.Transparent,
                ),
                icon = {
                    Box(Modifier.size(24.dp).graphicsLayer {
                        scaleX = selectedScale
                        scaleY = selectedScale
                    }) {
                        // A faint glyph-shaped shadow, never a new background pill or rectangle.
                        Icon(icon, contentDescription = null, tint = Color.Black,
                            modifier = Modifier.size(24.dp).offset(y = 1.dp).graphicsLayer {
                                alpha = .12f * (1f - .8f * pressDepth(pressedScale.value))
                            })
                        Icon(icon, contentDescription = null, tint = tint,
                            modifier = Modifier.size(24.dp).testTag("nav_icon_${item.route}"))
                        if(item.route=="my-events" && unreadCount>0) Box(Modifier.align(Alignment.TopEnd)
                            .offset(x=5.dp,y=(-3).dp).size(7.dp).background(Color(0xFFEF534B),CircleShape)
                            .testTag("my_events_unread").semantics { contentDescription="$unreadCount unread notifications" })
                    }
                },
                label = {
                    Text(item.label, maxLines = 2, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center)
                },
            )
        }
    }
}
