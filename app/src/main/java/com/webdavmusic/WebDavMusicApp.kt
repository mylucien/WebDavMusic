package com.webdavmusic

import android.app.Application
import com.webdavmusic.data.repository.MusicRepository
import com.webdavmusic.player.PlayerController
import com.webdavmusic.ui.car.CarDependencies
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class WebDavMusicApp : Application() {
    
    @Inject lateinit var repository: MusicRepository
    @Inject lateinit var playerController: PlayerController
    
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
    
    /**
     * 提供给 Car App 的依赖入口
     */
    val carDependencies: CarDependencies by lazy {
        object : CarDependencies {
            override fun repository(): MusicRepository = repository
            override fun playerController(): PlayerController = playerController
        }
    }
}
