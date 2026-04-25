package com.lightxin.navigation

import android.net.Uri
import kotlinx.serialization.Serializable

object Routes {
    const val ONBOARDING = "onboarding"
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
    const val RUNNING_ROUTE_SETTINGS = "running/route/settings"
    const val RUNNING_ROUTE_RECORD = "running/route/record"
    const val RUNNING_ROUTE_LIST = "running/route/list"
    const val RUNNING_ROUTE_DETAIL = "running/route/detail/{templateId}"
    fun runningRouteDetail(templateId: String) = "running/route/detail/$templateId"

    // Labor
    const val LABOR_SUMMARY = "labor/summary"
    const val LABOR_DETAIL = "labor/detail/{id}/{type}"
    fun laborDetail(id: String, type: String) = "labor/detail/$id/$type"

    // AI Class
    const val AICLASS_HOME = "aiclass/home"
    const val AICLASS_SCAN = "aiclass/scan"
    const val AICLASS_DETAIL = "aiclass/detail/{classId}"
    fun aiClassDetail(classId: String) = "aiclass/detail/${Uri.encode(classId)}"

    // About
    const val ABOUT = "about"
}
