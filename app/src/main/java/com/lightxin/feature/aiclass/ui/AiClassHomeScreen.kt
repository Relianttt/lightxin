package com.lightxin.feature.aiclass.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxFloatingActionButton
import com.lightxin.core.designsystem.component.LxProgressIndicator
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.aiclass.domain.AiCourse
import com.lightxin.feature.aiclass.domain.displayName
import com.lightxin.feature.aiclass.domain.studentCountText

@Composable
fun AiClassHomeScreen(
    onBack: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenCourseDetail: (classId: String) -> Unit,
    onOpenWorkingDetail: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AiClassViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val error = uiState.error

    LaunchedEffect(uiState.signResult) {
        uiState.signResult?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSignResult()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "AI课堂", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isLoading && error == null) {
                LxFloatingActionButton(
                    onClick = onOpenScan,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "扫码签到",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { padding ->
        when {
            uiState.isLoading || uiState.isSsoInProgress -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LxProgressIndicator()
                        if (uiState.isSsoInProgress) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在连接 AI课堂...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            error != null -> LxError(
                message = error,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )

            else -> AiClassContent(
                uiState = uiState,
                onSubmitSignCode = viewModel::submitSignCode,
                onOpenCourseDetail = onOpenCourseDetail,
                onOpenWorkingDetail = onOpenWorkingDetail,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AiClassContent(
    uiState: AiClassUiState,
    onSubmitSignCode: (String) -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    onOpenWorkingDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 当前课堂状态
        uiState.workingRecord?.let { record ->
            item(key = "working") {
                WorkingClassCard(record = record, onClick = onOpenWorkingDetail)
            }
        }

        // 快捷签到
        item(key = "sign_in") {
            SignInCard(
                isSigningIn = uiState.isSigningIn,
                hasWorkingClass = uiState.workingRecord != null,
                onSubmit = onSubmitSignCode,
            )
        }

        // 课程列表标题
        if (uiState.courses.isNotEmpty()) {
            item(key = "courses_title") {
                Text(
                    text = "我的课程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }

        // 课程列表
        items(uiState.courses, key = { it.stableId }) { course ->
            CourseCard(
                course = course,
                onClick = { onOpenCourseDetail(course.stableId) },
            )
        }
    }
}

@Composable
private fun WorkingClassCard(
    record: com.lightxin.feature.aiclass.domain.AiWorkingRecord,
    onClick: () -> Unit,
) {
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Class,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在上课",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = record.courseName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (record.courseItemName.isNotBlank()) {
                Text(
                    text = record.courseItemName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SignInCard(
    isSigningIn: Boolean,
    hasWorkingClass: Boolean,
    onSubmit: (String) -> Unit,
) {
    var signCode by rememberSaveable { mutableStateOf("") }

    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "数字码签到",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LxTextField(
                value = signCode,
                onValueChange = { if (it.length <= 6) signCode = it },
                label = "输入6位签到码",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (signCode.length == 6 && !isSigningIn) onSubmit(signCode)
                    },
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            LxButton(
                text = if (isSigningIn) "签到中..." else "签到",
                onClick = { onSubmit(signCode) },
                enabled = signCode.length == 6 && !isSigningIn && hasWorkingClass,
            )

            if (!hasWorkingClass) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前没有正在进行的课堂",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: AiCourse,
    onClick: () -> Unit,
) {
    LxCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${course.teacherName} · ${course.studentCountText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看课程详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
