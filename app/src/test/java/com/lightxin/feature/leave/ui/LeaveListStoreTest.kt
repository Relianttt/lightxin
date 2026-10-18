package com.lightxin.feature.leave.ui

import com.lightxin.feature.leave.data.LeavePage
import com.lightxin.feature.leave.data.LeaveSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveListStoreTest {
    @Test
    fun bothTabsLoadIndependentlyAndReadingCachedPagesDoesNotFetch() = runBlocking {
        val active = CompletableDeferred<Result<LeavePage>>()
        val all = CompletableDeferred<Result<LeavePage>>()
        val calls = mutableListOf<Pair<Int, String>>()
        val store = LeaveListStore(this) { page, status ->
            calls += page to status
            if (status == "1") active.await() else all.await()
        }
        store.refresh()
        yield()
        assertEquals(listOf(1 to "1", 1 to "3"), calls)
        all.complete(Result.success(LeavePage(listOf(row("all")), 1)))
        yield()
        assertTrue(store.uiState.value.active.isLoading)
        assertEquals("all", store.uiState.value.all.items.single().executionId)
        active.complete(Result.success(LeavePage(emptyList(), 0)))
        yield()
        repeat(10) {
            assertFalse(store.uiState.value.forStatus(if (it % 2 == 0) "1" else "3").isLoading)
        }
        assertEquals(2, calls.size)
        assertEquals(0, store.uiState.value.active.total)
        assertEquals(1, store.uiState.value.all.total)
    }

    @Test
    fun paginationCountsRawRowsIncludingWithdrawnAndKeepsTabsSeparate() = runBlocking {
        val calls = mutableListOf<Pair<Int, String>>()
        val store = LeaveListStore(this) { page, status ->
            calls += page to status
            val rows = when {
                status == "3" -> listOf(row("all"))
                page == 1 -> (1..20).map { row("$it", if (it <= 2) "已撤回" else "待销假") }
                else -> listOf(row("21", "已撤销"))
            }
            Result.success(LeavePage(rows, if (status == "1") 21 else 1))
        }
        store.refresh()
        yield()
        assertNull(store.uiState.value.active.total)
        store.loadMore("1")
        store.loadMore("1")
        yield()
        assertEquals(18, store.uiState.value.active.items.size)
        assertEquals(18, store.uiState.value.active.total)
        assertFalse(store.uiState.value.active.hasMore)
        assertEquals("all", store.uiState.value.all.items.single().executionId)
        store.loadMore("1")
        yield()
        assertEquals(listOf(1 to "1", 1 to "3", 2 to "1"), calls)
    }

    @Test
    fun refreshRetainsContentAndFailureDoesNotEraseEitherCache() = runBlocking {
        var fail = false
        val store = LeaveListStore(this) { _, status ->
            if (fail && status == "1") Result.failure(Exception("offline"))
            else Result.success(LeavePage(listOf(row(status)), 1))
        }
        store.refresh()
        yield()
        fail = true
        store.refresh()
        assertEquals("1", store.uiState.value.active.items.single().executionId)
        assertTrue(store.uiState.value.active.isRefreshing)
        assertFalse(store.uiState.value.active.isLoading)
        yield()
        assertEquals("offline", store.uiState.value.active.error)
        assertEquals("1", store.uiState.value.active.items.single().executionId)
        assertNull(store.uiState.value.all.error)
        fail = false
        store.retry("1")
        yield()
        assertNull(store.uiState.value.active.error)
    }

    @Test
    fun obsoleteResponseCannotOverwriteRefreshEvenIfFetchSwallowsCancellation() = runBlocking {
        val obsolete = CompletableDeferred<Result<LeavePage>>()
        var activeCalls = 0
        val store = LeaveListStore(this) { _, status ->
            if (status == "1" && ++activeCalls == 1) {
                withContext(NonCancellable) { obsolete.await() }
            } else {
                Result.success(LeavePage(listOf(row("fresh-$status")), 1))
            }
        }
        store.refresh()
        yield()
        store.refresh()
        yield()
        obsolete.complete(Result.success(LeavePage(listOf(row("obsolete")), 1)))
        yield()
        assertEquals("fresh-1", store.uiState.value.active.items.single().executionId)
        assertEquals("fresh-3", store.uiState.value.all.items.single().executionId)
    }

    private fun row(id: String, status: String = "待销假") = LeaveSummary(
        executionId = id,
        flowTitle = "学生请假申请",
        statusTxt = status,
        receiveTime = "2026-10-18 12:00:00",
    )
}
