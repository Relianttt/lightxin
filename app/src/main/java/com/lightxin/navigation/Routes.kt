package com.lightxin.navigation

import kotlinx.serialization.Serializable

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    // Schedule
    const val SCHEDULE = "schedule"

    // Checkin
    const val CHECKIN_LIST = "checkin/list"
    const val CHECKIN_DETAIL = "checkin/detail/{taskDateId}"
    fun checkinDetail(taskDateId: String) = "checkin/detail/$taskDateId"

    // Running
    const val RUNNING_HOME = "running/home"
    const val RUNNING_ACTIVE = "running/active"
    const val RUNNING_SIM = "running/sim"
    const val RUNNING_RESULT = "running/result"

    // Labor
    const val LABOR_SUMMARY = "labor/summary"
    const val LABOR_DETAIL = "labor/detail/{id}/{type}"
    fun laborDetail(id: String, type: String) = "labor/detail/$id/$type"
}
