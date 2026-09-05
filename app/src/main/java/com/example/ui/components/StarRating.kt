package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentGold
import kotlinx.coroutines.delay

@Composable
fun StarRating(
    starCount: Int,
    maxStars: Int = 3,
    modifier: Modifier = Modifier
) {
    var visibleStars by remember { mutableIntStateOf(0) }

    LaunchedEffect(starCount) {
        visibleStars = 0
        for (i in 1..starCount) {
            delay(300)
            visibleStars = i
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 1..maxStars) {
            val lit = i <= visibleStars
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(initialScale = 0.5f) + fadeIn()
            ) {
                Icon(
                    imageVector = if (lit) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "星级 $i",
                    tint = if (lit) AccentGold else AccentGold.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
