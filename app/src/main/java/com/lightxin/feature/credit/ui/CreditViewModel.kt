package com.lightxin.feature.credit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.reflect.TypeToken
import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.database.Snapshot
import com.lightxin.core.database.SnapshotKeys
import com.lightxin.core.database.SnapshotStore
import com.lightxin.feature.credit.data.CreditRepository
import com.lightxin.feature.credit.domain.CreditOverview
import com.lightxin.feature.credit.domain.CreditRecord
import com.lightxin.feature.credit.domain.CreditRecordDetail
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
import javax.inject.Inject

data class CreditUiState(
    val overview: CreditOverview? = null,
    val records: List<CreditRecord> = emptyList(),
    val selectedRecord: CreditRecord? = null,
    val selectedDetail: CreditRecordDetail? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDetailLoading: Boolean = false,
    val showDetailSheet: Boolean = false,
    /** 一条数据都没有时的整页错误。 */
    val error: String? = null,
    /** 有数据但本次刷新失败：header 下显示一行重试提示。 */
    val refreshError: String? = null,
    /** 记录区为空时的错误，直接占住记录区。 */
    val recordsError: String? = null,
    /** 当前展示的数据来自缓存；成功刷新后清空。 */
    val fetchedAt: Long? = null,
    val detailError: String? = null,
)

@HiltViewModel
class CreditViewModel @Inject constructor(
    private val repository: CreditRepository,
    private val tokenManager: TokenManager,
    private val snapshotStore: SnapshotStore,
    private val session: AccountSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreditUiState())
    val uiState: StateFlow<CreditUiState> = _uiState

    private var detailJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val account = tokenManager.account()
            val cachedOverview: Snapshot<CreditOverview>? =
                readSnapshot(account, SnapshotKeys.CreditOverview, CreditOverview::class.java)
            val cachedRecords: Snapshot<List<CreditRecord>>? =
                readSnapshot(account, SnapshotKeys.CreditRecords, creditRecordsType)
            val cachedAt = maxOf(cachedOverview?.fetchedAt ?: 0L, cachedRecords?.fetchedAt ?: 0L).takeIf { it > 0L }

            _uiState.update {
                it.copy(
                    overview = cachedOverview?.value ?: it.overview,
                    records = cachedRecords?.value ?: it.records,
                    isLoading = cachedAt == null,
                    isRefreshing = cachedAt != null,
                    fetchedAt = cachedAt,
                    error = null,
                    refreshError = null,
                    recordsError = null,
                )
            }
            refresh(account)
        }
    }

    private suspend fun refresh(account: AccountContext) {
        val (overviewResult, recordsResult) = coroutineScope {
            val overviewDeferred = async { repository.getOverview() }
            val recordsDeferred = async { repository.getRecords() }
            overviewDeferred.await() to recordsDeferred.await()
        }
        // 登录代次变了（退出 / 切换账号）：结果作废，不发布到界面、不写缓存。
        if (!session.isCurrent(account.lease)) return

        overviewResult.getOrNull()?.let { writeSnapshot(account, SnapshotKeys.CreditOverview, it) }
        recordsResult.getOrNull()?.let { writeSnapshot(account, SnapshotKeys.CreditRecords, it) }

        val state = _uiState.value
        val overview = overviewResult.getOrNull() ?: state.overview
        val records = recordsResult.getOrNull() ?: state.records
        val failure = overviewResult.exceptionOrNull() ?: recordsResult.exceptionOrNull()
        val hasData = overview != null || records.isNotEmpty()
        val allSucceeded = overviewResult.isSuccess && recordsResult.isSuccess

        _uiState.update {
            it.copy(
                overview = overview,
                records = records,
                isLoading = false,
                isRefreshing = false,
                fetchedAt = if (allSucceeded) null else it.fetchedAt,
                error = if (!hasData) failure?.message ?: "加载失败" else null,
                refreshError = if (hasData) failure?.message else null,
                recordsError = recordsResult.exceptionOrNull()?.message?.takeIf { records.isEmpty() },
            )
        }
    }

    fun onRecordClick(record: CreditRecord) {
        detailJob?.cancel()
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
            val result = repository.getRecordDetail(record.id)
            _uiState.update {
                it.copy(
                    selectedDetail = result.getOrNull(),
                    isDetailLoading = false,
                    detailError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(showDetailSheet = false, selectedRecord = null, selectedDetail = null, isDetailLoading = false, detailError = null)
        }
    }

    fun retry() {
        viewModelScope.launch {
            val account = tokenManager.account()
            _uiState.update {
                it.copy(isRefreshing = it.overview != null || it.records.isNotEmpty())
            }
            refresh(account)
        }
    }

    private suspend fun <T> readSnapshot(
        account: AccountContext,
        key: String,
        type: java.lang.reflect.Type,
    ): Snapshot<T>? = try {
        snapshotStore.read(account, key, type)
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

private val creditRecordsType: Type =
    TypeToken.getParameterized(List::class.java, CreditRecord::class.java).type
