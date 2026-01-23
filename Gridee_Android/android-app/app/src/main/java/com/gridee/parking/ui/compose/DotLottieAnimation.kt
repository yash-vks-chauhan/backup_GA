package com.gridee.parking.ui.compose

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

sealed interface DotLottieSource {
    data class Url(val url: String) : DotLottieSource
}

enum class Mode {
    Forward,
    Reverse,
}

@Composable
fun DotLottieAnimation(
    source: DotLottieSource,
    autoplay: Boolean,
    loop: Boolean,
    speed: Float,
    useFrameInterpolation: Boolean,
    playMode: Mode,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LottieAnimationView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { view ->
            val resolvedSpeed = when (playMode) {
                Mode.Forward -> speed
                Mode.Reverse -> -kotlin.math.abs(speed)
            }

            view.repeatCount = if (loop) LottieDrawable.INFINITE else 0
            view.repeatMode = LottieDrawable.RESTART
            view.speed = resolvedSpeed

            try {
                val method = view.javaClass.getMethod(
                    "setUseCompositionFrameRate",
                    Boolean::class.javaPrimitiveType,
                )
                method.invoke(view, !useFrameInterpolation)
            } catch (_: Exception) {
                // Older Lottie versions may not expose this API; ignore.
            }

            when (source) {
                is DotLottieSource.Url -> {
                    val url = source.url
                    if (view.tag != url) {
                        view.tag = url
                        view.setAnimationFromUrl(url)
                    }
                }
            }

            if (autoplay) {
                view.playAnimation()
            } else {
                view.pauseAnimation()
            }
        },
    )
}

