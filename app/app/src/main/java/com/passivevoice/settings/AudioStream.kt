package com.passivevoice.settings

import android.media.AudioAttributes

/** Which volume control PassiveVoice's speech follows (PRD §12 "Audio stream"). */
enum class AudioStream(val usage: Int, val label: String) {
  ACCESSIBILITY(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, "Accessibility volume, shared with TalkBack"),
  MEDIA(AudioAttributes.USAGE_MEDIA, "Media volume"),
  NOTIFICATION(AudioAttributes.USAGE_NOTIFICATION, "Notification volume"),
  ALARM(AudioAttributes.USAGE_ALARM, "Alarm volume"),
}
