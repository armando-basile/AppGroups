package it.armando.appgroups

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

object ShortcutHelper {
    const val EXTRA_GROUP_ID = "group_id"

    fun requestPin(context: Context, group: Group, repo: GroupRepository): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) return false
        val info = build(context, group, repo) ?: return false
        return manager.requestPinShortcut(info, null)
    }

    fun updateIfPinned(context: Context, group: Group, repo: GroupRepository) {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (manager.pinnedShortcuts.none { it.id == group.shortcutId }) return
        // A group removed earlier has its shortcut disabled; re-enable it if the group comes back.
        manager.enableShortcuts(listOf(group.shortcutId))
        build(context, group, repo)?.let { manager.updateShortcuts(listOf(it)) }
    }

    fun disable(context: Context, group: Group) {
        context.getSystemService(ShortcutManager::class.java)
            .disableShortcuts(listOf(group.shortcutId))
    }

    private fun build(context: Context, group: Group, repo: GroupRepository): ShortcutInfo? {
        val bitmap = repo.iconBitmap(group.id) ?: return null
        val intent = Intent(context, PopupActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_GROUP_ID, group.id)
        return ShortcutInfo.Builder(context, group.shortcutId)
            .setShortLabel(group.label)
            .setLongLabel(group.label)
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(intent)
            .build()
    }
}
