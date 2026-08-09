package com.hh.music.player.ui.theme

import androidx.compose.ui.graphics.Color

/** User-facing theme mode, persisted as a string key in LocalStore. */
enum class AppThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Fixed accent palettes shown in Settings; dynamic color can override them on Android 12+. */
enum class AppThemeColor(val key: String, val label: String, val swatch: Color) {
    GREEN("green", "云岭绿", Color(0xFF3E6846)),
    BLUE("blue", "夜帆蓝", Color(0xFF2E6FA3)),
    ORANGE("orange", "炽阳橙", Color(0xFFB45E23));

    companion object {
        fun from(key: String?): AppThemeColor =
            entries.firstOrNull { it.key == key } ?: GREEN
    }
}

/** Lyric text size presets, persisted as a string key. */
enum class LyricFontScale(val key: String, val label: String) {
    SMALL("small", "小"),
    MEDIUM("medium", "中"),
    LARGE("large", "大");

    companion object {
        fun from(key: String?): LyricFontScale =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}
