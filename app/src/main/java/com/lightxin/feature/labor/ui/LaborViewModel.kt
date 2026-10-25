package com.lightxin.feature.labor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.reflect.TypeToken
import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.database.Snapshot
import com.lightxin.core.database.SnapshotKeys
import com.lightxin.core.database.SnapshotStore
import com.lightxin.core.network.AuthExpiredException
import com.lightxin.feature.labor.data.LaborRepository
import com.lightxin.feature.labor.domain.ActivityRecord
import com.lightxin.feature.labor.domain.HoursSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import javax.inject.Inject

data class LaborUiState(
    val hoursSummary: HoursSummary? = null,
    val activities: List<ActivityRecord> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val error: String? = null,
    /** 有缓存时刷新失败的提示，列表继续显示旧数据。 */
    val refreshError: String? = null,
    /** 当前展示的数据来自缓存；成功刷新后清空，页面副标题随之消失。 */
    val fetchedAt: Long? = null,
    /** 校内登录态失效（ldjy 回 200 + 非法访问）：无缓存时出整张恢复卡片。 */
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
    private val tokenManager: TokenManager,
    private val snapshotStore: SnapshotStore,
    private val session: AccountSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaborUiState())
    val uiState: StateFlow<LaborUiState> = _uiState

    private var detailJob: Job? = null

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val account = tokenManager.account()
            val cachedSummary: Snapshot<HoursSummary>? =
                readSnapshot(account, SnapshotKeys.LaborSummary, HoursSummary::class.java)
            val cachedActivities: Snapshot<List<ActivityRecord>>? =
                readSnapshot(account, SnapshotKeys.LaborActivities, activityListType, ::sortLaborActivities)
            val cachedAt = maxOf(cachedSummary?.fetchedAt ?: 0L, cachedActivities?.fetchedAt ?: 0L).takeIf { it > 0L }

            _uiState.update {
                it.copy(
                    hoursSummary = cachedSummary?.value ?: it.hoursSummary,
                    activities = cachedActivities?.value ?: it.activities,
                    isLoading = cachedAt == null,
                    isRefreshing = cachedAt != null,
                    fetchedAt = cachedAt,
                    // 缓存里已是全部记录，不再分页。
                    hasMore = if (cachedAt != null) false else it.hasMore,
                    currentPage = 1,
                    error = null,
                    refreshError = null,
                    authExpiredDetail = null,
                    loadMoreError = null,
                    isLoadingMore = false,
                )
            }
            refresh(account)
        }
    }

    private suspend fun refresh(account: AccountContext) {
        val (hoursResult, activitiesResult) = coroutineScope {
            val hoursDeferred = async { repository.getHoursSummary() }
            val activitiesDeferred = async { repository.getActivities(page = 1, pageSize = LaborPageSize) }
            hoursDeferred.await() to activitiesDeferred.await()
        }
        // 登录代次变了（退出 / 切换账号）：结果作废，不发布到界面、不写缓存。
        if (!session.isCurrent(account.lease)) return

        val failure = hoursResult.exceptionOrNull() ?: activitiesResult.exceptionOrNull()
        val hasData = _uiState.value.hoursSummary != null || _uiState.value.activities.isNotEmpty()
        if (failure != null) {
            val message = failure.message ?: "加载失败"
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    refreshError = if (hasData) message else null,
                    error = if (hasData) null else message,
                    authExpiredDetail = (failure as? AuthExpiredException)?.technicalDetail,
                )
            }
            return
        }

        val summary = hoursResult.getOrThrow()
        val page = activitiesResult.getOrThrow()
        val records = mergeLaborActivities(emptyList(), page.records)
        writeSnapshot(account, SnapshotKeys.LaborSummary, summary)
        writeSnapshot(account, SnapshotKeys.LaborActivities, records)

        _uiState.update {
            it.copy(
                hoursSummary = summary,
                activities = records,
                isLoading = false,
                isRefreshing = false,
                fetchedAt = null,
                error = null,
                refreshError = null,
                authExpiredDetail = null,
                loadMoreError = null,
                currentPage = 1,
                hasMore = laborHasMore(
                    records.size, LaborPageSize, page.records.size, records.size, page.total,
                ),
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
        viewModelScope.launch {
            val account = tokenManager.account()
            _uiState.update { it.copy(isRefreshing = _uiState.value.hoursSummary != null || _uiState.value.activities.isNotEmpty()) }
            refresh(account)
        }
    }

    fun onRecordClick(record: ActivityRecord) {
        detailJob?.cancel()
        // 立即打开弹层，加载期间用骨架占位，避免“点了没反应”。
        _uiState.update {
            it.copy(
                showDetailSheet = true,
                selectedRecord = record,
                selectedDetail = null,
                isDetailLoading = true,
                detailError = null,
            )
        }
        detailJob = viewModelScope.launch {
            repository.getActivityDetail(record.id, record.type).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(selectedDetail = detail, isDetailLoading = false)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isDetailLoading = false, detailError = e.message ?: "加载失败")
                    }
                },
            )
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                showDetailSheet = false,
                selectedRecord = null,
                selectedDetail = null,
                isDetailLoading = false,
                detailError = null,
            )
        }
    }

    private suspend fun <T> readSnapshot(
        account: AccountContext,
        key: String,
        type: java.lang.reflect.Type,
        normalize: (T) -> T = { it },
    ): Snapshot<T>? = try {
        snapshotStore.read<T>(account, key, type)?.let { Snapshot(normalize(it.value), it.fetchedAt) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun <T> writeSnapshot(account: AccountContext, key: String, value: T) {
        try {
            snapshotStore.write(account, key, value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 缓存失败不影响本次展示。
        }
    }
}

private const val LaborPageSize = 100
private val activityListType: Type =
    TypeToken.getParameterized(List::class.java, ActivityRecord::class.java).type
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
): List<ActivityRecord> = sortLaborActivities(loaded + incoming)

/** 按 createDate 倒序；解析失败的排在最后。 */
internal fun sortLaborActivities(records: List<ActivityRecord>): List<ActivityRecord> {
    val unique = records.distinctBy { it.key }
    val (parsed, unparsed) = unique.partition { parseLaborDate(it.createDate) != null }
    return parsed.sortedByDescending { parseLaborDate(it.createDate) } + unparsed
}

internal fun parseLaborDate(raw: String): LocalDateTime? =
    try {
        LocalDateTime.parse(raw, LaborDateFormat)
    } catch (_: DateTimeParseException) {
        null
    }
