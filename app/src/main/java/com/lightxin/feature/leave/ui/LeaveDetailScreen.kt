package com.lightxin.feature.leave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxDialogConfirmTone
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.leave.data.LeaveDetail
import com.lightxin.feature.leave.domain.LeaveFormField

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "请假详情", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null -> LxError(
                message = uiState.error.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            uiState.detail != null -> {
                val detail = uiState.detail!!
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { StatusCard(detail) }
                    item {
                        LxCard {
                            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                                Text(
                                    text = "申请内容",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LxInk,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                detail.fields.forEach { field ->
                                    LeaveReadonlyField(field, detail.values)
                                }
                                if (detail.fields.isEmpty()) {
                                    Text(
                                        text = "服务端没有返回可展示的字段。",
                                        fontSize = 13.sp,
                                        color = LxInkMuted,
                                    )
                                }
                            }
                        }
                    }

                    if (detail.statusTxt.contains("待销假")) {
                        item {
                            XjxxCard(
                                xjxx = uiState.xjxx,
                                loaded = uiState.xjxxLoaded,
                                isWorking = uiState.isWorking,
                                onSubmit = viewModel::submitXjxx,
                            )
                        }
                    }

                    item {
                        if (uiState.canWithdraw) {
                            LxOutlinedButton(
                                text = "撤回申请",
                                onClick = { withdrawVisible = true },
                                enabled = !uiState.isWorking,
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }

    if (withdrawVisible) {
        LxDialog(
            title = "撤回申请",
            message = "确定撤回这次请假申请？撤回后需要重新发起。",
            confirmText = "撤回",
            dismissText = "取消",
            confirmTone = LxDialogConfirmTone.Destructive,
            onDismissRequest = { withdrawVisible = false },
            onDismiss = { withdrawVisible = false },
            onConfirm = {
                withdrawVisible = false
                viewModel.withdraw()
            },
        )
    }
}

@Composable
private fun StatusCard(detail: LeaveDetail) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeaveStatusBadge(detail.statusTxt)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = detail.flowTitle.ifBlank { "学生请假申请" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxInk,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (detail.flowNumber.isNotBlank()) {
                Text(text = "编号 ${detail.flowNumber}", fontSize = 12.sp, color = LxInkMuted)
            }
            Text(text = "提交时间 ${detail.receiveTime.ifBlank { "--" }}", fontSize = 12.sp, color = LxInkMuted)
        }
    }
}

@Composable
private fun LeaveReadonlyField(field: LeaveFormField, values: JsonObject) {
    val value = displayValue(values.get(field.key))
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = field.label,
            fontSize = 13.sp,
            color = LxInkMuted,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = LxInk,
            modifier = Modifier.weight(0.62f),
        )
    }
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

@Composable
private fun XjxxCard(
    xjxx: JsonObject?,
    loaded: Boolean,
    isWorking: Boolean,
    onSubmit: () -> Unit,
) {
    LxCard {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "销假信息",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxTerra,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!loaded) {
                Text(text = "正在读取销假信息…", fontSize = 12.sp, color = LxInkMuted)
            } else if (xjxx == null) {
                Text(
                    text = "暂时没有拿到销假信息，可稍后重试或在服务端处理。",
                    fontSize = 12.sp,
                    color = LxInkMuted,
                )
            } else {
                xjxxLabels.forEach { (key, label) ->
                    val value = xjxx.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                    if (!value.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = label, fontSize = 12.sp, color = LxInkMuted)
                            Text(text = value, fontSize = 12.sp, color = LxInk)
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
                        Text(text = key, fontSize = 12.sp, color = LxInkMuted)
                        Text(text = value, fontSize = 12.sp, color = LxInk)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (isWritableXjxx(xjxx)) {
                    LxButton(
                        text = if (isWorking) "提交中…" else "确认销假",
                        onClick = onSubmit,
                        enabled = !isWorking,
                    )
                } else {
                    Text(
                        text = "销假信息不是可回写的表单结构，仅展示。",
                        fontSize = 11.sp,
                        color = LxInkMuted,
                    )
                }
            }
        }
    }
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

/**
 * 只有确实是可回写表单对象时才显示提交按钮。
 * 抓包里 `getXjxx.do` 返回的是展示信息（cjScStr/isCj/...），不是表单，不能直接回写。
 */
private fun isWritableXjxx(obj: JsonObject): Boolean =
    obj.has("QJRQ") || obj.has("dataJson") || obj.has("flowTitle")
