package com.passivevoice.core

/** Semantic snapshot of an [android.view.accessibility.AccessibilityNodeInfo], decoupled from the live node so it can outlive it. */
data class NodeSnapshot(
  val text: String? = null,
  val contentDescription: String? = null,
  val stateDescription: String? = null,
  val paneTitle: String? = null,
  val hintText: String? = null,
  val className: String? = null,
  val viewIdResourceName: String? = null,
  val isClickable: Boolean = false,
  val isCheckable: Boolean = false,
  val isChecked: Boolean = false,
  val isSelected: Boolean = false,
  val isEnabled: Boolean = true,
  val isPassword: Boolean = false,
  val isScrollable: Boolean = false,
  val isEditable: Boolean = false,
  /** Checked state of a switch/checkbox nested inside this (non-checkable) row, e.g. a Settings row. */
  val innerChecked: Boolean? = null,
) {
  /** Best-effort human-readable label, preferring explicit text over content description. */
  val label: String?
    get() = text?.takeIf { it.isNotBlank() } ?: contentDescription?.takeIf { it.isNotBlank() } ?: paneTitle?.takeIf { it.isNotBlank() }

  /** Stable-ish identity for a node across repeated events, used for short-term dedup/state memory. */
  val identityKey: String
    get() = viewIdResourceName ?: "${className ?: "?"}:${label ?: ""}"

  val role: String?
    get() =
      when {
        className == null -> null
        className.endsWith("Button") -> "Button"
        className.endsWith("Switch") || className.endsWith("ToggleButton") || className.endsWith("CompoundButton") -> "Switch"
        className.endsWith("CheckBox") -> "Checkbox"
        className.endsWith("RadioButton") -> "Radio button"
        className.endsWith("EditText") -> "Text field"
        className.endsWith("TabWidget") || className.contains("Tab") -> "Tab"
        className.endsWith("ImageView") -> "Image"
        className.endsWith("RecyclerView") || className.endsWith("ListView") || className.endsWith("ScrollView") -> "List"
        else -> null
      }
}
