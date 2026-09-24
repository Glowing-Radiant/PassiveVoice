package com.passivevoice.interpreter

import com.passivevoice.core.ContextManager
import com.passivevoice.core.UserAction
import com.passivevoice.settings.Verbosity

/** Turns `UserAction + context + previous state` into speech, or nothing at all. */
fun interface ActionInterpreter {
  fun interpret(action: UserAction, context: ContextManager, verbosity: Verbosity): SpeechOutput?
}
