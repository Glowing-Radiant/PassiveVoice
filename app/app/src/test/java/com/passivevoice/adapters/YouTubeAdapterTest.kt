package com.passivevoice.adapters

import com.passivevoice.core.ContextManager
import com.passivevoice.core.NodeSnapshot
import com.passivevoice.settings.Verbosity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Descriptions below are verbatim from YouTube 21.37 on a real device.
class YouTubeAdapterTest {

  private val video =
    "Brandon Sanderson Talks Rhythm of War🥁 YouTube Success📸  & The Industry🛠️ – 43 minutes, 10 seconds – Go to channel Daniel Greene – Daniel Greene - 192 thousand views - 5 years ago – play video"
  private val ad = "Sponsored – MIVI One 5G- Only Snapdragon in the segment at special BBD price ₹11,999* – 43 seconds – Mivi One 5G - A phone"

  @Test
  fun `recognizes home feed video cards`() {
    assertTrue(YouTubeAdapter.isVideoCard(video))
    assertTrue(YouTubeAdapter.isVideoCard(ad))
    assertFalse(YouTubeAdapter.isVideoCard("More actions"))
  }

  @Test
  fun `names videos by title and channel`() {
    assertEquals(
      "Brandon Sanderson Talks Rhythm of War🥁 YouTube Success📸  & The Industry🛠️, by Daniel Greene",
      YouTubeAdapter.videoPhrase(video, Verbosity.STANDARD),
    )
    assertEquals(
      "Brandon Sanderson Talks Rhythm of War🥁 YouTube Success📸  & The Industry🛠️, by Daniel Greene, 43 minutes, 10 seconds, 192 thousand views, 5 years ago",
      YouTubeAdapter.videoPhrase(video, Verbosity.DETAILED),
    )
    assertEquals("Sponsored: MIVI One 5G- Only Snapdragon in the segment at special BBD price ₹11,999*", YouTubeAdapter.videoPhrase(ad, Verbosity.STANDARD))
  }

  @Test
  fun `advertiser names come from ad labels in either layout`() {
    assertEquals("Google Gemini", YouTubeAdapter.advertiserName("Google Gemini\nAd"))
    assertEquals("Kavana - AI Chat Stories", YouTubeAdapter.advertiserName("Kavana - AI Chat Stories\nAd\n4.4 stars\nFREE"))
    assertNull(YouTubeAdapter.advertiserName("Ad"))
    assertNull(YouTubeAdapter.advertiserName("Normies React to Weird Side of Instagram"))
  }

  @Test
  fun `channel names drop the prefix and the at sign`() {
    assertEquals("KaanPhodMusic", YouTubeAdapter.channelName("Go to channel @KaanPhodMusic"))
    assertEquals("Mivi", YouTubeAdapter.channelName("Go to channel: Mivi."))
  }

  @Test
  fun `bottom bar buttons read as tabs, including decorated labels`() {
    assertEquals("Subscriptions", YouTubeAdapter.tabName("Subscriptions: New content is available", "android.widget.Button"))
    assertEquals("Home", YouTubeAdapter.tabName("Home", "android.widget.Button"))
    assertNull(YouTubeAdapter.tabName("Home", "android.widget.TextView"))
    assertNull(YouTubeAdapter.tabName("Share this video", "android.widget.Button"))
  }

  @Test
  fun `short phrases per verbosity`() {
    assertEquals("Next short, by KaanPhodMusic", YouTubeAdapter.shortPhrase(PageMove.NEXT, "KaanPhodMusic", "This rabbit…", Verbosity.STANDARD))
    assertEquals("Previous short", YouTubeAdapter.shortPhrase(PageMove.PREVIOUS, "KaanPhodMusic", "This rabbit…", Verbosity.MINIMAL))
    assertEquals("Short by KaanPhodMusic. This rabbit…", YouTubeAdapter.shortPhrase(PageMove.FIRST, "KaanPhodMusic", "This rabbit…", Verbosity.DETAILED))
  }

  // Shorts send no scroll events, so the adapter follows what's on screen.

