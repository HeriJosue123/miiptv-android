@file:OptIn(UnstableApi::class)

package com.miiptv.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(channel: Channel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var errorText by remember { mutableStateOf<String?>(null) }

    val exo = remember(channel.url) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(http))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(channel.url))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorText = "No se pudo reproducir (${error.errorCodeName})"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) errorText = null
            }
        }
        exo.addListener(listener)

        val activity = ctx.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView)
        }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            exo.removeListener(listener)
            exo.release()
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    this.player = exo
                    useController = true
                    keepScreenOn = true
                }
            },
            update = { it.player = exo },
        )

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) { Text("← Volver", color = Color.White) }

        errorText?.let { msg ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(msg, color = Color.White)
                Button(onClick = {
                    errorText = null
                    exo.prepare()
                    exo.play()
                }) { Text("Reintentar") }
            }
        }
    }
}
