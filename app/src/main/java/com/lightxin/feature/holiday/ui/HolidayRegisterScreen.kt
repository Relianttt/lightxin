package com.lightxin.feature.holiday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.holiday.domain.StrokeOption

@Composable
fun HolidayRegisterScreen(
    holidayId: String,
    onSubmitSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HolidayRegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val datePickerState = rememberHolidayDatePickerState()
    val error = uiState.error

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            onSubmitSuccess()
        }
    }

    // 日期选择结果回调
    LaunchedEffect(datePickerState.result) {
        datePickerState.result?.let { formatted ->
            when (datePickerState.editingField) {
                0 -> viewModel.updateStartDate(formatted)
                1 -> viewModel.updateEndDate(formatted)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LxTopBar(
                title = uiState.holidayName.ifBlank { "节假日登记" },
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            error != null && uiState.holidayName.isBlank() -> LxError(
                message = error,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            else -> RegisterForm(
                uiState = uiState,
                datePickerState = datePickerState,
                onStrokeChange = viewModel::updateStroke,
                onReasonChange = viewModel::updateReason,
                onDestinationChange = viewModel::updateDestination,
                onUrgentPhoneChange = viewModel::updateUrgentPhone,
                onSubmit = viewModel::submit,
                modifier = Modifier.padding(padding),
            )
        }
    }

    // 两个 BottomSheet
    HolidayCalendarSheet(state = datePickerState)
    HolidayTimeSheet(state = datePickerState)
}

@Composable
private fun RegisterForm(
    uiState: HolidayRegisterUiState,
    datePickerState: HolidayDatePickerState,
    onStrokeChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onUrgentPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 错误提示
        uiState.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // 日期范围
        SectionLabel("时间安排")
        DateField(
            label = "开始时间",
            value = uiState.startDate,
            placeholder = "选择开始时间",
            onClick = { datePickerState.open(fieldIndex = 0, currentValue = uiState.startDate) },
        )
        DateField(
            label = "结束时间",
            value = uiState.endDate,
            placeholder = "选择结束时间",
            onClick = { datePickerState.open(fieldIndex = 1, currentValue = uiState.endDate) },
        )

        // 离校/留校
        SectionLabel("离校/留校")
        StrokeRadioGroup(
            options = uiState.strokeOptions,
            selected = uiState.stroke,
            onSelect = onStrokeChange,
        )

        // 事由
        SectionLabel("事由")
        OutlinedTextField(
            value = uiState.reason,
            onValueChange = onReasonChange,
            label = { Text("事由") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        // 目的地（按节假日配置显隐）
        if (uiState.destinationEnabled) {
            SectionLabel("目的地")
            OutlinedTextField(
                value = uiState.destination,
                onValueChange = onDestinationChange,
                label = { Text("目的地") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // 紧急联系电话（按节假日配置显隐）
        if (uiState.urgentPhoneEnabled) {
            SectionLabel("紧急联系电话")
            OutlinedTextField(
                value = uiState.urgentPhone,
                onValueChange = onUrgentPhoneChange,
                label = { Text("紧急联系电话") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 提交按钮
        Button(
            onClick = onSubmit,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("提交登记")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ═══════════════ 日期可点字段 ═══════════════

@Composable
private fun DateField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LxInkMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LxSand)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    fontSize = 15.sp,
                    color = if (value.isBlank()) LxInkMuted.copy(alpha = 0.6f) else LxInk,
                )
                // 琥珀色小圆点提示可点击
                if (value.isBlank()) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LxTerra.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LxInkMuted,
    )
}

@Composable
private fun StrokeRadioGroup(
    options: List<StrokeOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == option.value,
                        onClick = { onSelect(option.value) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == option.value,
                    onClick = null,
                )
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
