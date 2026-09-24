package com.passivevoice

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object SpeechSettings : NavKey

@Serializable data object AppList : NavKey

@Serializable data class AppSettings(val packageName: String) : NavKey

@Serializable data object DebugLog : NavKey
