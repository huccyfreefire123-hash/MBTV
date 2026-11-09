package com.ondatv.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ondatv.player.data.Progress
import com.ondatv.player.data.Store
import com.ondatv.player.ui.NextEpisodeButton
import com.ondatv.player.ui.OndaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What to play. [queue] is the whole series in order (season, then episode) when an episode is
 * started, so the next one follows by itself; empty for films and channels.
 */
data class PlayRequest(
    val key: String,
    val title: String,
    val url: String,
    val logo: String?,
    val live: Boolean,
    val queue: List<PlayRequest> = emptyList(),
)

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private lateinit var view: PlayerView
    private lateinit var store: Store
    private var player: ExoPlayer? = null

    private var queue: List<PlayRequest> = emptyList()
    private var live = false
    private var startIndex = 0
    private var playingIndex = 0
    private var lastDuration = 0L

    // Read by the Compose overlay.
    private var nextLabel by mutableStateOf<String?>(null)
    private var secondsLeft by mutableStateOf(0)
    private var nextTarget = -1
    private var inPip by mutableStateOf(false)

    private var aspect = Rational(16, 9)
    private val pipSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val p = player ?: return
            when (intent.getStringExtra(EXTRA_WHAT)) {
                WHAT_TOGGLE -> if (p.isPlaying) p.pause() else p.play()
                WHAT_NEXT -> playNext(-1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Films, episodes and channels play in landscape (either side, following the sensor).
        // The activity handles orientation changes itself, so turning never restarts the video.
        if (!isTv()) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Use the whole screen, also beside a camera cutout.
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        store = Store(this)
        readQueue(intent)
        view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
        }
        val overlay = ComposeView(this).apply {
            setContent {
                OndaTheme {
                    NextEpisodeButton(
                        label = if (inPip) null else nextLabel,
                        secondsLeft = secondsLeft,
                        onClick = { playNext(nextTarget) },
                    )
                }
            }
        }
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                overlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END,
                ).apply {
                    rightMargin = (24 * density).toInt()
                    bottomMargin = (96 * density).toInt()
                },
            )
        }
        setContentView(root)
        hideSystemBars()
        // Keep the controls and the button out of the camera cutout (the safe area).
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val c = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(c.left, c.top, c.right, c.bottom)
            insets
        }

        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)

        // Watches the time left: shows the button and saves progress now and then.
        lifecycleScope.launch {
            var tick = 0
            while (true) {
                delay(500)
                val p = player ?: continue
                if (live) continue
                val d = p.duration
                if (d > 0 && d != C.TIME_UNSET) lastDuration = d
                val left = if (d > 0) d - p.currentPosition else Long.MAX_VALUE
                val next = queue.getOrNull(p.currentMediaItemIndex + 1)
                if (next != null && p.hasNextMediaItem() && left in 0..60_000) {
                    nextTarget = p.currentMediaItemIndex + 1
                    nextLabel = next.title
                    secondsLeft = ((left + 999) / 1000).toInt()
                } else {
                    nextLabel = null
                }
                if (++tick % 20 == 0) saveProgress()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveProgress()
        readQueue(intent)
        player?.let { play(it) }
    }

    override fun onStart() {
        super.onStart()
        if (player != null) return
        val p = buildPlayer(this)
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    getString(R.string.play_error, error.errorCodeName),
                    Toast.LENGTH_LONG,
                ).show()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Reached by itself at the end of the previous episode: mark that one as watched.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) markWatched(playingIndex)
                playingIndex = p.currentMediaItemIndex
                startIndex = playingIndex
                nextLabel = null
                updatePip()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !live) {
                    markWatched(p.currentMediaItemIndex)
                    finish()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Paused (or stopped to buffer): keep the exact point.
                if (!isPlaying) saveProgress()
                updatePip()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val w = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
                    // Android accepts ratios between 1:2.39 and 2.39:1.
                    aspect = when {
                        w * 100 > videoSize.height * 239 -> Rational(239, 100)
                        w * 239 < videoSize.height * 100 -> Rational(100, 239)
                        else -> Rational(w, videoSize.height)
                    }
                    updatePip()
                }
            }
        })
        view.player = p
        player = p
        play(p)
    }

    private fun play(p: ExoPlayer) {
        val items = queue.map { mediaItemFor(it.url).buildUpon().setMediaId(it.key).build() }
        val first = queue.getOrNull(startIndex) ?: return
        var position = C.TIME_UNSET
        if (live) {
            store.addRecentLive(first.key)
        } else {
            val saved = store.progress()[first.key]
            // Only when it was saved for this very address, and not finished (95%): a finished
            // film starts again from the beginning and gets a new record.
            if (saved != null && saved.url == first.url && saved.position < saved.duration * 0.95) position = saved.position
        }
        playingIndex = startIndex
        p.setMediaItems(items, startIndex, position)
        p.prepare()
        p.playWhenReady = true
        updatePip()
    }

    /**
     * Starts episode [target] now (the one the button shows), or the one after the current when
     * [target] is -1 (PiP window). Does nothing when that episode has already begun by itself, so
     * a late press can never skip an episode or start one twice.
     */
    private fun playNext(target: Int = -1) {
        val p = player ?: return
        val index = if (target >= 0) target else p.currentMediaItemIndex + 1
        if (index <= p.currentMediaItemIndex || index >= p.mediaItemCount) return
        nextLabel = null
        saveProgress()
        p.seekToDefaultPosition(index)
        p.play()
    }

    // Saved here too: the screen behind resumes before this one stops.
    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        view.player = null
        player?.release()
        player = null
        // Closing the picture-in-picture window stops the activity: leave it.
        if (isInPictureInPictureMode) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipReceiver)
    }

    private fun saveProgress() {
        val p = player ?: return
        if (live) return
        val r = queue.getOrNull(p.currentMediaItemIndex) ?: return
        val duration = p.duration
        if (duration <= 0 || duration == C.TIME_UNSET) return
        store.saveProgress(Progress(r.key, r.title, r.logo, r.url, p.currentPosition, duration, System.currentTimeMillis()))
    }

    private fun markWatched(index: Int) {
        val r = queue.getOrNull(index) ?: return
        if (live || lastDuration <= 0) return
        store.saveProgress(Progress(r.key, r.title, r.logo, r.url, lastDuration, lastDuration, System.currentTimeMillis()))
    }

    // ---------- Picture-in-picture ----------

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(aspect).setActions(pipActions())
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+: goes to the floating window by itself when leaving while playing.
            b.setAutoEnterEnabled(player?.isPlaying == true)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun updatePip() {
        if (!pipSupported) return
        try {
            setPictureInPictureParams(pipParams())
        } catch (e: IllegalStateException) {
            // Some devices refuse while the activity is not visible; the next update retries.
        }
    }

    private fun pipActions(): List<RemoteAction> {
        val p = player
        val playing = p?.isPlaying == true
        val list = mutableListOf(
            remoteAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pausar" else "Reproduzir", WHAT_TOGGLE, 1,
            )
        )
        if (!live && p?.hasNextMediaItem() == true) {
            list += remoteAction(android.R.drawable.ic_media_next, "Próximo episódio", WHAT_NEXT, 2)
        }
        return list
    }

    private fun remoteAction(icon: Int, title: String, what: String, code: Int): RemoteAction {
        val intent = Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_WHAT, what)
        val pending = PendingIntent.getBroadcast(
            this, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pending)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Before Android 12 the window has to be asked for when the user leaves.
        if (pipSupported && Build.VERSION.SDK_INT < 31 && player?.isPlaying == true) {
            try {
                enterPictureInPictureMode(pipParams())
            } catch (e: IllegalStateException) {
                // Not allowed right now (e.g. turned off for this app in system settings).
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        view.useController = !isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            if (lifecycle.currentState == Lifecycle.State.CREATED) {
                // The floating window was closed (not expanded): stop here.
                finish()
            } else {
                hideSystemBars()
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun readQueue(i: Intent) {
        val keys = i.getStringArrayListExtra("keys")
        val titles = i.getStringArrayListExtra("titles")
        val urls = i.getStringArrayListExtra("urls")
        val logos = i.getStringArrayListExtra("logos")
        live = i.getBooleanExtra("live", false)
        queue = if (keys != null && titles != null && urls != null && logos != null) {
            keys.indices.map { n -> PlayRequest(keys[n], titles[n], urls[n], logos[n].ifEmpty { null }, live) }
        } else {
            emptyList()
        }
        startIndex = i.getIntExtra("index", 0).coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    }

    companion object {
        private const val ACTION_PIP = "com.ondatv.player.PIP_ACTION"
        private const val EXTRA_WHAT = "what"
        private const val WHAT_TOGGLE = "toggle"
        private const val WHAT_NEXT = "next"

        fun intent(context: Context, r: PlayRequest): Intent {
            val list = r.queue.ifEmpty { listOf(r) }
            val index = list.indexOfFirst { it.key == r.key }.coerceAtLeast(0)
            return Intent(context, PlayerActivity::class.java)
                .putStringArrayListExtra("keys", ArrayList(list.map { it.key }))
                .putStringArrayListExtra("titles", ArrayList(list.map { it.title }))
                .putStringArrayListExtra("urls", ArrayList(list.map { it.url }))
                .putStringArrayListExtra("logos", ArrayList(list.map { it.logo.orEmpty() }))
                .putExtra("index", index)
                .putExtra("live", r.live)
        }
    }
}
