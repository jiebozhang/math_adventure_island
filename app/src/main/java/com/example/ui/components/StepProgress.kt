package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design spec: 进度条填充色金色 #FFC857，轨道底色 #F5E5BD
private val ProgressFill = Color(0xFFFFC857)
private val ProgressTrack = Color(0xFFF5E5BD)

@Composable
fun StepProgress(
    currentStep: Int,
    totalSteps: Int = 7,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (currentStep + 1f) / totalSteps,
        animationSpec = tween(400),
        label = "stepProgress"
    )

    // Design spec: 左侧文本「第 X 步 / 共 7 步」，右侧一条进度条
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "第 ${currentStep + 1} 步 / 共 $totalSteps 步",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF6B6B6B)
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ProgressFill,
            trackColor = ProgressTrack,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
