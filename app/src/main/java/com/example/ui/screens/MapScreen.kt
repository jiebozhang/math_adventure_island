package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*

private data class DomainCardUi(
    val id: String, val name: String, val emoji: String, val color: Color
)

private val DOMAINS = listOf(
    DomainCardUi("calc", "计算城堡", "🏰", CardBlue),
    DomainCardUi("figure", "图形山谷", "⛰️", CardYellow),
    DomainCardUi("unit", "单位王国", "⚖️", CardPink),
    DomainCardUi("wisdom", "智慧森林", "🌲", CardGreen),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    userSettings: UserSettings,
    allQuestions: List<Question>,
    masteredQuestions: List<MasteredQuestion>,
    onStartQuest: (Question) -> Unit,
    modifier: Modifier = Modifier
) {
    val masteredIds = remember(masteredQuestions) { masteredQuestions.map { it.questionId }.toSet() }
    val allTopics = remember { Constants.KNOWLEDGE_MAP }

    // 选中册别：默认 3 年级上册
    var selectedGrade by remember { mutableIntStateOf(3) }
    var selectedSemester by remember { mutableIntStateOf(1) }
    // 选中领域（空 = 全部）
    var selectedStrand by remember { mutableStateOf<String?>(null) }
    // 选中关卡（用于展开题目）
    var selectedTopicId by remember { mutableStateOf<String?>(null) }
    // book-pill dropdown 状态
    var bookMenuExpanded by remember { mutableStateOf(false) }

    val topicsInBook = allTopics.filter { it.grade == selectedGrade && it.semester == selectedSemester }
    val topicsAfterStrand = if (selectedStrand == null) topicsInBook else topicsInBook.filter { it.strand == selectedStrand }

    // 领域卡统计（基于册别）
    val domainStats: Map<String, Pair<Int, Int>> = remember(topicsInBook, allQuestions, masteredIds, selectedGrade, selectedSemester) {
        DOMAINS.associate { dom ->
            val dTopics = topicsInBook.filter { it.strand == dom.id }
            val dQs = allQuestions.filter { q -> dTopics.any { it.id == q.topicId } }
            val dMastered = dQs.count { it.id in masteredIds }
            dom.id to (dQs.size to dMastered)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 页头：标题 + book-pill 册别选择器 ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("冒险地图", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
                // book-pill
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceWhite,
                        shadowElevation = 1.dp,
                        modifier = Modifier.clickable { bookMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedGrade}年级${if (selectedSemester == 1) "上" else "下"}册",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ExpandMore, contentDescription = null,
                                tint = MutedGray, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = bookMenuExpanded,
                        onDismissRequest = { bookMenuExpanded = false }
                    ) {
                        (1..6).forEach { g ->
                            listOf(1 to "上", 2 to "下").forEach { (sem, label) ->
                                DropdownMenuItem(
                                    text = { Text("${g}年级${label}册", fontSize = 14.sp) },
                                    onClick = {
                                        selectedGrade = g; selectedSemester = sem
                                        selectedStrand = null; selectedTopicId = null
                                        bookMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 四大领域卡（手机 2×2；平板横屏一行四列，避免大片留白）──
        item {
            val d = Dimens.current
            val groups = if (d.isTablet) listOf(DOMAINS) else listOf(DOMAINS.take(2), DOMAINS.drop(2))
            Column(verticalArrangement = Arrangement.spacedBy(if (d.isTablet) 16.dp else 8.dp)) {
                groups.forEach { group ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (d.isTablet) 16.dp else 8.dp)) {
                        group.forEach { dom ->
                            val (total, mastered) = domainStats[dom.id] ?: (0 to 0)
                            val selected = selectedStrand == dom.id
                            DomainCard(
                                domain = dom, totalQuestions = total, masteredQuestions = mastered,
                                selected = selected,
                                onClick = { selectedStrand = if (selected) null else dom.id; selectedTopicId = null },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ── 当前航线（该册别下选中领域的关卡进度） ──
        if (topicsAfterStrand.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp), color = SurfaceWhite, shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val strandLabel = selectedStrand?.let { V15Data.STRANDS[it]?.label } ?: "全部领域"
                            Text("当前航线 · $strandLabel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                            val totalQs = allQuestions.count { q -> topicsAfterStrand.any { it.id == q.topicId } }
                            val masteredQs = allQuestions.count { q -> topicsAfterStrand.any { it.id == q.topicId } && q.id in masteredIds }
                            Text("$masteredQs / $totalQs", fontSize = 12.sp, color = MutedGray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // 关卡进度节点（最多展示 8 个）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            topicsAfterStrand.take(8).forEachIndexed { idx, t ->
                                val tQs = allQuestions.filter { it.topicId == t.id }
                                val tDone = tQs.isNotEmpty() && tQs.all { it.id in masteredIds }
                                val tSome = tQs.any { it.id in masteredIds }
                                Box(
                                    modifier = Modifier
                                        .size(if (tDone || tSome) 18.dp else 16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                tDone -> SuccessGreen
                                                tSome -> AccentAmber
                                                else -> MutedGray.copy(alpha = 0.3f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (tDone) {
                                        Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (idx < topicsAfterStrand.take(8).lastIndex) {
                                    Box(
                                        modifier = Modifier.weight(1f).height(2.dp)
                                            .background(if (tDone) SuccessGreen else MutedGray.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 本册关卡（按领域筛选后的 topic 列表） ──
        item {
            Text(
                if (selectedStrand == null) "本册关卡（全部 ${topicsAfterStrand.size} 关）"
                else "${V15Data.STRANDS[selectedStrand]?.label} · ${topicsAfterStrand.size} 关",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark
            )
        }
        items(topicsAfterStrand, key = { it.id }) { topic ->
            val topicQs = allQuestions.filter { it.topicId == topic.id }
            val topicMastered = topicQs.count { it.id in masteredIds }
            val expanded = selectedTopicId == topic.id
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (expanded) CardBlue.copy(alpha = 0.08f) else SurfaceWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clickable { selectedTopicId = if (expanded) null else topic.id }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (topicMastered == topicQs.size && topicQs.isNotEmpty()) "✓" else "${topic.unitOrder}",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (topicMastered == topicQs.size && topicQs.isNotEmpty()) SuccessGreen else NavbarBlue)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(topic.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${topic.unitName} · ${V15Data.THINKING_TAGS[topic.thinkingTag]?.name ?: "综合"}思想 · ${topicQs.size} 题",
                                fontSize = 11.sp, color = MutedGray, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("$topicMastered/${topicQs.size}", fontSize = 12.sp,
                            color = if (topicMastered == topicQs.size && topicQs.isNotEmpty()) SuccessGreen else MutedGray,
                            fontWeight = FontWeight.SemiBold)
                    }
                    // 展开：题目列表
                    if (expanded && topicQs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            topicQs.forEach { q ->
                                val mastered = q.id in masteredIds
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceWhite,
                                    modifier = Modifier.fillMaxWidth().clickable { onStartQuest(q) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(20.dp).clip(CircleShape)
                                                .background(if (mastered) SuccessGreen else AccentAmber),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (mastered) "✓" else "?", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Text(q.story.ifBlank { q.text }, fontSize = 12.sp, color = TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowForward, contentDescription = "开始", tint = MutedGray, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (topicsAfterStrand.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceWhite, modifier = Modifier.fillMaxWidth()) {
                    Text("${selectedGrade}年级${if (selectedSemester == 1) "上" else "下"}册暂无${V15Data.STRANDS[selectedStrand]?.label ?: ""}题目",
                        fontSize = 13.sp, color = MutedGray, modifier = Modifier.padding(20.dp))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun DomainCard(
    domain: DomainCardUi,
    totalQuestions: Int,
    masteredQuestions: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = Dimens.current
    Surface(
        shape = RoundedCornerShape(d.cardCornerRadius),
        color = if (selected) domain.color else domain.color.copy(alpha = 0.6f),
        shadowElevation = if (selected) 3.dp else 1.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(if (d.isTablet) 20.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(d.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(domain.emoji, fontSize = if (d.isTablet) 30.sp else 22.sp)
                if (selected) Text("✓", fontSize = if (d.isTablet) 18.sp else 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            }
            Text(domain.name, fontSize = d.bodyFontSize, fontWeight = FontWeight.Bold, color = TextDark)
            Text("$masteredQuestions/$totalQuestions 题", fontSize = d.microFontSize, color = TextDark.copy(alpha = 0.7f))
        }
    }
}