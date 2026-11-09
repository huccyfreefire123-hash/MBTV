package com.ondatv.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.ondatv.player.data.Net

/**
 * One player set up for IPTV servers. [small] caps the picture at 960x540, which keeps
 * four players at once within what phones can decode.
 */
@OptIn(UnstableApi::class)
fun buildPlayer(context: Context, small: Boolean = false): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Net.userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val builder = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
    if (small) {
        val selector = DefaultTrackSelector(context)
        selector.setParameters(selector.buildUponParameters().setMaxVideoSize(960, 540))
        builder.setTrackSelector(selector)
    }
    return builder.build()
}

/** Android TV / Smart TV: orientation is never forced there. */
fun Context.isTv(): Boolean =
    (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

/** A phone (not a TV, not a tablet): the app screens stay in portrait, the player in landscape. */
fun Context.isPhone(): Boolean = !isTv() && resources.configuration.smallestScreenWidthDp < 600

fun mediaItemFor(url: String): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return builder.build()
}
