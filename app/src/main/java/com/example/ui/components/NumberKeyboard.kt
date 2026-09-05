package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Dimens

// Design spec: 数字键深底 #2D2A26 白字；运算符键琥珀色 #B45309
private val KeyDarkBg = Color(0xFF2D2A26)
private val KeyOperatorBg = Color(0xFFB45309)
private val KeyDeleteBg = Color(0xFF6B6258)

/**
 * 自定义数字键盘（4×4 设计稿布局）。
 *
 * 平板适配：
 * - 按键高度取 Dimens.keyHeight（手机 48dp / 平板 60dp），保证触控热区
 * - 通过 maxWidth 限制键盘宽度并居中，避免宽屏下按键被拉得又宽又扁
 */
@Composable
fun NumberKeyboard(
    onDigit: (String) -> Unit,
    onOperator: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified
) {
    val d = Dimens.current
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf("删除", "0", ".", "+")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (d.isTablet) 8.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.then(
                    if (maxWidth == Dp.Unspecified) Modifier.fillMaxWidth()
                    else Modifier.widthIn(max = maxWidth).fillMaxWidth(0.72f)
                ),
                verticalArrangement = Arrangement.spacedBy(if (d.isTablet) 8.dp else 4.dp)
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (d.isTablet) 8.dp else 4.dp)
                    ) {
                        row.forEach { key ->
                            val isOperator = key in listOf("÷", "×", "−", "+")
                            val isDelete = key == "删除"
                            val bg = when {
                                isOperator -> KeyOperatorBg
                                isDelete -> KeyDeleteBg
                                else -> KeyDarkBg
                            }
                            Surface(
                                shape = RoundedCornerShape(if (d.isTablet) 14.dp else 10.dp),
                                color = bg,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(d.keyHeight)
                                    .clickable {
                                        when {
                                            isDelete -> onBackspace()
                                            isOperator -> onOperator(key)
                                            else -> onDigit(key)
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        key,
                                        color = Color.White,
                                        fontSize = when {
                                            isDelete -> d.captionFontSize
                                            else -> if (d.isTablet) 24.sp else 20.sp
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
