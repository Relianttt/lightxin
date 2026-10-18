package com.lightxin.feature.leave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lightxin.core.designsystem.component.LxCallout
import com.lightxin.core.designsystem.component.LxConfirmSheet
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxGhostButton
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
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.feature.leave.data.LeaveDetail
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
    var checkInVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

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

    val title = uiState.detail?.let(::leaveDetailTitle) ?: "请假详情"

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = { LxNavBar(onBack = onBack, title = title, collapsed = collapsed) },
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
                state = uiState,
                padding = padding,
                listState = listState,
                onSubmitCheckIn = { checkInVisible = true },
                onWithdraw = { withdrawVisible = true },
            )
        }
    }

    if (checkInVisible) {
        LxConfirmSheet(
            title = "提交销假？",
            message = "销假后本次请假流程结束；已超假的部分会进入辅导员审批。",
            confirmText = "提交销假",
            destructive = false,
            onConfirm = {
                checkInVisible = false
                viewModel.submitCheckIn()
            },
            onDismiss = { checkInVisible = false },
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaveDetailContent(
    detail: LeaveDetail,
    state: LeaveDetailUiState,
    padding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSubmitCheckIn: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val timeRange = timeRangeOf(detail)
    val reason = leaveReason(detail)
    val attachments = leaveAttachments(detail)
    val infoItems = detailInfoItems(detail)
    val tone = statusTone(detail.statusTxt)
    val waitingCheckIn = detail.statusTxt.contains("待销假")

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
    ) {
        item {
            LxPageHeader(title = leaveDetailTitle(detail))
            Spacer(Modifier.height(16.dp))
            LxCallout(
                tone = tone,
                title = detail.statusTxt.ifBlank { "处理中" },
                subtitle = buildString {
                    append("已提交 ${formatDetailTimestamp(detail.receiveTime)}")
                    statusHint(detail.statusTxt)?.let { append(" · $it") }
                },
            ) {
                LxStepBar(
                    steps = leaveSteps(detail.statusTxt),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Spacer(Modifier.height(if (waitingCheckIn) 14.dp else 26.dp))
        }

        // 待销假时最要紧的是“超没超假、超了多久”，放在状态块下面，不再沉到页面最底。
        if (waitingCheckIn) {
            item {
                CheckInPanel(
                    xjxx = state.xjxx,
                    loaded = state.xjxxLoaded,
                    isWorking = state.isWorking,
                    canSubmit = state.ruTaskNodeId.isNotBlank(),
                    onSubmit = onSubmitCheckIn,
                )
                Spacer(Modifier.height(26.dp))
            }
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp),
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

        if (state.canWithdraw) {
            item {
                LxGhostButton(
                    text = "撤回申请",
                    onClick = onWithdraw,
                    contentColor = LxDanger,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
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

/**
 * 销假块：服务端 getXjxx.do 只给展示信息（cjScStr 等），真正的动作是 agree.do 同意这个待办节点，
 * 所以这里做“读信息 + 提交销假”，不再假装它是可回写表单。
 */
@Composable
private fun CheckInPanel(
    xjxx: JsonObject?,
    loaded: Boolean,
    isWorking: Boolean,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
) {
    val overstay = xjxx?.let(::overstaySummary)
    val over = xjxx?.let { it.string("isCj") == "是" }
    LxCallout(
        tone = if (over == true) LxTone.Danger else LxTone.Warning,
        title = when {
            !loaded -> "销假"
            xjxx == null -> "销假"
            over == true -> overstay?.headline ?: "已超假"
            else -> "待销假"
        },
        subtitle = when {
            !loaded -> "正在读取销假信息…"
            xjxx == null -> "没有拿到销假信息，返回列表刷新后再进。"
            over == true -> "超假部分需要辅导员审批，提交后留意审批结果。"
            else -> "未超假，提交销假后会自动通过。"
        },
    ) {
        if (xjxx != null) {
            overstay?.let { summary ->
                Spacer(Modifier.height(10.dp))
                summary.rows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = label, fontSize = 12.5.sp, color = LxTextMuted)
                        Text(text = value, fontSize = 12.5.sp, color = LxText)
                    }
                }
            }
            when {
                isWorking -> LxPrimaryButton(
                    text = "提交中…",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.padding(top = 14.dp),
                )
                canSubmit -> LxPrimaryButton(
                    text = "提交销假",
                    onClick = onSubmit,
                    modifier = Modifier.padding(top = 14.dp),
                )
                else -> Text(
                    text = "销假入口暂不可用，返回列表刷新后再进。",
                    fontSize = 12.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private data class OverstaySummary(val headline: String, val rows: List<Pair<String, String>>)

/** cjSc 是小时数（3342 → 139 天）；cjScStr 有值优先用原文，避免再换算一次。 */
private fun overstaySummary(xjxx: JsonObject): OverstaySummary {
    val hours = xjxx.get("cjSc")?.takeIf { it.isJsonPrimitive }?.let {
        runCatching { it.asDouble }.getOrNull()
    }
    val raw = xjxx.string("cjScStr").ifBlank {
        hours?.let { "${trimNumber(it)} 小时" }.orEmpty()
    }
    val days = hours?.let { it / 24 }?.toInt()
    val headline = when {
        days != null && days >= 1 -> "已超假 $days 天"
        raw.isNotBlank() -> "已超假 $raw"
        else -> "已超假"
    }
    val rows = buildList {
        val start = xjxx.string("cjStartTimeStr")
        val end = xjxx.string("cjEndTimeStr")
        if (start.isNotBlank() || end.isNotBlank()) {
            add("超假起止" to "${formatTime(start)} ~ ${formatTime(end)}")
        }
        if (raw.isNotBlank()) add("超假时长" to raw)
    }
    return OverstaySummary(headline = headline, rows = rows)
}

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

/** flowTitle 里不一定带类型，退回到详情字段里的“请假类型”。 */
private fun leaveDetailTitle(detail: LeaveDetail): String {
    val fromTitle = leaveDisplayTitle(detail.flowTitle)
    if (fromTitle != "学生请假申请") return fromTitle
    val type = detail.fields.firstOrNull { it.label.contains("类型") }
        ?.let { displayValue(detail.values.get(it.key)) }
        .orEmpty()
    return type.takeIf { it.isNotBlank() }?.let { "$it 申请" } ?: fromTitle
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
    return items.distinctBy { it.first }.take(10)
}

private fun durationText(detail: LeaveDetail, range: Pair<String, String>): String {
    val explicit = detail.values.get("QJSC")?.takeIf { it.isJsonPrimitive }?.asString
    if (!explicit.isNullOrBlank()) return "共 $explicit 小时"
    val start = parseDetailTime(range.first) ?: return ""
    val end = parseDetailTime(range.second) ?: return ""
    val minutes = Duration.between(start, end).toMinutes()
    if (minutes <= 0) return ""
    val dateOnly = !range.first.contains(":") && !range.second.contains(":")
    val days = minutes / (60 * 24) + if (dateOnly) 1 else 0
    val hours = (minutes % (60 * 24)) / 60
    return buildString {
        append("共")
        if (days > 0) append(" $days 天")
        if (!dateOnly && hours > 0) append(" $hours 小时")
    }
}

private fun formatTime(raw: String): String {
    val parsed = parseDetailTime(raw) ?: return raw
    val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[parsed.dayOfWeek.value - 1]
    // 老数据/PC 端用的是纯日期，没有具体时刻，别补一个假的 00:00。
    return if (raw.contains(":")) {
        "%02d/%02d %s %02d:%02d".format(parsed.monthValue, parsed.dayOfMonth, week, parsed.hour, parsed.minute)
    } else {
        "%02d/%02d %s".format(parsed.monthValue, parsed.dayOfMonth, week)
    }
}

private fun formatDetailTimestamp(raw: String): String {
    val parsed = parseDetailTime(raw) ?: return raw
    val prefix = if (parsed.year == LocalDateTime.now().year) "" else "${parsed.year}/"
    return "%s%02d/%02d %02d:%02d".format(prefix, parsed.monthValue, parsed.dayOfMonth, parsed.hour, parsed.minute)
}

private fun parseDetailTime(raw: String): LocalDateTime? {
    val cleaned = raw.trim()
    return runCatching { LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(cleaned) }.getOrNull()
        ?: runCatching {
            java.time.LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()
        }.getOrNull()
        ?: runCatching {
            java.time.LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyy/MM/dd")).atStartOfDay()
        }.getOrNull()
}

private fun statusTone(status: String): LxTone = when {
    status.contains("驳回") || status.contains("拒绝") -> LxTone.Danger
    status.contains("待") || status.contains("审批中") -> LxTone.Warning
    status.contains("完成") || status.contains("完结") || status.contains("通过") ||
        status.contains("销假") || status.contains("休假") -> LxTone.Success
    status.contains("撤销") || status.contains("撤回") -> LxTone.Neutral
    else -> LxTone.Accent
}

/** 只有真正等待审批时才提“1 个工作日”；待销假/销假审批中各有自己的下一步。 */
private fun statusHint(status: String): String? = when {
    status.contains("待销假") -> "审批已通过，请按时销假"
    status.contains("销假审批") -> "销假申请已提交，等待处理"
    status.contains("销假逾期") -> "销假已逾期，尽快提交销假"
    status.contains("待") || status.contains("审批中") -> "辅导员通常 1 个工作日内处理"
    status.contains("通过") || status.contains("休假") -> "请假已生效，返校后记得销假"
    status.contains("完结") -> "这条申请已经结束"
    status.contains("驳回") || status.contains("拒绝") -> "申请未通过，可修改后重新提交"
    status.contains("撤回") || status.contains("撤销") -> "申请已撤回"
    else -> null
}

private fun leaveSteps(status: String): List<LxStepItem> {
    if (status.contains("待销假") || status.contains("销假")) {
        return listOf(
            LxStepItem("已提交", LxStepState.Done),
            LxStepItem("审批通过", LxStepState.Done),
            LxStepItem("待销假", LxStepState.Current),
        )
    }
    val approved = status.contains("通过") || status.contains("完成") || status.contains("完结") ||
        status.contains("休假") || status.contains("备案")
    val rejected = status.contains("驳回") || status.contains("拒绝") || status.contains("撤回") || status.contains("撤销")
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
