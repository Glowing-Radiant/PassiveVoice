package com.passivevoice.interpreter

import com.passivevoice.core.TextChange
import com.passivevoice.speech.SpeechPriority

/**
 * Speaks what was just typed, from the field's own text changes, so it works with any keyboard:
 * each key as it lands, and the whole word once a space (or new line) ends it.
 */
object TypingEcho {

  /** Longer insertions are pastes or restored drafts, not typing. */
  private const val MAX_INSERTION_LENGTH = 100
  private const val MAX_DELETED_WORD_LENGTH = 30

  private val CHARACTER_NAMES =
    mapOf(
      ' ' to "space",
      '\n' to "new line",
      '\t' to "tab",
      '.' to "period",
      ',' to "comma",
      '?' to "question mark",
      '!' to "exclamation mark",
      '\'' to "apostrophe",
      '"' to "quote",
      ':' to "colon",
      ';' to "semicolon",
      '-' to "dash",
      '_' to "underscore",
      '@' to "at",
      '#' to "hash",
      '$' to "dollar",
      '%' to "percent",
      '&' to "and",
      '*' to "star",
      '(' to "left bracket",
      ')' to "right bracket",
      '[' to "left square bracket",
      ']' to "right square bracket",
      '{' to "left brace",
      '}' to "right brace",
      '<' to "less than",
      '>' to "greater than",
      '/' to "slash",
      '\\' to "backslash",
      '|' to "vertical bar",
      '+' to "plus",
      '=' to "equals",
      '~' to "tilde",
      '`' to "backtick",
      '^' to "caret",
    )

  fun interpret(change: TextChange, characters: Boolean, words: Boolean): SpeechOutput? {
    val phrase = if (change.added.isEmpty()) deletion(change.removed, characters) else insertion(change, characters, words)
    // Typing is the user's own direct action, like a tap: it may cut off scroll or screen feedback.
    return phrase?.let { SpeechOutput(it, SpeechPriority.HIGH) }
  }

  private fun insertion(change: TextChange, characters: Boolean, words: Boolean): String? {
    val added = change.added
    if (words && added.last().isWhitespace()) wordBefore(change.text, change.end - 1)?.let { return it }
    if (added.length == 1) return if (characters) nameOf(added[0]) else null
    // A picked suggestion, glide-typed word or autocorrection arrives as several characters at once.
    val inserted = added.trim()
    return if (words && inserted.isNotEmpty() && inserted.length <= MAX_INSERTION_LENGTH) inserted else null
  }

  /** Backspace says what it removed; clearing a whole sentence (often the app, after sending) stays quiet. */
  private fun deletion(removed: String, characters: Boolean): String? {
    if (!characters) return null
    if (removed.length == 1) return "${nameOf(removed[0])} deleted"
    val word = removed.trim()
    return if (word.isNotEmpty() && word.length <= MAX_DELETED_WORD_LENGTH && word.none(Char::isWhitespace)) "$word deleted" else null
  }

  /** The word that ends just before [separatorIndex], without surrounding punctuation ("hello," → "hello"). */
  private fun wordBefore(text: String, separatorIndex: Int): String? {
    if (separatorIndex !in text.indices) return null
    var start = separatorIndex
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    return text.substring(start, separatorIndex).trim { !it.isLetterOrDigit() }.ifEmpty { null }
  }

  private fun nameOf(c: Char): String =
    CHARACTER_NAMES[c] ?: if (c.isUpperCase()) "cap $c" else c.toString()
}
