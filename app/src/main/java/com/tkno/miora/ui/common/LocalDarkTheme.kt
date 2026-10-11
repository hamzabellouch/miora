package com.tkno.miora.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.tkno.miora.util.DarkThemePreference

val LocalDarkTheme = compositionLocalOf { DarkThemePreference() }
