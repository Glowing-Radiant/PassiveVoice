package com.passivevoice.adapters

import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.NodeSnapshot
import com.passivevoice.core.ScrollDirection
import com.passivevoice.core.ScrollSummary
import com.passivevoice.core.UserAction
import com.passivevoice.settings.Verbosity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Descriptions below are verbatim from Instagram 449.0 on a real device.
class InstagramAdapterTest {

  private val carousel = "Photo 1 of 3, Photo shared by बाग़ी-Haryanvi on September 18"
  private val suggested = "Suggested Photo by Jaun Elia on September 19, 2026. May be an image of flowers"
  private val reelInFeed = "Reel by ar.arruuu. Double-tap to play or pause."

  @Test
  fun `recognizes feed media descriptions`() {
    assertTrue(InstagramAdapter.isMediaDescription(carousel))
    assertTrue(InstagramAdapter.isMediaDescription(suggested))
    assertTrue(InstagramAdapter.isMediaDescription(reelInFeed))
    assertFalse(InstagramAdapter.isMediaDescription("View tagged people"))
    assertFalse(InstagramAdapter.isMediaDescription("Actions for jaun_elia_'s post"))
  }

  @Test
  fun `names posts by author`() {
    assertEquals("Post by बाग़ी-Haryanvi, 3 photos", InstagramAdapter.postPhrase(carousel, Verbosity.STANDARD))
    assertEquals("Suggested photo by Jaun Elia", InstagramAdapter.postPhrase(suggested, Verbosity.STANDARD))
    assertEquals("Reel by ar.arruuu", InstagramAdapter.postPhrase(reelInFeed, Verbosity.STANDARD))
  }

  @Test
  fun `handles the other description formats seen in the feed`() {
    val reelWithCaption = "Reel by Rijjul Madaan | Author, Liked by riddhikadwivedi and others, 19 comments, 2 days ago, \"You love poetry,\nyou should go back to it\""
    val adCarousel = "Photo 1 of 2 by Indus Valley Organic Beauty, 0 comments, 💚 Natural Colour in Just 10 Minutes."
    val tagged = "Photo 1 of 3, Photo shared by Shehnaaz Gill on September 14, 2026 tagging @5_elementsbyradhikagupta, @the.stylescribee"
    assertEquals("Reel by Rijjul Madaan", InstagramAdapter.postPhrase(reelWithCaption, Verbosity.STANDARD))
    assertEquals("Post by Indus Valley Organic Beauty, 2 photos", InstagramAdapter.postPhrase(adCarousel, Verbosity.STANDARD))
    assertEquals("Post by Shehnaaz Gill, 3 photos", InstagramAdapter.postPhrase(tagged, Verbosity.STANDARD))
  }

  @Test
  fun `detailed alt text stops before the like count`() {
    val selfie = "Photo 1 of 5, Photo by Yasmin Israr on September 21, 2026. May be a selfie of one or more people, people smiling, glasses and text., Liked by _mudassir67 and others, 79 comments"
    assertEquals(
      "Post by Yasmin Israr, 5 photos. May be a selfie of one or more people, people smiling, glasses and text.",
      InstagramAdapter.postPhrase(selfie, Verbosity.DETAILED),
    )
  }

  @Test
  fun `detailed adds Instagram's alt text, minimal just says next post`() {
    assertEquals("Suggested photo by Jaun Elia. May be an image of flowers", InstagramAdapter.postPhrase(suggested, Verbosity.DETAILED))
    assertEquals("Reel by ar.arruuu", InstagramAdapter.postPhrase(reelInFeed, Verbosity.DETAILED))
    assertEquals("Next post", InstagramAdapter.postPhrase(suggested, Verbosity.MINIMAL))
  }

