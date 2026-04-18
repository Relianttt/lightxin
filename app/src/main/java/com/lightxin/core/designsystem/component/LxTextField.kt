package com.lightxin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkFaint
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSandDeep
import com.lightxin.core.designsystem.theme.LxTerra

/**
 * 暖色输入框：标签置于上方，不依赖 Material3 浮动 label，避免描边与标题裁切。
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
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        !enabled -> LxSandDeep.copy(alpha = 0.65f)
        isFocused -> LxTerra
        else -> LxSandDeep
    }
    val labelColor = when {
        !enabled -> LxInkFaint
        isFocused -> LxTerra
        else -> LxInkMuted
    }
    val textStyle = MaterialTheme.typography.bodyLarge.merge(
        TextStyle(color = if (enabled) LxInk else LxInkFaint),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(LxTerra),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                val containerModifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.height(56.dp) else Modifier.heightIn(min = 56.dp))
                    .clip(MaterialTheme.shapes.small)
                    .background(LxCream)
                    .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.small)
                Row(
                    modifier = containerModifier
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (singleLine) 0.dp else 15.dp,
                        ),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (singleLine) Modifier.fillMaxHeight() else Modifier),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                    ) {
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .then(if (singleLine) Modifier.fillMaxHeight() else Modifier),
                            contentAlignment = if (singleLine) Alignment.Center else Alignment.TopCenter,
                        ) {
                            trailingIcon()
                        }
                    }
                }
            },
        )
    }
}
