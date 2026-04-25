package com.lightxin.feature.aiclass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDetailRow
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxSuccess
import com.lightxin.feature.aiclass.domain.AiCourse
import com.lightxin.feature.aiclass.domain.AiQuiz
import com.lightxin.feature.aiclass.domain.displayName
import com.lightxin.feature.aiclass.domain.studentCountText

@Composable
fun AiClassCourseDetailScreen(
    classId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiClassViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(classId) {
        viewModel.openCourseDetail(classId)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "课程详情", onBack = onBack) },
    ) { padding ->
        val course = uiState.selectedCourse
        when {
            course == null && uiState.isQuizLoading -> LxLoading(modifier = Modifier.padding(padding))
            course == null -> LxError(
                message = uiState.quizError ?: "课程信息不存在",
                onRetry = { viewModel.openCourseDetail(classId) },
                modifier = Modifier.padding(padding),
            )
            else -> AiClassCourseDetailContent(
                course = course,
                quizList = uiState.quizList,
                isQuizLoading = uiState.isQuizLoading,
                quizError = uiState.quizError,
                onRetryQuiz = viewModel::retryQuiz,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AiClassCourseDetailContent(
    course: AiCourse,
    quizList: List<AiQuiz>,
    isQuizLoading: Boolean,
    quizError: String?,
    onRetryQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "course_summary") {
            CourseSummaryCard(course = course)
        }

        item(key = "quiz_title") {
            Text(
                text = "测验",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        when {
            isQuizLoading -> {
                item(key = "quiz_loading") {
                    LxLoading()
                }
            }

            quizError != null -> {
                item(key = "quiz_error") {
                    LxError(
                        message = quizError,
                        onRetry = onRetryQuiz,
                    )
                }
            }

            quizList.isEmpty() -> {
                item(key = "quiz_empty") {
                    EmptyQuizCard()
                }
            }

            else -> {
                items(quizList, key = { quizItemKey(it) }) { quiz ->
                    QuizCard(quiz = quiz)
                }
            }
        }
    }
}

@Composable
private fun CourseSummaryCard(course: AiCourse) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = course.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "这里先展示当前版本已接通的课程基础信息与测验列表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LxDetailRow(label = "教师", value = course.teacherName)
            LxDetailRow(label = "人数", value = course.studentCountText())
            if (course.typeName.isNotBlank()) {
                LxDetailRow(label = "类型", value = course.typeName)
            }
            LxDetailRow(label = "课程ID", value = course.courseId)
            LxDetailRow(label = "教学班ID", value = course.teachClassId, showDivider = false)
        }
    }
}

@Composable
private fun EmptyQuizCard() {
    LxCard {
        Text(
            text = "当前课程暂无测验",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun QuizCard(quiz: AiQuiz) {
    LxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quiz.title.ifBlank { "未命名测验" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildQuizMeta(quiz),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            QuizStatusBadge(isCommitted = quiz.isCommitted)
        }
    }
}

@Composable
private fun QuizStatusBadge(isCommitted: Boolean) {
    val color = if (isCommitted) LxSuccess else MaterialTheme.colorScheme.secondary
    val text = if (isCommitted) "已提交" else "未提交"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

private fun buildQuizMeta(quiz: AiQuiz): String {
    val parts = buildList {
        when {
            quiz.publishDateTime.isNotBlank() -> add(quiz.publishDateTime)
            quiz.publishTime.isNotBlank() -> add(quiz.publishTime)
        }
        if (quiz.publishWeek.isNotBlank()) {
            add(quiz.publishWeek)
        }
        quiz.answerDurationMinutes?.takeIf { it > 0 }?.let { add("${it}分钟") }
    }
    return parts.joinToString(" · ").ifBlank { "FIF 测验" }
}

private fun quizItemKey(quiz: AiQuiz): String {
    return quiz.id.ifBlank { "${quiz.title}|${quiz.publishDateTime}|${quiz.publishTime}|${quiz.publishWeek}" }
}
