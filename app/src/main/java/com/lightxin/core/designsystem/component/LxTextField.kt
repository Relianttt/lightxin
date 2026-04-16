package com.lightxin.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkFaint
import com.lightxin.core.designsystem.theme.LxSandDeep

/**
 * 暖色输入框：cream 底、sand-deep 边框、聚焦 terra、圆角 RMd(12dp)、高度 56dp 以容纳 label
 * （浮动 label 的 Material3 OutlinedTextField 需要略多一点垂直空间）
 */
@Composable
fun LxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().height(56.dp),
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.small, // 12dp
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LxInk,
            unfocusedTextColor = LxInk,
            focusedContainerColor = LxCream,
            unfocusedContainerColor = LxCream,
            disabledContainerColor = LxCream,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = LxSandDeep,
            disabledBorderColor = LxSandDeep,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = LxInkFaint,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
