package com.lightxin.feature.update.domain

sealed interface UpdateResult {
    data class HasUpdate(val info: UpdateInfo) : UpdateResult
    data object AlreadyLatest : UpdateResult
    data object Throttled : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}
