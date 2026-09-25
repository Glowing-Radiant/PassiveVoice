package com.passivevoice.adapters

/** Picks the app-specific adapter for a package, if there is one (PRD §20 "App Recognition"). */
class AppAdapterManager(adapters: List<AppAdapter> = listOf(InstagramAdapter(), YouTubeAdapter())) {
  private val byPackage = adapters.associateBy { it.packageName }

  fun forPackage(packageName: String?): AppAdapter? = packageName?.let(byPackage::get)

  companion object {
    /** Human-readable app names for adapters, for settings and the debug log. */
    val SUPPORTED_APPS = mapOf(InstagramAdapter.PACKAGE to "Instagram", YouTubeAdapter.PACKAGE to "YouTube")
  }
}
