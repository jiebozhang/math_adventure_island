package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MutedGray
import com.example.ui.theme.TextDark
import com.example.util.PinyinUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinyinText(
    text: String,
    showPinyin: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    color: Color = TextDark
) {
    if (!showPinyin) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            color = color,
            modifier = modifier
        )
        return
    }

    // Split text by lines or render character flow
    val lines = text.split("\n")
    Column(modifier = modifier) {
        lines.forEach { line ->
            // 原实现用单行 Row 逐字排列：内容超出父容器宽度时会被裁剪、不换行、
            // 也不可横向滚动（题干被截断的根因）。改用 FlowRow 让"汉字+拼音"这一组
            // 作为一个 item 在超出宽度时整体折到下一行。
            // 注意：FlowRow 在 foundation 1.7.x 仍是 @ExperimentalLayoutApi，需配合函数上
            // 的 @OptIn 使用；compose-bom 2024.09.00 已内置，无需额外依赖。
            // 升级到已稳定的 foundation 版本后可将 @OptIn 一并移除。
            FlowRow(
                modifier = Modifier.padding(vertical = 2.dp),
                // 字间距：沿用每个字符的 padding(horizontal = 1.dp)，折行后与原先单行一致
                horizontalArrangement = Arrangement.Start,
                // 行间距：原先每行 Row 上下各 padding 2.dp，相邻两行之间实际为 4.dp，
                // 用 spacedBy(4.dp) 让折行后的行间距与原先一致
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 底部对齐：原 Row 的 verticalAlignment = Alignment.Bottom 让纯字符与
                // "汉字+拼音"的汉字底边对齐；FlowRow 1.7.x 没有对应参数，改用
                // RowScope.align(Alignment.Bottom)（FlowRowScope 继承自 RowScope）逐项对齐。
                // TODO: 连续非中文字符目前逐字符拆分，多位数字（如 12、300）可能被拆到两行。
                // 当前题库尚未出现多位数字；若后续题库出现，需改为按词分组（合并连续非中文项）。
                line.forEach { char ->
                    val isChinese = PinyinUtils.isChinese(char)
                    val pinyin = if (isChinese) PinyinUtils.getPinyin(char) else ""

                    if (isChinese && pinyin.isNotBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 1.dp).align(Alignment.Bottom)
                        ) {
                            Text(
                                text = pinyin,
                                fontSize = (fontSize.value * 0.6).sp,
                                color = MutedGray,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = char.toString(),
                                fontSize = fontSize,
                                color = color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = char.toString(),
                            fontSize = fontSize,
                            color = color,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 1.dp).align(Alignment.Bottom)
                        )
                    }
                }
            }
        }
    }
}
