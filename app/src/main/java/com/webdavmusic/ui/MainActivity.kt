package com.webdavmusic.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.webdavmusic.R
import com.webdavmusic.databinding.ActivityMainBinding
import com.webdavmusic.player.PlayerController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val vm: MainViewModel by viewModels()

    @Inject lateinit var playerController: PlayerController

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) vm.scanLocalMusic()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Connect player (non-suspending, uses ListenableFuture internally)
        playerController.connect()

        // Load the main fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainFragment())
                .commit()
        }

        lifecycleScope.launch {
            vm.messages.collect { msg ->
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    fun requestStoragePermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) vm.scanLocalMusic()
        else storagePermission.launch(perms)
    }

    // ── Hardware key handling: remotes, car steering wheel controls ────────────
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK      -> { vm.togglePlayPause(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY       -> { vm.togglePlayPause(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE      -> { vm.togglePlayPause(); true }
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_BUTTON_R1        -> { vm.skipNext(); true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_BUTTON_L1        -> { vm.skipPrevious(); true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                vm.seekTo(playerController.currentPosition() + 10_000); true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                vm.seekTo(playerController.currentPosition() - 10_000); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        playerController.disconnect()
        super.onDestroy()
    }
}
