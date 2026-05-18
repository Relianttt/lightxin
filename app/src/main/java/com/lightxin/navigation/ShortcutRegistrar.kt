package com.lightxin.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.lightxin.MainActivity
import com.lightxin.R

object ShortcutRegistrar {
    private const val SCAN_SHORTCUT_ID = "scan_checkin"
    private const val DORM_SHORTCUT_ID = "dorm_checkin"

    fun register(context: Context) {
        val shortcuts = listOf(
            buildShortcut(
                context = context,
                id = SCAN_SHORTCUT_ID,
                shortLabel = context.getString(R.string.shortcut_scan_checkin),
                longLabel = context.getString(R.string.shortcut_scan_checkin_long),
                iconRes = R.drawable.ic_shortcut_scan,
                target = ShortcutTarget.SCAN_CHECKIN,
                rank = 0,
            ),
            buildShortcut(
                context = context,
                id = DORM_SHORTCUT_ID,
                shortLabel = context.getString(R.string.shortcut_dorm_checkin),
                longLabel = context.getString(R.string.shortcut_dorm_checkin_long),
                iconRes = R.drawable.ic_shortcut_dorm,
                target = ShortcutTarget.DORM_CHECKIN,
                rank = 1,
            ),
        )
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun buildShortcut(
        context: Context,
        id: String,
        shortLabel: String,
        longLabel: String,
        iconRes: Int,
        target: ShortcutTarget,
        rank: Int,
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = target.action
            flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }
}
