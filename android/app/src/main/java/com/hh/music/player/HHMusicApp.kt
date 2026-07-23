package com.hh.music.player

import android.app.Application
import com.hh.music.player.data.AppContainer

class HHMusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
