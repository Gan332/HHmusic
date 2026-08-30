package com.hh.music.player.ui.theme

/** Selectable UI skin. Persisted by [com.hh.music.player.data.local.LocalStore.uiStyle]. */
enum class UiStyle(val key: String, val displayName: String) {
    MATERIAL("material", "Material 3（原版）"),
    MIUIX("miuix", "MIUI 风格");

    companion object {
        fun from(key: String?): UiStyle = entries.firstOrNull { it.key == key } ?: MATERIAL
    }
}
