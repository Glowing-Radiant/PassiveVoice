package com.passivevoice.interpreter

import com.passivevoice.speech.SpeechPriority

data class SpeechOutput(val text: String, val priority: SpeechPriority = SpeechPriority.NORMAL)
