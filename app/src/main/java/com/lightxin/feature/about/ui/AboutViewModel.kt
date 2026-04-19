package com.lightxin.feature.about.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.settings.DeveloperPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val versionName: String = "",
    val advancedEnabled: Boolean = false,
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val developerPrefs: DeveloperPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState(versionName = readVersionName()))
    val uiState: StateFlow<AboutUiState> = _uiState

    init {
        viewModelScope.launch {
            developerPrefs.isAdvancedEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(advancedEnabled = enabled) }
            }
        }
    }

    fun setAdvancedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            developerPrefs.setAdvancedEnabled(enabled)
        }
    }

    private fun readVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
