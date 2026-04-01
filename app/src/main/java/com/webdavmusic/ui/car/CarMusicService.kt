package com.webdavmusic.ui.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Automotive OS (AAOS) entry point.
 * Provides a simplified car-optimized UI via the Car App Library.
 */
class CarMusicService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = CarMusicSession()
}
