package com.lightxin.navigation

import com.lightxin.feature.checkin.data.CheckinRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutRouter @Inject constructor(
    private val checkinRepository: CheckinRepository,
) {
    var pendingDormTaskId: String? = null
        private set

    suspend fun resolveFirstUnsignedTask(): String? {
        val id = checkinRepository.getTasks(page = 1)
            .getOrNull()
            ?.firstOrNull { !it.isSigned }
            ?.taskDateId
        pendingDormTaskId = id
        return id
    }

    fun consume() {
        pendingDormTaskId = null
    }
}