  @Test
  fun `reels are named by author`() {
    assertEquals("Next reel, by ar.arruuu", InstagramAdapter.reelPhrase(forward = true, "ar.arruuu", null, Verbosity.STANDARD))
    assertEquals("Previous reel", InstagramAdapter.reelPhrase(forward = false, "ar.arruuu", null, Verbosity.MINIMAL))
    assertEquals("Next reel, by ar.arruuu. In these lines…", InstagramAdapter.reelPhrase(forward = true, "ar.arruuu", "In these lines…", Verbosity.DETAILED))
  }

  // Against a screen shaped like the real feed.

  private val adapter = InstagramAdapter()
  private val feedList = "android:id/list"

  private fun feedScreen(topDescription: String, topBottom: Int, secondDescription: String) =
    FakeScreen(
      FakeNode(
        NodeSnapshot(viewIdResourceName = feedList),
        Bounds(0, 115, 1080, 2208),
        listOf(
          FakeNode(NodeSnapshot(contentDescription = topDescription), Bounds(0, 115, 1080, topBottom)),
          FakeNode(NodeSnapshot(contentDescription = secondDescription), Bounds(0, 747, 1080, 1827)),
        ),
      )
    )

  private val feedScroll =
    UserAction(ActionType.SCROLL, InstagramAdapter.PACKAGE, null, null, null, null, NodeSnapshot(viewIdResourceName = feedList), timestamp = 0L)

  private fun speech(result: AdapterResult?) = (result as? AdapterResult.Speak)?.output?.text

  @Test
  fun `announces the post that fills most of the screen`() {
    // The carousel has scrolled mostly off the top; the suggested photo now dominates.
    val result = adapter.interpret(feedScroll, feedScreen(carousel, topBottom = 139, secondDescription = suggested), ContextManager(), Verbosity.STANDARD)
    assertEquals("Suggested photo by Jaun Elia", speech(result))
  }

  @Test
  fun `scrolling within the same post falls back to generic narration`() {
    val screen = feedScreen(carousel, topBottom = 139, secondDescription = suggested)
    adapter.interpret(feedScroll, screen, ContextManager(), Verbosity.STANDARD)
    assertNull(adapter.interpret(feedScroll, screen, ContextManager(), Verbosity.STANDARD))
    assertEquals(AdapterResult.Silence, adapter.interpret(feedScroll, screen, ContextManager(), Verbosity.MINIMAL))
  }

  @Test
  fun `reel swipes read as next reel with the visible author`() {
    val pager = "com.instagram.android:id/clips_viewer_view_pager"
    val screen =
      FakeScreen(
        FakeNode(
          NodeSnapshot(viewIdResourceName = pager),
          Bounds(0, 115, 1080, 2208),
          listOf(FakeNode(NodeSnapshot(text = "ar.arruuu", viewIdResourceName = "com.instagram.android:id/clips_author_username"), Bounds(156, 1962, 385, 2016))),
        )
      )
    val swipe =
      UserAction(
        ActionType.SCROLL, InstagramAdapter.PACKAGE, null, null, null, null, NodeSnapshot(viewIdResourceName = pager),
        scroll = ScrollSummary(ScrollDirection.FORWARD, isPageChange = true, fromIndex = 2, toIndex = 2, itemCount = 7, sampleCount = 3),
        timestamp = 0L,
      )
    assertEquals("Next reel, by ar.arruuu", speech(adapter.interpret(swipe, screen, ContextManager(), Verbosity.STANDARD)))
  }

  @Test
  fun `bottom tabs read as tabs`() {
    val tabClick =
      UserAction(ActionType.CLICK, InstagramAdapter.PACKAGE, null, "Reels", null, null, NodeSnapshot(contentDescription = "Reels", viewIdResourceName = "com.instagram.android:id/clips_tab"), timestamp = 0L)
    assertEquals("Reels tab", speech(adapter.interpret(tabClick, FakeScreen(FakeNode(NodeSnapshot(), Bounds(0, 0, 1080, 2400))), ContextManager(), Verbosity.STANDARD)))
  }
}
