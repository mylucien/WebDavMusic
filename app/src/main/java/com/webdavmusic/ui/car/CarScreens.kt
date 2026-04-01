package com.webdavmusic.ui.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import com.webdavmusic.R

/**
 * Car session - creates the initial car screen
 */
class CarMusicSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = CarHomeScreen(carContext)
}

/**
 * Main car screen - shows library and now playing
 * Optimized for rotary knob navigation and large touch targets
 */
class CarHomeScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Songs section header
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎵 所有歌曲")
                .addText("浏览并播放音乐库")
                .setOnClickListener { screenManager.push(CarSongListScreen(carContext)) }
                .isBrowsable(true)
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("💿 专辑")
                .addText("按专辑浏览")
                .setOnClickListener { screenManager.push(CarSongListScreen(carContext, "albums")) }
                .isBrowsable(true)
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 艺术家")
                .addText("按艺术家浏览")
                .setOnClickListener { screenManager.push(CarSongListScreen(carContext, "artists")) }
                .isBrowsable(true)
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("📋 播放列表")
                .addText("查看自动生成的播放列表")
                .setOnClickListener { screenManager.push(CarSongListScreen(carContext, "playlists")) }
                .isBrowsable(true)
                .build()
        )

        return ListTemplate.Builder()
            .setTitle("WebDAV 音乐")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}

/**
 * Car song list screen
 */
class CarSongListScreen(
    carContext: CarContext,
    private val mode: String = "songs"
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // In a real implementation, inject the repository and collect songs
        // For the car template, we show a placeholder that connects to the media session
        listBuilder.addItem(
            Row.Builder()
                .setTitle("正在加载...")
                .addText("请稍候")
                .build()
        )

        val title = when (mode) {
            "albums" -> "专辑"
            "artists" -> "艺术家"
            "playlists" -> "播放列表"
            else -> "所有歌曲"
        }

        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
