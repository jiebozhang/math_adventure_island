package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserSettings
import com.example.ui.theme.*

@Composable
fun UserCard(
    userSettings: UserSettings,
    modifier: Modifier = Modifier
) {
    val expInCurrentLevel = userSettings.exp % 50
    val expProgress = expInCurrentLevel / 50f
    val animatedProgress by animateFloatAsState(
        targetValue = expProgress,
        animationSpec = tween(durationMillis = 800),
        label = "expProgress"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = NavbarBlue,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MilitaryTech,
                            contentDescription = "等级",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "小勇者 Lv.${userSettings.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        "数学探险家",
                        fontSize = 12.sp,
                        color = MutedGray
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = "连胜",
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "${userSettings.streakDays} 天",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentOrange
                    )
                }
            }
            // EXP 进度条
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "距离 Lv.${userSettings.level + 1}",
                        fontSize = 11.sp,
                        color = MutedGray
                    )
                    Text(
                        "$expInCurrentLevel / 50",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NavbarBlue
                    )
                }
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NavbarBlue,
                    trackColor = BorderGray,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}
