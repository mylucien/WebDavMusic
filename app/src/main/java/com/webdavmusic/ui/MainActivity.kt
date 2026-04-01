package com.webdavmusic.ui

import android.content.ComponentName
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.webdavmusic.R
import com.webdavmusic.databinding.ActivityMainBinding
import com.webdavmusic.player.PlaybackService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        connectToPlayer()
        observeMessages()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Bottom nav for phone/tablet; side nav for TV
        binding.bottomNav?.setupWithNavController(navController)
        binding.sideNav?.setupWithNavController(navController)
    }

    private fun connectToPlayer() {
        lifecycleScope.launch {
            viewModel.playerController.connect()
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            viewModel.uiMessage.collect { msg ->
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ─── D-Pad / Remote / Rotary Knob support ─────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                viewModel.togglePlayPause(); true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_BUTTON_R1 -> {
                viewModel.skipNext(); true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_BUTTON_L1 -> {
                viewModel.skipPrevious(); true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                viewModel.seekTo(viewModel.playerController.getCurrentPosition() + 10_000); true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                viewModel.seekTo(viewModel.playerController.getCurrentPosition() - 10_000); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        viewModel.playerController.disconnect()
        super.onDestroy()
    }
}
