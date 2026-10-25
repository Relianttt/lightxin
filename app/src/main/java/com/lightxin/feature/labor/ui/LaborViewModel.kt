package com.lightxin.feature.labor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightxin.core.network.AuthExpiredException
import com.lightxin.feature.labor.data.LaborRepository
import com.lightxin.feature.labor.domain.ActivityRecord
import com.lightxin.feature.labor.domain.HoursSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import javax.inject.Inject

data class LaborUiState(
    val hoursSummary: HoursSummary? = null,
    val activities: List<ActivityRecord> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val error: String? = null,
    /** 校内登录态失效（ldjy 回 200 + 非法访问）：出整张恢复卡片，而不是一行红字。 */
    val authExpiredDetail: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val showDetailSheet: Boolean = false,
    val selectedRecord: ActivityRecord? = null,
    val selectedDetail: com.lightxin.feature.labor.domain.ActivityDetail? = null,
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
)

@HiltViewModel
class LaborViewModel @Inject constructor(
    private val repository: LaborRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaborUiState())
    val uiState: StateFlow<LaborUiState> = _uiState

    private var detailJob: Job? = null

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, authExpiredDetail = null, loadMoreError = null)
            }

            // 并行加载工时总览和首页活动
            val hoursResult = repository.getHoursSummary()
            val activitiesResult = repository.getActivities(page = 1, pageSize = LaborPageSize)

            hoursResult.fold(
                onSuccess = { summary ->
                    _uiState.update { it.copy(hoursSummary = summary) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "加载失败",
                            authExpiredDetail = (e as? AuthExpiredException)?.technicalDetail,
                        )
                    }
                    return@launch
                },
            )

            activitiesResult.fold(
                onSuccess = { page ->
                    val records = mergeLaborActivities(emptyList(), page.records)
                    _uiState.update {
                        it.copy(
                            activities = records,
                            isLoading = false,
                            currentPage = 1,
                            hasMore = laborHasMore(
                                records.size, LaborPageSize, page.records.size, records.size, page.total,
                            ),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "加载失败",
                            authExpiredDetail = (e as? AuthExpiredException)?.technicalDetail,
                        )
                    }
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }

        viewModelScope.launch {
            val nextPage = state.currentPage + 1

            repository.getActivities(page = nextPage, pageSize = LaborPageSize).fold(
                onSuccess = { page ->
                    _uiState.update {
                        val records = mergeLaborActivities(it.activities, page.records)
                        val newCount = records.size - it.activities.size
                        it.copy(
                            activities = records,
                            isLoadingMore = false,
                            currentPage = nextPage,
                            hasMore = laborHasMore(
                                records.size, LaborPageSize, page.records.size, newCount, page.total,
                            ),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoadingMore = false, loadMoreError = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    fun retry() {
        loadInitial()
    }

    fun onRecordClick(record: ActivityRecord) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, detailError = null) }
            repository.getActivityDetail(record.id, record.type).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            showDetailSheet = true,
                            selectedRecord = record,
                            selectedDetail = detail,
                            isDetailLoading = false,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            showDetailSheet = true,
                            selectedRecord = record,
                            isDetailLoading = false,
                            detailError = e.message ?: "加载失败",
                        )
                    }
                },
            )
        }
    }

    fun dismissDetail() {
        _uiState.update {
            it.copy(
                showDetailSheet = false,
                selectedRecord = null,
                selectedDetail = null,
                detailError = null,
            )
        }
    }
}

private const val LaborPageSize = 100
private val LaborDateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
    .withResolverStyle(ResolverStyle.STRICT)

internal fun laborHasMore(loaded: Int, pageSize: Int, lastPageSize: Int, newCount: Int, total: Int?): Boolean =
    when {
        newCount == 0 -> false
        total != null -> loaded < total
        else -> lastPageSize >= pageSize
    }

internal fun mergeLaborActivities(
    loaded: List<ActivityRecord>,
    incoming: List<ActivityRecord>,
): List<ActivityRecord> = (loaded + incoming).distinctBy { it.key }.sortedByDescending {
    try {
        LocalDateTime.parse(it.createDate, LaborDateFormat)
    } catch (_: DateTimeParseException) {
        null
    }
}
