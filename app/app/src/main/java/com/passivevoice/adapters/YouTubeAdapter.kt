package com.passivevoice.adapters

import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.UserAction
import com.passivevoice.interpreter.SpeechOutput
import com.passivevoice.settings.Verbosity
import com.passivevoice.speech.SpeechPriority

/**
 * YouTube (tested against 21.37): names Shorts by channel and home-feed videos by title. Shorts
 * are followed by what's on screen rather than scroll events, because YouTube sends none for them
 * (at least while TalkBack is on). Like/Save are real checkable controls, already handled generically.
 */
class YouTubeAdapter : AppAdapter {
  override val packageName = PACKAGE

  private val shorts = PageTracker()
  private var lastVideo: String? = null

  override fun interpret(action: UserAction, screen: Screen, context: ContextManager, verbosity: Verbosity): AdapterResult? {
    val viewId = action.node?.viewIdResourceName
    return when (action.type) {
      ActionType.SCROLL ->
        when (viewId) {
          SHORTS_LIST -> AdapterResult.Silence // Announced from the content itself; see onContentChanged.
          HOME_RESULTS -> video(screen, verbosity)
          else -> null
        }
      ActionType.CLICK, ActionType.SELECTION, ActionType.TAB_CHANGED ->
        tabName(action.label, action.node?.className)?.let { AdapterResult.Speak(SpeechOutput("$it tab", SpeechPriority.HIGH)) }
      else -> null
    }
  }

  override fun onContentChanged(screen: Screen, context: ContextManager, verbosity: Verbosity): SpeechOutput? {
    val list = screen.byId(SHORTS_LIST).firstOrNull()
    if (list == null) {
      shorts.leave()
      return null
    }
    // Ads come in their own layout or inside a normal page, but never with a "Go to channel" button.
    val page = screen.mostVisible(screen.byId(SHORTS_PAGE)) ?: return sponsoredShort(screen, list, verbosity)
    val channel =
      screen.find(page, limit = 1) { it.snapshot.contentDescription?.startsWith(CHANNEL_PREFIX) == true }
        .firstOrNull()?.snapshot?.contentDescription?.let(::channelName)
    val title =
      screen.find(page, limit = 1) { node ->
          val text = node.snapshot.text
          text != null && text.length > 2 && !text.startsWith("@") && node.snapshot.className?.endsWith("ViewGroup") == true
        }
        .firstOrNull()?.snapshot?.text
    if (channel == null) return sponsoredShort(screen, page, verbosity)
    // Keyed by channel alone: the first text on a page shifts while it loads, which would otherwise
    // read as a new Short. The cost is that two Shorts in a row from one channel read as one.
    val move = shorts.observe(channel) ?: return null
    return SpeechOutput(shortPhrase(move, channel, title, verbosity), SpeechPriority.NORMAL)
  }

  /** Ads in the Shorts feed are labeled "Advertiser\nAd" instead of having a channel. */
  private fun sponsoredShort(screen: Screen, within: ScreenNode, verbosity: Verbosity): SpeechOutput? {
    val advertiser =
      screen.find(within, limit = 1) { it.snapshot.contentDescription?.let(::advertiserName) != null }
        .firstOrNull()?.snapshot?.contentDescription?.let(::advertiserName) ?: return null
    val move = shorts.observe("ad|$advertiser") ?: return null
    return SpeechOutput(sponsoredPhrase(move, advertiser, verbosity), SpeechPriority.NORMAL)
  }

  private fun video(screen: Screen, verbosity: Verbosity): AdapterResult? {
    val cards = screen.find(limit = 30) { node -> node.snapshot.contentDescription?.let(::isVideoCard) == true }
    val description = screen.mostVisible(cards)?.snapshot?.contentDescription ?: return null
    if (description == lastVideo) return if (verbosity == Verbosity.MINIMAL) AdapterResult.Silence else null
    lastVideo = description
    return AdapterResult.Speak(SpeechOutput(videoPhrase(description, verbosity), SpeechPriority.NORMAL))
  }

