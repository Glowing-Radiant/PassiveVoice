package com.passivevoice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextChangeTest {

  @Test
  fun `a typed character is the change`() {
    assertEquals(TextChange("o", "", "hello", 5), TextChange.from("hell", "hello", 4, 1, 0))
  }

  @Test
  fun `a keyboard replacing the composing word reduces to the new character`() {
    assertEquals(TextChange("l", "", "hel", 3), TextChange.from("he", "hel", 0, 3, 2))
  }

  @Test
  fun `autocorrect on space keeps the corrected text`() {
    val change = TextChange.from("teh", "the ", 0, 4, 3)!!
    assertEquals("the ", change.text)
    assertEquals(4, change.end)
    assertEquals("he ", change.added)
  }

  @Test
  fun `a deletion trusts the old text, not the hint an emptied field reports`() {
    assertEquals(TextChange("", "a", "", 0), TextChange.from("a", "Search", 0, 0, 1))
  }

  @Test
  fun `inconsistent indices fall back to comparing the texts`() {
    assertEquals(TextChange("!", "", "hi!", 3), TextChange.from("hi", "hi!", -1, 1, 0))
  }

  @Test
  fun `without the old text the indices locate the insertion`() {
    assertEquals(TextChange("i", "", "hi", 2), TextChange.from(null, "hi", 1, 1, 0))
  }

  @Test
  fun `no difference is no change`() {
    assertNull(TextChange.from("same", "same", 0, 4, 4))
    assertNull(TextChange.from(null, "", 0, 0, 1))
  }
}
