package com.passivevoice.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(val packageName: String, val label: String)

/** Apps the user can open from the launcher: the ones worth giving a per-app profile. */
object InstalledApps {

  fun launchable(context: Context): List<AppEntry> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return packageManager
      .queryIntentActivities(intent, 0)
      .mapNotNull { info ->
        val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
        AppEntry(packageName, info.loadLabel(packageManager).toString())
      }
      .distinctBy { it.packageName }
      .sortedBy { it.label.lowercase() }
  }

  fun labelFor(context: Context, packageName: String): String =
    try {
      val packageManager = context.packageManager
      @Suppress("DEPRECATION")
      packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    } catch (_: PackageManager.NameNotFoundException) {
      packageName
    }
}
