package com.passivevoice.adapters

import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.ScrollDirection
import com.passivevoice.core.UserAction
import com.passivevoice.interpreter.SpeechOutput
import com.passivevoice.settings.Verbosity
import com.passivevoice.speech.SpeechPriority

/**
 * Instagram (tested against 449.0): names reels and feed posts by author instead of "Next item",
 * and reads the bottom tabs as tabs. Likes, follows and saves need nothing special: their labels
 * flip ("Like" → "Unlike", "Follow" → "Following"), which the generic engine already handles.
 */
class InstagramAdapter : AppAdapter {
  override val packageName = PACKAGE

  private var lastPost: String? = null

  override fun interpret(action: UserAction, screen: Screen, context: ContextManager, verbosity: Verbosity): AdapterResult? {
    val viewId = action.node?.viewIdResourceName
    return when (action.type) {
      ActionType.SCROLL ->
        when (viewId) {
          REELS_PAGER -> reel(action, screen, verbosity)
          FEED_LIST -> post(screen, verbosity)
          else -> null
        }
      ActionType.CLICK -> if (viewId in TAB_IDS) tab(action.label) else null
      ActionType.SELECTION, ActionType.TAB_CHANGED -> if (action.label in TAB_LABELS) tab(action.label) else null
      else -> null
    }
  }

  private fun tab(label: String?): AdapterResult? = label?.let { AdapterResult.Speak(SpeechOutput("$it tab", SpeechPriority.HIGH)) }

  private fun reel(action: UserAction, screen: Screen, verbosity: Verbosity): AdapterResult? {
    val scroll = action.scroll ?: return null
    if (!scroll.isPageChange) return null
    val author = screen.mostVisible(screen.byId(id("clips_author_username")))?.snapshot?.label
    val caption =
      if (verbosity == Verbosity.DETAILED) {
        screen.mostVisible(screen.byId(id("clips_caption_component")))?.let { component ->
          screen.find(component, limit = 1) { it.snapshot.label != null }.firstOrNull()?.snapshot?.label
        }
      } else {
        null
      }
    val phrase = reelPhrase(forward = scroll.direction == ScrollDirection.FORWARD, author, caption, verbosity)
    return AdapterResult.Speak(SpeechOutput(phrase, SpeechPriority.NORMAL))
  }

  private fun post(screen: Screen, verbosity: Verbosity): AdapterResult? {
    val media = screen.find(limit = 30) { node -> node.snapshot.contentDescription?.let(::isMediaDescription) == true }
    val description = screen.mostVisible(media)?.snapshot?.contentDescription ?: return null
    // Still on the same post: nothing new to say (Minimal), or fall back to "Scrolled down".
    if (description == lastPost) return if (verbosity == Verbosity.MINIMAL) AdapterResult.Silence else null
    lastPost = description
    return AdapterResult.Speak(SpeechOutput(postPhrase(description, verbosity), SpeechPriority.NORMAL))
  }

  companion object {
    const val PACKAGE = "com.instagram.android"

    private fun id(name: String) = "$PACKAGE:id/$name"

    private val REELS_PAGER = id("clips_viewer_view_pager")
    private val FEED_LIST = "android:id/list" // A framework ID, not Instagram's own.
    private val TAB_IDS = setOf(id("feed_tab"), id("clips_tab"), id("direct_tab"), id("search_tab"), id("profile_tab"))
    private val TAB_LABELS = setOf("Home", "Reels", "Message", "Search and explore", "Profile")

    private val MEDIA = Regex("^(Suggested )?(Photo|Video|Reel)\\b.*\\bby\\b", RegexOption.IGNORE_CASE)
    // The author ends at a date (" on September 18"), a sentence, " | Author", ", 0 comments", or a caption's line break.
    private val AUTHOR = Regex("\\bby (.+?)(?= on [A-Z][a-z]+ \\d|\\. |\\.$| \\| |, |\\n|$)")
    private val CAROUSEL_COUNT = Regex("^\\w+ \\d+ of (\\d+)")
    private val NOUN = Regex("\\b(Photo|Video|Reel)\\b", RegexOption.IGNORE_CASE)

    /** e.g. "Photo 1 of 3, Photo shared by X on September 18" or "Suggested Photo by X on …. May be an image of …". */
    fun isMediaDescription(description: String): Boolean = MEDIA.containsMatchIn(description)

    fun postPhrase(description: String, verbosity: Verbosity): String {
      val author = AUTHOR.find(description)?.groupValues?.get(1)?.trim()
      if (verbosity == Verbosity.MINIMAL || author == null) return "Next post"

      val suggested = description.startsWith("Suggested", ignoreCase = true)
      val count = CAROUSEL_COUNT.find(description)?.groupValues?.get(1)
      val noun = NOUN.find(description)?.value?.lowercase() ?: "post"
      val standard =
        buildString {
          if (suggested) append("Suggested ")
          if (count != null) {
            append("post by $author, $count ${if (noun == "photo") "photos" else "items"}")
          } else {
            append("$noun by $author")
          }
        }.replaceFirstChar { it.uppercase() }

      // Instagram's automatic alt text follows the first sentence ("May be an image of …"), before the like count.
      val altText =
        description.substringAfter(". ", "").substringBefore(", Liked by").substringBefore('\n').trimEnd(',', ' ').takeIf { it.startsWith("May be") }
      return if (verbosity == Verbosity.DETAILED && altText != null) "$standard. $altText" else standard
    }

    fun reelPhrase(forward: Boolean, author: String?, caption: String?, verbosity: Verbosity): String {
      val base = if (forward) "Next reel" else "Previous reel"
      if (verbosity == Verbosity.MINIMAL || author == null) return base
      val standard = "$base, by $author"
      return if (verbosity == Verbosity.DETAILED && caption != null) "$standard. $caption" else standard
    }
  }
}
