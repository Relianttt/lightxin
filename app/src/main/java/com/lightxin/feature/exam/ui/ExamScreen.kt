package com.lightxin.feature.exam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.*
import com.lightxin.core.designsystem.theme.*
import com.lightxin.feature.exam.domain.ExamScore
import com.lightxin.feature.exam.domain.examTermOptions
import com.lightxin.feature.exam.domain.summarize

@Composable
fun ExamScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showTerms by remember { mutableStateOf(false) }
    var staggerDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.scores.isNotEmpty()) {
        if (state.scores.isNotEmpty() && !staggerDone) {
            kotlinx.coroutines.delay(LxMotion.StaggerWindowMs.toLong())
            staggerDone = true
        }
    }
    val playStagger = !staggerDone
    val semesters = listOf("1" to "第一学期", "2" to "第二学期")
    val termOptions = remember(state.schoolYears) { examTermOptions(state.schoolYears)}
    val yearLabel = state.schoolYears.find { it.value == state.selectedYear }?.display ?: state.selectedYear
    val semesterLabel = semesters.find { it.first == state.selectedSemester }?.second.orEmpty()
    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = { LxNavBar(onBack = onBack, title = "考试成绩", collapsed = listState.lxHeaderCollapsed()) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item("header") { LxPageHeader("考试成绩", modifier = Modifier.padding(horizontal = 24.dp)) }
            if (state.selectedYear.isNotBlank()) {
                item("term") {
                    Row(
                        modifier = Modifier.padding(start = 24.dp, top = 14.dp)
                            .height(36.dp).clip(RoundedCornerShape(LxRadiusSm))
                            .background(LxTint).lxPressable(onClick = { showTerms = true })
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$yearLabel $semesterLabel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LxText)
                        Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.padding(start = 6.dp).size(18.dp), tint = LxTextMuted)
                    }
                }
            }
            when {
                state.isLoading || state.isScoresLoading -> item("loading") { LxLoading() }
                state.error != null -> item("error") {
                    LxErrorState(state.error!!, viewModel::retry, Modifier.padding(top = 24.dp))
                }
                else -> {
                    item("hero") { ExamHero(state.scores) }
                    item("section") {
                        LxSectionLabel("成绩记录 · ${state.scores.size}", Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 4.dp))
                    }
                    if (state.scores.isEmpty()) item("empty") {
                        Text("该学期暂无成绩记录", fontSize = 13.sp, color = LxTextMuted, modifier = Modifier.padding(24.dp))
                    }
                    itemsIndexed(state.scores, key = { index, score -> "${score.courseCode}-$index" }) { index, score ->
                        ScoreRow(
                            score = score,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = LxMotion.enter(),
                                    placementSpec = LxMotion.placement(),
                                    fadeOutSpec = LxMotion.exit(),
                                )
                                .lxStaggerEnter(index = index + 1, play = playStagger),
                        )
                    }
                }
            }
        }
    }
    if (showTerms) {
        LxOptionSheet(
            title = "选择学期",
            options = termOptions.map { it.label to it.value },
            selectedValue = "${state.selectedYear}|${state.selectedSemester}",
            onSelect = { _, value ->
                val parts = value.split('|', limit = 2)
                viewModel.onTermSelected(parts[0], parts[1])
                showTerms = false
            },
            onDismiss = { showTerms = false },
        )
    }
}

@Composable
private fun ExamHero(scores: List<ExamScore>) {
    val summary = remember(scores) { summarize(scores) }
    val gpa by rememberLxCountUp(summary.weightedGpa?.toFloat() ?: 0f)
    val credit by rememberLxCountUp(summary.totalCredit.toFloat())
    LxPanel(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp).fillMaxWidth(), padding = 20.dp) {
        Row(Modifier.fillMaxWidth()) {
            ExamStat(
                summary.weightedGpa?.let { "%.2f".format(gpa) } ?: "--",
                "学期绩点",
                Modifier.weight(1f),
            )
            ExamStat("%.1f".format(credit), "总学分", Modifier.weight(1f))
            ExamStat(summary.courseCount.toString(), "课程", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExamStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = LxText, style = TextStyle(fontFeatureSettings = "tnum"))
        Text(label, fontSize = 11.sp, color = LxTextMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ScoreRow(score: ExamScore, modifier: Modifier = Modifier) {
    val failed = score.score.toDoubleOrNull()?.let { it < 60 } == true || "不及格" in score.score || "不合格" in score.score
    val meta = listOfNotNull(
        score.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
        score.gpa.takeIf { it.isNotBlank() }?.let { "绩点 $it" },
        score.category.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(score.courseName, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = LxText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, fontSize = 12.5.sp, color = LxTextMuted, modifier = Modifier.padding(top = 5.dp))
        }
        Text(score.score, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (failed) LxDanger else LxText,
            style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.padding(start = 12.dp))
    }
}
