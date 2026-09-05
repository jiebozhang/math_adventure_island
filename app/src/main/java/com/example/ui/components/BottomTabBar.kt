package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Dimens
import com.example.ui.theme.NavbarBlue
import com.example.ui.theme.MutedGray
import com.example.ui.theme.SurfaceWhite

data class TabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomTabs = listOf(
    TabItem("home", "冒险", Icons.Filled.Explore, Icons.Filled.Explore),
    TabItem("codex", "图鉴", Icons.Filled.AutoStories, Icons.Filled.AutoStories),
    TabItem("diary", "日记", Icons.Filled.MenuBook, Icons.Filled.MenuBook),
    TabItem("parent", "家长", Icons.Filled.Shield, Icons.Filled.Shield)
)

@Composable
fun BottomTabBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val d = Dimens.current
    Surface(
        color = SurfaceWhite,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = SurfaceWhite,
            modifier = Modifier
                .fillMaxWidth()
                .height(d.bottomBarHeight)
                .navigationBarsPadding()
        ) {
            bottomTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(tab.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(d.iconMedium)
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            fontSize = d.microFontSize,
                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NavbarBlue,
                        selectedTextColor = NavbarBlue,
                        unselectedIconColor = MutedGray,
                        unselectedTextColor = MutedGray,
                        indicatorColor = NavbarBlue.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
