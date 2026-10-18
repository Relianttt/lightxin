package com.lightxin.feature.leave.ui

import com.lightxin.feature.leave.data.LeavePage
import com.lightxin.feature.leave.data.LeaveSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class LeaveListUiState(
    val items: List<LeaveSummary> = emptyList(),
    val total: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

data class LeaveListsUiState(
    val active: LeaveListUiState = LeaveListUiState(),
    val all: LeaveListUiState = LeaveListUiState(),
) {
    fun forStatus(status: String): LeaveListUiState = if (status == "1") active else all
}

/** 两份独立的内存列表：切页只读状态，不触发网络；刷新才同时失效。 */
internal class LeaveListStore(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int, status: String) -> Result<LeavePage>,
) {
    private val _uiState = MutableStateFlow(LeaveListsUiState())
    val uiState = _uiState.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val rawItems = mutableMapOf<String, List<LeaveSummary>>()
    private val nextPage = mutableMapOf<String, Int>()

    fun refresh() {
        load("1", reset = true)
        load("3", reset = true)
    }

    fun retry(status: String) = load(status, reset = true)

    fun loadMore(status: String) {
        val state = _uiState.value.forStatus(status)
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore || state.error != null) return
        load(status, reset = false)
    }

    private fun update(status: String, transform: (LeaveListUiState) -> LeaveListUiState) {
        _uiState.update {
            if (status == "1") it.copy(active = transform(it.active)) else it.copy(all = transform(it.all))
        }
    }

    private fun load(status: String, reset: Boolean) {
        if (!reset && jobs[status]?.isActive == true) return
        jobs[status]?.cancel()
        update(status) {
            it.copy(
                isLoading = reset && it.items.isEmpty(),
                isRefreshing = reset && it.items.isNotEmpty(),
                isLoadingMore = !reset,
                error = null,
            )
        }
        jobs[status] = scope.launch {
            val page = if (reset) 1 else nextPage[status] ?: 1
            val result = fetch(page, status)
            // Repository 会包装 Exception；被刷新取代的请求即使晚到，也不能覆盖新状态。
            coroutineContext.ensureActive()
            result.fold(
                onSuccess = { response ->
                    val raw = (if (reset) response.items else rawItems[status].orEmpty() + response.items)
                        .distinctBy { it.executionId }
                    rawItems[status] = raw
                    nextPage[status] = page + 1
                    val loadedAll = response.total?.let { raw.size >= it } ?: (response.items.size < PAGE_SIZE)
                    val total = if (status == "1") {
                        response.total?.takeIf { loadedAll }
                            ?.minus(raw.count { isWithdrawn(it.statusTxt) })?.coerceAtLeast(0)
                    } else {
                        response.total
                    }
                    update(status) {
                        it.copy(
                            items = (if (status == "1") raw.filterNot { isWithdrawn(it.statusTxt) } else raw)
                                .sortedWith(compareByDescending { parseLeaveTime(it.receiveTime) }),
                            total = total,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            hasMore = !loadedAll,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    update(status) {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = error.message ?: "获取请假列表失败",
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
