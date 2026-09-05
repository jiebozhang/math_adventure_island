package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class ThinkingStoneCategory(
    val name: String,
    val current: Int,
    val total: Int,
    val color: Color
)

@Composable
fun ThinkingStonesGrid(
    masteredCount: Int,
    totalQuestions: Int,
    modifier: Modifier = Modifier
) {
    // 6 思想类别（当前简化为按已掌握数等分）
    val per = if (totalQuestions > 0) masteredCount / 6 else 0
    val perTotal = if (totalQuestions > 0) (totalQuestions + 5) / 6 else 0
    val categories = listOf(
        ThinkingStoneCategory("数形结合", per, perTotal, StoneNumberShape),
        ThinkingStoneCategory("转化", per, perTotal, StoneTransform),
        ThinkingStoneCategory("方程", per, perTotal, StoneEquation),
        ThinkingStoneCategory("模型", per, perTotal, StoneModel),
        ThinkingStoneCategory("统计", per, perTotal, StoneStatistic),
        ThinkingStoneCategory("推理", per, perTotal, StoneReasoning)
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("思想印记", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text(
                    "$masteredCount / $totalQuestions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NavbarBlue
                )
            }
            categories.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cat.color.copy(alpha = 0.15f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cat.color
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${cat.current}/${cat.total}",
                                    fontSize = 11.sp,
                                    color = cat.color.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