  companion object {
    const val PACKAGE = "com.google.android.youtube"

    private fun id(name: String) = "$PACKAGE:id/$name"

    private val SHORTS_LIST = id("reel_recycler")
    private val SHORTS_PAGE = id("reel_player_page_container")
    private val HOME_RESULTS = id("results")
    private const val CHANNEL_PREFIX = "Go to channel"
    private val TABS = setOf("Home", "Shorts", "Create", "Subscriptions", "You")
    private const val SEPARATOR = " – "

    /** Bottom bar buttons, including "Subscriptions: New content is available". */
    fun tabName(label: String?, className: String?): String? {
      if (label == null || className != "android.widget.Button") return null
      return label.substringBefore(':').trim().takeIf { it in TABS }
    }

    /** "Go to channel @KaanPhodMusic" / "Go to channel: Mivi." → "KaanPhodMusic" / "Mivi". */
    fun channelName(description: String): String =
      description.removePrefix(CHANNEL_PREFIX).trim(' ', ':', '.').removePrefix("@")

    fun shortPhrase(move: PageMove, channel: String, title: String?, verbosity: Verbosity): String {
      val standard =
        when (move) {
          PageMove.FIRST -> "Short by $channel"
          PageMove.NEXT -> "Next short, by $channel"
          PageMove.PREVIOUS -> "Previous short, by $channel"
        }
      return when (verbosity) {
        Verbosity.MINIMAL ->
          when (move) {
            PageMove.FIRST -> "Short"
            PageMove.NEXT -> "Next short"
            PageMove.PREVIOUS -> "Previous short"
          }
        Verbosity.STANDARD -> standard
        Verbosity.DETAILED -> if (title != null) "$standard. $title" else standard
      }
    }

    /** "Google Gemini\nAd" or "Kavana - AI Chat Stories\nAd\n4.4 stars\nFREE" → the first line. */
    fun advertiserName(description: String): String? {
      val lines = description.lines().map { it.trim() }.filter { it.isNotEmpty() }
      if (lines.size < 2 || lines.drop(1).none { it == "Ad" || it == "Sponsored" }) return null
      return lines.first()
    }

    fun sponsoredPhrase(move: PageMove, advertiser: String, verbosity: Verbosity): String =
      when {
        verbosity == Verbosity.MINIMAL -> "Ad"
        move == PageMove.PREVIOUS -> "Previous short, ad from $advertiser"
        else -> "Sponsored short, from $advertiser"
      }

    /** e.g. "Title – 43 minutes, 10 seconds – Go to channel X – X - 192 thousand views - 5 years ago – play video". */
    fun isVideoCard(description: String): Boolean =
      SEPARATOR in description && (description.endsWith("play video") || description.endsWith("play Short") || description.startsWith("Sponsored$SEPARATOR"))

    fun videoPhrase(description: String, verbosity: Verbosity): String {
      val parts = description.split(SEPARATOR)
      val sponsored = parts.first() == "Sponsored"
      val fields = if (sponsored) parts.drop(1) else parts
      val title = fields.first()
      if (verbosity == Verbosity.MINIMAL) return title

      val channel = fields.firstOrNull { it.startsWith(CHANNEL_PREFIX) }?.let(::channelName)
      val isShort = description.endsWith("play Short")
      val standard =
        buildString {
          if (sponsored) append("Sponsored: ")
          append(title)
          if (channel != null) append(", by $channel")
          if (isShort) append(", Short")
        }
      if (verbosity != Verbosity.DETAILED) return standard

      val duration = fields.getOrNull(1)?.takeIf { f -> listOf("second", "minute", "hour").any { it in f } }
      val stats = fields.firstOrNull { " views" in it }?.split(" - ")?.drop(1)?.joinToString(", ")
      return listOfNotNull(standard, duration, stats).joinToString(", ")
    }
  }
}
