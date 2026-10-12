package com.passivevoice.core

/** What one edit did to a text field: [removed] was replaced by [added], which ends at [end] in the new [text]. */
data class TextChange(val added: String, val removed: String, val text: String, val end: Int) {

  companion object {
    /**
     * Builds the change from a TYPE_VIEW_TEXT_CHANGED event's fields. Keyboards often replace the
     * whole word being composed ("he" → "hel"), so the reported region is trimmed down to what
     * actually changed. Returns null when nothing changed or the event is inconsistent.
     */
    fun from(before: String?, after: String, fromIndex: Int, addedCount: Int, removedCount: Int): TextChange? {
      if (addedCount <= 0) {
        // An emptied field reports its hint as its text, so only the old text and the indices can be trusted.
        if (before == null || removedCount <= 0 || fromIndex < 0 || fromIndex + removedCount > before.length) return null
        return TextChange("", before.substring(fromIndex, fromIndex + removedCount), before.removeRange(fromIndex, fromIndex + removedCount), fromIndex)
      }
      if (before == null) {
        if (fromIndex < 0 || fromIndex + addedCount > after.length) return null
        return TextChange(after.substring(fromIndex, fromIndex + addedCount), "", after, fromIndex + addedCount)
      }
      return if (indicesMatch(before, after, fromIndex, addedCount, removedCount)) {
        trimmed(before, after, fromIndex, fromIndex + removedCount, fromIndex + addedCount)
      } else {
        // Some apps report indices that don't describe the edit; compare the whole texts instead.
        trimmed(before, after, 0, before.length, after.length)
      }
    }

    private fun indicesMatch(before: String, after: String, from: Int, added: Int, removed: Int): Boolean =
      from >= 0 &&
        from + removed <= before.length &&
        from + added <= after.length &&
        before.length - removed + added == after.length &&
        before.regionMatches(0, after, 0, from) &&
        before.regionMatches(from + removed, after, from + added, before.length - from - removed)

    /** Narrows `before[start, beforeEnd)` → `after[start, afterEnd)` by dropping the characters both sides share. */
    private fun trimmed(before: String, after: String, start: Int, beforeEnd: Int, afterEnd: Int): TextChange? {
      var from = start
      while (from < beforeEnd && from < afterEnd && before[from] == after[from]) from++
      var oldEnd = beforeEnd
      var newEnd = afterEnd
      while (oldEnd > from && newEnd > from && before[oldEnd - 1] == after[newEnd - 1]) {
        oldEnd--
        newEnd--
      }
      if (oldEnd == from && newEnd == from) return null
      return TextChange(after.substring(from, newEnd), before.substring(from, oldEnd), after, newEnd)
    }
  }
}
