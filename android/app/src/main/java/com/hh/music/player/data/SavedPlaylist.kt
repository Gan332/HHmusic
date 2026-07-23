package com.hh.music.player.data

import kotlinx.serialization.Serializable

/**
 * A lightweight playlist reference for "saved playlists" (favorites tab).
 * Superset-enough to render cards and re-open a full playlist detail.
 */
@Serializable
data class SavedPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val creator: String = ""
)
