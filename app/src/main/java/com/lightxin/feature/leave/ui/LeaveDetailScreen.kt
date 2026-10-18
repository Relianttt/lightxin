package com.lightxin.feature.leave.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lightxin.core.designsystem.component.LxCallout
import com.lightxin.core.designsystem.component.LxConfirmSheet
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxFlowStep
import com.lightxin.core.designsystem.component.LxMetaGrid
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPanel
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxStepBar
import com.lightxin.core.designsystem.component.LxStepItem
import com.lightxin.core.designsystem.component.LxStepState
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.feature.leave.data.LeaveDetail
import com.lightxin.feature.leave.domain.LeaveFormField
import com.lightxin.feature.leave.domain.LeaveFormType
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LeaveDetailScreen(
    executionId: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaveDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var withdrawVisible by remember { mutableStateOf(false) }

    LaunchedEffect(executionId) { viewModel.load(executionId) }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(uiState.withdrawn) {
        if (uiState.withdrawn) {
            viewModel.consumeWithdrawn()
            onChanged()
            onBack()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack) {
                if (uiState.canWithdraw) {
                    LxTextAction(
                        text = "撤回",
                        onClick = { withdrawVisible = true },
                        color = LxDanger,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LxSkeletonList(count = 5, modifier = Modifier.padding(padding))
            uiState.error != null -> Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LxErrorState(message = uiState.error.orEmpty(), onRetry = viewModel::retry)
            }
            uiState.detail != null -> LeaveDetailContent(
                detail = uiState.detail!!,
                padding = padding,
                state = uiState,
                onSubmitXjxx = viewModel::submitXjxx,
            )
        }
    }

    if (withdrawVisible) {
        LxConfirmSheet(
            title = "撤回这次申请？",
            message = "撤回后需要重新发起，已提交的材料不会再保留。",
            confirmText = "撤回申请",
            destructive = true,
            onConfirm = {
                withdrawVisible = false
                viewModel.withdraw()
            },
            onDismiss = { withdrawVisible = false },
        )
    }
}

@Composable
private fun LeaveDetailContent(
    detail: LeaveDetail,
    padding: androidx.compose.foundation.layout.PaddingValues,
    state: LeaveDetailUiState,
    onSubmitXjxx: () -> Unit,
) {
    val timeRange = timeRangeOf(detail)
    val reason = leaveReason(detail)
    val attachments = leaveAttachments(detail)
    val infoItems = detailInfoItems(detail)
    val tone = statusTone(detail.statusTxt)

    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
    ) {
        item {
            LxPageHeader(title = leaveDisplayTitle(detail.flowTitle))
            Spacer(Modifier.height(20.dp))
            LxCallout(
                tone = tone,
                title = detail.statusTxt.ifBlank { "处理中" },
                subtitle = buildString {
                    append("已提交 ${formatDetailTimestamp(detail.receiveTime)}")
                    if (isPending(detail.statusTxt)) append(" · 辅导员通常 1 个工作日内处理")
                },
            ) {
                LxStepBar(
                    steps = leaveSteps(detail.statusTxt),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        if (timeRange != null) {
            item {
                LxSectionLabel("请假时间")
                Spacer(Modifier.height(10.dp))
                LxPanel(radius = 12.dp, padding = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeCell(label = "起", value = formatTime(timeRange.first), modifier = Modifier.weight(1f))
                        Text(
                            text = "→",
                            fontSize = 15.sp,
                            color = LxTextFaint,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                        TimeCell(
                            label = "止",
                            value = formatTime(timeRange.second),
                            modifier = Modifier.weight(1f),
                            alignEnd = true,
                        )
                    }
                    val duration = durationText(detail, timeRange)
                    if (duration.isNotBlank()) {
                        Text(
                            text = duration,
                            fontSize = 11.5.sp,
                            color = LxTextMuted,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }

        if (reason.isNotBlank()) {
            item {
                LxSectionLabel("请假事由")
                Text(
                    text = reason,
                    fontSize = 14.5.sp,
                    lineHeight = 26.sp,
                    color = LxText,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(26.dp))
            }
        }

        if (attachments.isNotEmpty()) {
            item {
                LxSectionLabel("证明材料")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    attachments.forEach { name ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(LxTint)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.InsertDriveFile,
                                contentDescription = null,
                                tint = LxTextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                color = LxText,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }

        if (infoItems.isNotEmpty()) {
            item {
                LxSectionLabel("申请信息")
                Spacer(Modifier.height(16.dp))
                LxMetaGrid(infoItems)
            }
        }

        if (detail.statusTxt.contains("待销假")) {
            item {
                XjxxPanel(
                    xjxx = state.xjxx,
                    loaded = state.xjxxLoaded,
                    isWorking = state.isWorking,
                    onSubmit = onSubmitXjxx,
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TimeCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(text = label, fontSize = 10.5.sp, color = LxTextMuted)
        Text(
            text = value,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun XjxxPanel(
    xjxx: JsonObject?,
    loaded: Boolean,
    isWorking: Boolean,
    onSubmit: () -> Unit,
) {
    LxCallout(
        tone = LxTone.Warning,
        title = "销假信息",
        subtitle = when {
            !loaded -> "正在读取销假信息…"
            xjxx == null -> "暂时没有拿到销假信息，可稍后重试或在服务端处理。"
            else -> null
        },
    ) {
        if (xjxx != null) {
            Spacer(Modifier.height(8.dp))
            xjxxLabels.forEach { (key, label) ->
                val value = xjxx.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                if (!value.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = label, fontSize = 12.sp, color = LxTextMuted)
                        Text(text = value, fontSize = 12.sp, color = LxText)
                    }
                }
            }
            val extra = xjxx.entrySet()
                .filter { (key, value) -> key !in xjxxLabels && value.isJsonPrimitive }
                .map { (key, value) -> key to value.asString }
                .filter { (_, value) -> value.length < 40 && value.any { it.isLetter() } }
            extra.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = key, fontSize = 12.sp, color = LxTextMuted)
                    Text(text = value, fontSize = 12.sp, color = LxText)
                }
            }
            if (isWritableXjxx(xjxx)) {
                LxPrimaryButton(
                    text = if (isWorking) "提交中…" else "确认销假",
                    onClick = onSubmit,
                    enabled = !isWorking,
                    modifier = Modifier.padding(top = 14.dp),
                )
            } else {
                Text(
                    text = "销假信息不是可回写的表单结构，仅展示。",
                    fontSize = 11.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun leaveReason(detail: LeaveDetail): String {
    val field = detail.fields.firstOrNull {
        it.type == LeaveFormType.TEXTAREA || it.label.contains("事由") || it.label.contains("原因")
    } ?: return ""
    return displayValue(detail.values.get(field.key))
}

private fun leaveAttachments(detail: LeaveDetail): List<String> =
    detail.fields
        .filter { it.type == LeaveFormType.UPLOAD_FILE || it.type == LeaveFormType.UPLOAD_IMG }
        .flatMap { field ->
            val element = detail.values.get(field.key) ?: return@flatMap emptyList()
            when {
                element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
                    if (item.isJsonObject) {
                        val obj = item.asJsonObject
                        obj.string("fileName").ifBlank { obj.string("name") }.ifBlank { null }
                    } else if (item.isJsonPrimitive) {
                        item.asString
                    } else null
                }
                element.isJsonObject -> listOfNotNull(
                    element.asJsonObject.string("fileName").ifBlank {
                        element.asJsonObject.string("name")
                    }.ifBlank { null },
                )
                else -> emptyList()
            }
        }

private fun timeRangeOf(detail: LeaveDetail): Pair<String, String>? {
    val element = detail.values.get("QJRQ") ?: detail.fields
        .firstOrNull { it.type == LeaveFormType.DATE_RANGE }
        ?.let { detail.values.get(it.key) }
        ?: return null
    if (!element.isJsonArray) return null
    val array = element.asJsonArray
    if (array.size() < 2) return null
    val start = array.get(0)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val end = array.get(1)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    if (start.isBlank() && end.isBlank()) return null
    return start to end
}

private fun detailInfoItems(detail: LeaveDetail): List<Pair<String, String>> {
    val items = mutableListOf<Pair<String, String>>()
    if (detail.flowNumber.isNotBlank()) items += "编号" to detail.flowNumber
    if (detail.receiveTime.isNotBlank()) items += "提交时间" to formatDetailTimestamp(detail.receiveTime)
    detail.fields
        .filter {
            it.type !in setOf(
                LeaveFormType.DATE_RANGE,
                LeaveFormType.TEXTAREA,
                LeaveFormType.UPLOAD_FILE,
                LeaveFormType.UPLOAD_IMG,
                LeaveFormType.FORMULA,
                LeaveFormType.READONLY_TEXT,
            )
        }
        .filter { it.label != "标题" }
        .forEach { field ->
            val value = displayValue(detail.values.get(field.key))
            if (value.isNotBlank() && value.length <= 40) items += field.label to value
        }
    return items.distinctBy { it.first }.take(8)
}

private fun durationText(detail: LeaveDetail, range: Pair<String, String>): String {
    val explicit = detail.values.get("QJSC")?.takeIf { it.isJsonPrimitive }?.asString
    if (!explicit.isNullOrBlank()) return "共 $explicit 小时"
    val start = parseDetailTime(range.first) ?: return ""
    val end = parseDetailTime(range.second) ?: return ""
    val minutes = Duration.between(start, end).toMinutes()
    if (minutes <= 0) return ""
    val days = minutes / (60 * 24)
    val hours = (minutes % (60 * 24)) / 60
    return buildString {
        append("共")
        if (days > 0) append(" $days 天")
        if (hours > 0) append(" $hours 小时")
    }
}

private fun formatTime(raw: String): String {
    val parsed = parseDetailTime(raw) ?: return raw
    val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[parsed.dayOfWeek.value - 1]
    return "%02d/%02d %s %02d:%02d".format(parsed.monthValue, parsed.dayOfMonth, week, parsed.hour, parsed.minute)
}

private fun formatDetailTimestamp(raw: String): String {
    val parsed = parseDetailTime(raw) ?: return raw
    return "%02d/%02d %02d:%02d".format(parsed.monthValue, parsed.dayOfMonth, parsed.hour, parsed.minute)
}

private fun parseDetailTime(raw: String): LocalDateTime? {
    val cleaned = raw.trim()
    return runCatching { LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(cleaned) }.getOrNull()
}

private fun statusTone(status: String): LxTone = when {
    status.contains("驳回") || status.contains("拒绝") -> LxTone.Danger
    status.contains("待") || status.contains("中") -> LxTone.Warning
    status.contains("完成") || status.contains("通过") || status.contains("销假") -> LxTone.Success
    status.contains("撤销") -> LxTone.Neutral
    else -> LxTone.Accent
}

private fun isPending(status: String): Boolean =
    status.contains("待") || status.contains("中")

private fun leaveSteps(status: String): List<LxStepItem> {
    if (status.contains("待销假")) {
        return listOf(
            LxStepItem("已提交", LxStepState.Done),
            LxStepItem("审批通过", LxStepState.Done),
            LxStepItem("待销假", LxStepState.Current),
        )
    }
    val approved = status.contains("通过") || status.contains("完成") || status.contains("销假") || status.contains("备案")
    val rejected = status.contains("驳回") || status.contains("拒绝") || status.contains("撤销")
    val second = when {
        approved -> LxStepState.Done
        rejected -> LxStepState.Todo
        else -> LxStepState.Current
    }
    val third = when {
        approved -> LxStepState.Done
        else -> LxStepState.Todo
    }
    return listOf(
        LxStepItem("已提交", LxStepState.Done),
        LxStepItem("辅导员审批", second),
        LxStepItem("学院备案", third),
    )
}

private fun displayValue(element: JsonElement?): String {
    if (element == null || element.isJsonNull) return ""
    if (element.isJsonPrimitive) return element.asString
    if (element.isJsonArray) {
        val array = element.asJsonArray
        if (array.all { it.isJsonPrimitive }) {
            return array.joinToString(" ~ ") { it.asString }
        }
        return array.mapNotNull { item ->
            when {
                item.isJsonObject -> {
                    val obj = item.asJsonObject
                    obj.string("fileName").ifBlank { obj.string("name") }
                }
                item.isJsonPrimitive -> item.asString
                else -> null
            }
        }.joinToString("、")
    }
    if (element.isJsonObject) {
        val obj = element.asJsonObject
        return obj.string("fileName").ifBlank {
            obj.string("name").ifBlank { obj.string("fileUrl").ifBlank { obj.string("url") } }
        }
    }
    return ""
}

private fun JsonObject.string(key: String): String =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private val xjxxLabels = mapOf(
    "cjScStr" to "已请假时长",
    "isCj" to "是否超假",
    "cjStartTimeStr" to "销假开始",
    "cjEndTimeStr" to "销假结束",
    "cjSc" to "超假时长",
)

private fun isWritableXjxx(obj: JsonObject): Boolean =
    obj.has("QJRQ") || obj.has("dataJson") || obj.has("flowTitle")
