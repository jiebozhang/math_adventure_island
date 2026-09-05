package com.example.ui.theme

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 平板适配：尺寸 token 按 WindowWidthSizeClass 切换
 * - Compact（手机竖屏 <600dp）：紧凑间距 + 小字号
 * - Medium/Expanded（≥600dp）：放大间距 + 大字号，圆角/触控热区也增大
 *
 * 用法：
 * - 顶层 MainActivity 包一层 ProvideDimens(windowSizeClass) { ... }
 * - Composable 内用 Dimens.xxx 即可，老代码（object 字段写法）也兼容
 */
data class DimensImpl(
    /** 是否为平板/宽屏（≥600dp）布局，供 Grid 列数、双栏判定等使用 */
    val isTablet: Boolean,
    val xs: Dp,
    val sm: Dp,
    val md: androidx.compose.ui.unit.Dp,
    val lg: androidx.compose.ui.unit.Dp,
    val xl: androidx.compose.ui.unit.Dp,
    val xxl: androidx.compose.ui.unit.Dp,
    val touchTarget: Dp,
    /** 自定义数字键盘按键高度 */
    val keyHeight: Dp,
    /** 自定义键盘最大宽度；宽屏下不占满整屏，居中显示（Dp.Unspecified 表示不限） */
    val keyboardMaxWidth: Dp,
    /** 图鉴网格列数 */
    val codexColumns: Int,
    /** 闯关页是否走左右双栏（仅"又宽又高"的平板横屏才启用） */
    val isTwoPane: Boolean,
    val cardCornerRadius: Dp,
    val chipCornerRadius: androidx.compose.ui.unit.Dp,
    val bottomBarHeight: androidx.compose.ui.unit.Dp,
    val topBarHeight: androidx.compose.ui.unit.Dp,
    val screenPadding: androidx.compose.ui.unit.Dp,
    val cardPadding: androidx.compose.ui.unit.Dp,
    val cardPaddingSmall: androidx.compose.ui.unit.Dp,
    val titleFontSize: androidx.compose.ui.unit.TextUnit,
    val bodyFontSize: androidx.compose.ui.unit.TextUnit,
    val captionFontSize: androidx.compose.ui.unit.TextUnit,
    val microFontSize: androidx.compose.ui.unit.TextUnit,
    val iconSmall: androidx.compose.ui.unit.Dp,
    val iconMedium: androidx.compose.ui.unit.Dp,
    val iconLarge: androidx.compose.ui.unit.Dp,
    val iconXLarge: androidx.compose.ui.unit.Dp,
)

private val CompactDimens = DimensImpl(
    isTablet = false,
    xs = 4.dp, sm = 8.dp, md = 16.dp, lg = 24.dp, xl = 32.dp, xxl = 48.dp,
    touchTarget = 48.dp, keyHeight = 48.dp,
    keyboardMaxWidth = Dp.Unspecified, codexColumns = 2, isTwoPane = false,
    cardCornerRadius = 16.dp, chipCornerRadius = 20.dp,
    bottomBarHeight = 64.dp, topBarHeight = 56.dp,
    screenPadding = 16.dp, cardPadding = 16.dp, cardPaddingSmall = 12.dp,
    titleFontSize = 18.sp, bodyFontSize = 16.sp, captionFontSize = 12.sp, microFontSize = 11.sp,
    iconSmall = 16.dp, iconMedium = 24.dp, iconLarge = 32.dp, iconXLarge = 48.dp,
)

private val TabletDimens = DimensImpl(
    isTablet = true,
    xs = 6.dp, sm = 12.dp, md = 24.dp, lg = 32.dp, xl = 48.dp, xxl = 72.dp,
    touchTarget = 64.dp, keyHeight = 60.dp,
    keyboardMaxWidth = 560.dp, codexColumns = 3, isTwoPane = true,
    cardCornerRadius = 20.dp, chipCornerRadius = 24.dp,
    bottomBarHeight = 72.dp, topBarHeight = 64.dp,
    screenPadding = 28.dp, cardPadding = 24.dp, cardPaddingSmall = 16.dp,
    titleFontSize = 22.sp, bodyFontSize = 18.sp, captionFontSize = 14.sp, microFontSize = 12.sp,
    iconSmall = 20.dp, iconMedium = 28.dp, iconLarge = 40.dp, iconXLarge = 56.dp,
)

private val LocalDimens = compositionLocalOf { CompactDimens }

@Composable
fun ProvideDimens(windowSizeClass: WindowSizeClass, content: @Composable () -> Unit) {
    // 高度门槛很关键：手机横屏虽然宽（≈730dp → Medium），但高度只有 360dp（Compact），
    // 若只看宽度会误判成平板、套用双栏布局导致内容被挤扁。
    val isTall = windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact
    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact && isTall
    // 双栏更保守：只有真正够宽（≥840dp，Expanded）的平板横屏才启用
    val isTwoPane = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded && isTall
    val dimens = if (isTablet) {
        TabletDimens.copy(isTwoPane = isTwoPane)
    } else {
        CompactDimens
    }
    CompositionLocalProvider(LocalDimens provides dimens) {
        content()
    }
}

/**
 * 顶层访问入口：在 Composable 内写 `Dimens.md` 即可拿到当前 WindowSizeClass 对应的尺寸。
 * 用属性扩展而非 object 字段，确保在 Composition 里跟随 LocalDimens。
 */
object Dimens {
    val current: DimensImpl
        @Composable
        @ReadOnlyComposable
        get() = LocalDimens.current

    // 兼容老代码（静态访问 CompactDimens，无 Composable 上下文也能用，比如工具/常量场景）。
    // 新代码请用 Dimens.current（Composable 内）或 LocalDimens.current。
    val xs get() = CompactDimens.xs
    val sm get() = CompactDimens.sm
    val md get() = CompactDimens.md
    val lg get() = CompactDimens.lg
    val xl get() = CompactDimens.xl
    val xxl get() = CompactDimens.xxl
    val touchTarget get() = CompactDimens.touchTarget
    val keyHeight get() = CompactDimens.keyHeight
    val keyboardMaxWidth get() = CompactDimens.keyboardMaxWidth
    val codexColumns get() = CompactDimens.codexColumns
    val cardCornerRadius get() = CompactDimens.cardCornerRadius
    val chipCornerRadius get() = CompactDimens.chipCornerRadius
    val bottomBarHeight get() = CompactDimens.bottomBarHeight
    val topBarHeight get() = CompactDimens.topBarHeight
    val screenPadding get() = CompactDimens.screenPadding
    val cardPadding get() = CompactDimens.cardPadding
    val cardPaddingSmall get() = CompactDimens.cardPaddingSmall
    val titleFontSize get() = CompactDimens.titleFontSize
    val bodyFontSize get() = CompactDimens.bodyFontSize
    val captionFontSize get() = CompactDimens.captionFontSize
    val microFontSize get() = CompactDimens.microFontSize
    val iconSmall get() = CompactDimens.iconSmall
    val iconMedium get() = CompactDimens.iconMedium
    val iconLarge get() = CompactDimens.iconLarge
    val iconXLarge get() = CompactDimens.iconXLarge
}
