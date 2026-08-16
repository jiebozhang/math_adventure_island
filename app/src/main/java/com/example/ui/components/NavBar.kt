package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MonsterStats
import com.example.data.model.UserSettings
import com.example.ui.theme.NavbarBlue

@Composable
fun NavBar(
    currentRoute: String,
    userSettings: UserSettings,
    monsterStats: List<MonsterStats>,
    onNavigate: (String) -> Unit
) {
    val totalDefeated = monsterStats.sumOf { it.defeatedCount }

    Surface(
        color = NavbarBlue,
        shadowElevation = 2.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // App Title Logo & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "数学冒险岛",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                }

                // Stats Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        icon = Icons.Default.MilitaryTech,
                        text = "Lv.${userSettings.level}",
                        tint = Color(0xFFFFE066)
                    )
                    StatusChip(
                        icon = Icons.Default.EmojiEvents,
                        text = "打退 $totalDefeated 只",
                        tint = Color(0xFFFFD700)
                    )
                    StatusChip(
                        icon = Icons.Default.LocalFireDepartment,
                        text = "连胜 ${userSettings.streakDays} 天",
                        tint = Color(0xFFFF8A65)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NavButton(
                    label = "地图",
                    icon = Icons.Default.Map,
                    isSelected = currentRoute == "map",
                    onClick = { onNavigate("map") }
                )
                NavButton(
                    label = "图鉴",
                    icon = Icons.Default.Pets,
                    isSelected = currentRoute == "codex",
                    onClick = { onNavigate("codex") }
                )
                NavButton(
                    label = "成长",
                    icon = Icons.Default.Assessment,
                    isSelected = currentRoute == "diary",
                    onClick = { onNavigate("diary") }
                )
                NavButton(
                    label = "训练",
                    icon = Icons.Default.FitnessCenter,
                    isSelected = currentRoute == "camp",
                    onClick = { onNavigate("camp") }
                )
                NavButton(
                    label = "家长",
                    icon = Icons.Default.AdminPanelSettings,
                    isSelected = currentRoute == "parent",
                    onClick = { onNavigate("parent") }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun NavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.White else Color.Transparent,
            contentColor = if (isSelected) NavbarBlue else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = if (isSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null,
        modifier = Modifier.wrapContentWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}
