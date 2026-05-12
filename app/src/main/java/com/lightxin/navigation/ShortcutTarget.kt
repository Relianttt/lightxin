package com.lightxin.navigation

enum class ShortcutTarget(val action: String) {
    SCAN_CHECKIN("com.lightxin.action.SCAN_CHECKIN"),
    DORM_CHECKIN("com.lightxin.action.DORM_CHECKIN"),
    ;

    companion object {
        fun fromAction(action: String?): ShortcutTarget? = entries.firstOrNull { it.action == action }
    }
}
