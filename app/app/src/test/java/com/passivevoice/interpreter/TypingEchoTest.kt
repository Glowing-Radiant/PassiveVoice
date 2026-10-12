package com.passivevoice.interpreter

import com.passivevoice.core.TextChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypingEchoTest {

  private fun typed(before: String, after: String, characters: Boolean = true, words: Boolean = true) =
    TypingEcho.interpret(TextChange.from(before, after, 0, after.length, before.length)!!, characters, words)?.text

  @Test
  fun `each key is spoken`() {
    assertEquals("h", typed("", "h"))
    assertEquals("cap H", typed("", "H"))
    assertEquals("comma", typed("hi", "hi,"))
  }

  @Test
  fun `a space after a word speaks the word`() {
    assertEquals("hello", typed("hello", "hello "))
    assertEquals("hello", typed("hello,", "hello, "))
    assertEquals("world", typed("hello world", "hello world\n"))
  }

  @Test
  fun `a space with no word before it is just a space`() {
    assertEquals("space", typed("hi ", "hi  "))
    assertEquals("space", typed("", " "))
  }

  @Test
  fun `a picked suggestion speaks the finished word`() {
    assertEquals("hello", typed("hel", "hello "))
    assertEquals("hello", typed("hi ", "hi hello"))
  }

  @Test
  fun `backspace says what it removed`() {
    assertEquals("o deleted", typed("hello", "hell"))
    assertEquals("space deleted", typed("a ", "a"))
    assertEquals("hello deleted", typed("say hello", "say "))
  }

  @Test
  fun `clearing a sentence is quiet`() {
    assertNull(typed("see you soon", ""))
  }

  @Test
  fun `words only skips individual keys but still speaks words`() {
    assertNull(typed("hell", "hello", characters = false))
    assertEquals("hello", typed("hello", "hello ", characters = false))
    assertNull(typed("hello", "hell", characters = false))
  }

  @Test
  fun `characters only says space instead of the word`() {
    assertEquals("space", typed("hello", "hello ", words = false))
    assertNull(typed("hel", "hello ", words = false))
  }
}