  private fun shortsList(vararg pages: FakeNode) =
    FakeScreen(
      FakeNode(
        NodeSnapshot(),
        Bounds(0, 0, 1080, 2400),
        listOf(FakeNode(NodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_recycler"), Bounds(0, 115, 1080, 2256), pages.toList())),
      )
    )

  private fun shortsScreen(channel: String?, title: String?) =
    shortsList(
      FakeNode(
        NodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_player_page_container"),
        Bounds(0, 115, 1080, 2256),
        listOfNotNull(
          channel?.let { FakeNode(NodeSnapshot(contentDescription = "Go to channel @$it", className = "android.widget.ImageView"), Bounds(48, 2034, 144, 2130)) },
          title?.let { FakeNode(NodeSnapshot(text = it, className = "android.view.ViewGroup"), Bounds(48, 2154, 888, 2208)) },
        ),
      )
    )

  // An ad in the Shorts feed, verbatim structure: no page container, "Google Gemini\nAd".
  private fun adScreen() =
    shortsList(
      FakeNode(
        NodeSnapshot(className = "android.widget.RelativeLayout"),
        Bounds(0, 115, 1080, 2256),
        listOf(FakeNode(NodeSnapshot(contentDescription = "Google Gemini\nAd", className = "android.view.ViewGroup"), Bounds(48, 1793, 918, 2028))),
      )
    )

  @Test
  fun `ads inside a normal Shorts page are recognized too`() {
    val adInPage =
      shortsList(
        FakeNode(
          NodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_player_page_container"),
          Bounds(0, 115, 1080, 2256),
          listOf(
            FakeNode(NodeSnapshot(contentDescription = "Brick & Bolt\nAd", className = "android.view.ViewGroup"), Bounds(48, 1721, 888, 1956)),
            FakeNode(NodeSnapshot(text = "Assured quality with 470+ unique checks", className = "android.view.ViewGroup"), Bounds(48, 1899, 888, 1990)),
          ),
        )
      )
    val adapter = YouTubeAdapter()
    adapter.onContentChanged(shortsScreen("ExtraGaming31", "Clutch"), ContextManager(), Verbosity.STANDARD)
    assertEquals("Sponsored short, from Brick & Bolt", adapter.onContentChanged(adInPage, ContextManager(), Verbosity.STANDARD)?.text)
  }

  @Test
  fun `ads between Shorts are announced as sponsored and don't reset the history`() {
    val adapter = YouTubeAdapter()
    val context = ContextManager()
    adapter.onContentChanged(shortsScreen("agsyworld", "Street food"), context, Verbosity.STANDARD)
    assertEquals("Sponsored short, from Google Gemini", adapter.onContentChanged(adScreen(), context, Verbosity.STANDARD)?.text)
    assertEquals("Next short, by notfunnyrohan", adapter.onContentChanged(shortsScreen("notfunnyrohan", "Ladko ko"), context, Verbosity.STANDARD)?.text)
    assertEquals("Previous short, ad from Google Gemini", adapter.onContentChanged(adScreen(), context, Verbosity.STANDARD)?.text)
  }

  @Test
  fun `a new Short on screen is announced once`() {
    val adapter = YouTubeAdapter()
    val context = ContextManager()
    assertEquals("Short by JasmitSingh_Inspire", adapter.onContentChanged(shortsScreen("JasmitSingh_Inspire", "Stay hungry"), context, Verbosity.STANDARD)?.text)
    assertNull(adapter.onContentChanged(shortsScreen("JasmitSingh_Inspire", "Stay hungry"), context, Verbosity.STANDARD))
    assertEquals("Next short, by Mr.Solanki-u7d", adapter.onContentChanged(shortsScreen("Mr.Solanki-u7d", "Tractor stunts"), context, Verbosity.STANDARD)?.text)
    assertEquals("Previous short, by JasmitSingh_Inspire", adapter.onContentChanged(shortsScreen("JasmitSingh_Inspire", "Stay hungry"), context, Verbosity.STANDARD)?.text)
  }

  @Test
  fun `a Short's title settling while it loads is not a new Short`() {
    val adapter = YouTubeAdapter()
    val context = ContextManager()
    assertEquals("Short by PersevereExtra", adapter.onContentChanged(shortsScreen("PersevereExtra", null), context, Verbosity.STANDARD)?.text)
    assertNull(adapter.onContentChanged(shortsScreen("PersevereExtra", "Original audio"), context, Verbosity.STANDARD))
    assertNull(adapter.onContentChanged(shortsScreen("PersevereExtra", "Discipline beats motivation"), context, Verbosity.STANDARD))
  }

  @Test
  fun `nothing is announced outside Shorts`() {
    val adapter = YouTubeAdapter()
    assertNull(adapter.onContentChanged(FakeScreen(FakeNode(NodeSnapshot(), Bounds(0, 0, 1080, 2400))), ContextManager(), Verbosity.STANDARD))
  }
}
