package com.webdavmusic.ui.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.webdavmusic.R
import com.webdavmusic.WebDavMusicApp
import com.webdavmusic.data.model.Song
import com.webdavmusic.data.repository.MusicRepository
import com.webdavmusic.player.PlayerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Car App Service 入口
 */
class CarMusicService : CarAppService() {
    override fun createHostValidator() = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = CarMusicSession()
}

class CarMusicSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = CarHomeScreen(carContext)
}

/**
 * Car App 依赖入口点
 */
interface CarDependencies {
    fun repository(): MusicRepository
    fun playerController(): PlayerController
}

/**
 * 获取 Car App 依赖的辅助方法
 */
private fun CarContext.getDependencies(): CarDependencies {
    return (applicationContext as WebDavMusicApp).carDependencies
}

/**
 * 主屏幕 - 显示主要功能入口
 */
class CarHomeScreen(ctx: CarContext) : Screen(ctx) {
    
    private val dependencies: CarDependencies by lazy { carContext.getDependencies() }
    private val repository: MusicRepository by lazy { dependencies.repository() }
    private val playerController: PlayerController by lazy { dependencies.playerController() }
    
    override fun onGetTemplate(): Template {
        // 观察播放状态
        lifecycleScope.launch {
            playerController.state.collectLatest { 
                invalidate()
            }
        }
        
        val state = playerController.state.value
        val isPlaying = state.isPlaying
        
        return GridTemplate.Builder()
            .setTitle("WebDAV Music")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(if (isPlaying) "⏸" else "▶")
                            .setOnClickListener {
                                playerController.togglePlayPause()
                            }
                            .build()
                    )
                    .build()
            )
            .setSingleList(
                ItemList.Builder()
                    // 所有歌曲
                    .addItem(
                        GridItem.Builder()
                            .setTitle("🎵 所有歌曲")
                            .setText("播放全部")
                            .setImage(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_music_note_large)
                                ).build()
                            )
                            .setOnClickListener {
                                screenManager.push(
                                    CarSongListScreen(carContext, "所有歌曲", repository.getAllSongs())
                                )
                            }
                            .build()
                    )
                    // 本地音乐
                    .addItem(
                        GridItem.Builder()
                            .setTitle("📱 本地音乐")
                            .setText("设备存储")
                            .setImage(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_phone)
                                ).build()
                            )
                            .setOnClickListener {
                                screenManager.push(
                                    CarSongListScreen(carContext, "本地音乐", repository.getLocalSongs())
                                )
                            }
                            .build()
                    )
                    // 收藏
                    .addItem(
                        GridItem.Builder()
                            .setTitle("❤ 我的收藏")
                            .setText("收藏的歌曲")
                            .setImage(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_favorite)
                                ).build()
                            )
                            .setOnClickListener {
                                screenManager.push(
                                    CarSongListScreen(carContext, "我的收藏", repository.getFavorites())
                                )
                            }
                            .build()
                    )
                    // 播放列表
                    .addItem(
                        GridItem.Builder()
                            .setTitle("📋 播放列表")
                            .setText("自定义列表")
                            .setImage(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_playlist)
                                ).build()
                            )
                            .setOnClickListener {
                                screenManager.push(CarPlaylistScreen(carContext))
                            }
                            .build()
                    )
                    // 设置
                    .addItem(
                        GridItem.Builder()
                            .setTitle("⚙ 设置")
                            .setText("账户管理")
                            .setImage(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_refresh)
                                ).build()
                            )
                            .setOnClickListener {
                                screenManager.push(CarSettingsScreen(carContext))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

/**
 * 歌曲列表屏幕
 */
class CarSongListScreen(
    ctx: CarContext,
    private val title: String,
    private val songsFlow: Flow<List<Song>>
) : Screen(ctx) {
    
    private val dependencies: CarDependencies by lazy { carContext.getDependencies() }
    private val playerController: PlayerController by lazy { dependencies.playerController() }
    
    private var songs = listOf<Song>()
    
    override fun onGetTemplate(): Template {
        lifecycleScope.launch {
            songsFlow.collectLatest { list ->
                songs = list
                invalidate()
            }
        }
        
        val builder = ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
        
        if (songs.isEmpty()) {
            builder.setLoading(false)
        } else {
            val itemList = ItemList.Builder()
            songs.forEachIndexed { index, song ->
                itemList.addItem(
                    Row.Builder()
                        .setTitle(song.title)
                        .addText("${song.artist} - ${song.album}")
                        .setOnClickListener {
                            playerController.playSongs(songs, index)
                        }
                        .build()
                )
            }
            builder.setSingleList(itemList.build())
        }
        
        return builder.build()
    }
}

/**
 * 播放列表屏幕
 */
class CarPlaylistScreen(ctx: CarContext) : Screen(ctx) {
    
    private val dependencies: CarDependencies by lazy { carContext.getDependencies() }
    private val repository: MusicRepository by lazy { dependencies.repository() }
    
    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setTitle("播放列表")
            .setHeaderAction(Action.BACK)
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("暂无播放列表")
                            .addText("请在手机端创建播放列表")
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

/**
 * 设置屏幕
 */
class CarSettingsScreen(ctx: CarContext) : Screen(ctx) {
    
    private val dependencies: CarDependencies by lazy { carContext.getDependencies() }
    private val repository: MusicRepository by lazy { dependencies.repository() }
    
    override fun onGetTemplate(): Template {
        return ListTemplate.Builder()
            .setTitle("设置")
            .setHeaderAction(Action.BACK)
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("扫描音乐")
                            .addText("扫描所有 WebDAV 账户")
                            .setOnClickListener {
                                lifecycleScope.launch {
                                    repository.scanAllAccounts()
                                }
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("扫描本地音乐")
                            .addText("扫描设备存储")
                            .setOnClickListener {
                                lifecycleScope.launch {
                                    repository.scanLocalMusic()
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
