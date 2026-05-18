package com.lightxin.feature.aiclass.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.aiclass.domain.AiStudentWork

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHomeworkDetailScreen(
    cwId: String,
    teachClassId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiHomeworkDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(teachClassId) {
        viewModel.load(teachClassId)
    }

    LaunchedEffect(uiState.submitResult) {
        uiState.submitResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSubmitResult()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "作业详情", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.detail == null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            else -> HomeworkDetailContent(
                uiState = uiState,
                onSubmitClick = viewModel::showSubmitSheet,
                onLoadMore = viewModel::loadMoreWorks,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (uiState.showSubmitSheet) {
        SubmitBottomSheet(
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissSubmitSheet,
            onSubmit = viewModel::submitHomework,
        )
    }
}

@Composable
private fun HomeworkDetailContent(
    uiState: AiHomeworkDetailUiState,
    onSubmitClick: () -> Unit,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val detail = uiState.detail
    val listState = rememberLazyListState()
    val reachedBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(
        reachedBottom,
        uiState.hasMoreWorks,
        uiState.isWorksLoadingMore,
        uiState.studentWorks.size,
    ) {
        if (reachedBottom && uiState.hasMoreWorks && !uiState.isWorksLoadingMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (detail != null) {
            item(key = "header") {
                LxCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "教师: ${detail.teacherName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "截止: ${detail.deadline}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 作业题目（HTML渲染）
            if (detail.htmlContent.isNotBlank()) {
                item(key = "content") {
                    LxCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "作业要求",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = AnnotatedString.fromHtml(detail.htmlContent),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // 提交按钮（仅未截止时显示）
            if (!isDeadlinePassed(detail.deadline)) {
                item(key = "submit_btn") {
                    Button(
                        onClick = onSubmitClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("提交作业")
                    }
                }
            }

            // 学生提交列表
            if (uiState.studentWorks.isNotEmpty()) {
                item(key = "works_title") {
                    Text(
                        text = "提交列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                items(uiState.studentWorks, key = { it.stuCwId }) { work ->
                    StudentWorkCard(work)
                }

                if (uiState.isWorksLoadingMore) {
                    item(key = "works_loading_more") {
                        LxLoading()
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentWorkCard(work: AiStudentWork) {
    LxCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = work.studentName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (work.score.isNotBlank() && work.score != "-") {
                    Text(
                        text = "${work.score}分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (work.showContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = work.showContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = work.submitTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubmitBottomSheet(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "提交作业",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text("请输入作业内容...") },
                enabled = !isSubmitting,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSubmit(text) },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank() && !isSubmitting,
            ) {
                Text(if (isSubmitting) "提交中..." else "提交")
            }
        }
    }
}

/** 判断截止时间是否已过。格式: "2026/05/31 23:00" 或 "2026-05-31 23:00" */
private fun isDeadlinePassed(deadline: String): Boolean {
    if (deadline.isBlank()) return false // 无截止时间则允许提交
    return try {
        val normalized = deadline.replace("/", "-")
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val deadlineTime = format.parse(normalized) ?: return false
        System.currentTimeMillis() > deadlineTime.time
    } catch (_: Exception) {
        false // 解析失败默认允许提交
    }
}
