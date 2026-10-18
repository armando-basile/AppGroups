package it.armando.appgroups

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager

data class AppInfo(val ref: AppRef, val label: String, val activity: LauncherActivityInfo)

object AppCatalog {
    fun load(context: Context): List<AppInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val userManager = context.getSystemService(UserManager::class.java)
        return launcherApps.profiles
            .flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user)
                    .filter { it.componentName.packageName != context.packageName }
                    .map { AppInfo(AppRef(it.componentName.packageName, it.componentName.className, serial), it.label.toString(), it) }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun find(context: Context, ref: AppRef): LauncherActivityInfo? {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = userFor(context, ref.user) ?: return null
        val activities = launcherApps.getActivityList(ref.pkg, user)
        return activities.firstOrNull { it.componentName.className == ref.cls } ?: activities.firstOrNull()
    }

    fun launch(context: Context, info: LauncherActivityInfo): Boolean = runCatching {
        context.getSystemService(LauncherApps::class.java)
            .startMainActivity(info.componentName, info.user, null, null)
    }.isSuccess

    private fun userFor(context: Context, serial: Long): UserHandle? =
        context.getSystemService(UserManager::class.java).getUserForSerialNumber(serial)
}
