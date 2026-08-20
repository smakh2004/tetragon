package com.tetragon.app.avatarSelection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.LruCache
import android.view.View
import android.widget.FrameLayout
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import com.tetragon.app.R

object AvatarBitmapRenderer {

    private val avatarNumberKeys = listOf("face", "hair", "glasses", "hat", "mustache", "body")

    // Cache rendered avatars by a config signature so identical looks aren't re-rendered
    private val cache = LruCache<String, Bitmap>(80)

    private fun configKey(config: Map<*, *>): String {
        val nums = avatarNumberKeys.joinToString(",") {
            (config[it] as? Number)?.toInt()?.toString() ?: "1"
        }
        val bg = (config["backgroundColor"] as? String) ?: "#00AEEF"
        return "$nums|$bg"
    }

    /**
     * Render an avatar config to a square bitmap.
     * Async — Rive needs a layout pass. [onReady] is called on the main thread
     * with the bitmap (or null on failure).
     */
    fun render(
        context: Context,
        config: Map<*, *>,
        sizePx: Int,
        onReady: (Bitmap?) -> Unit
    ) {
        val key = configKey(config)
        cache.get(key)?.let { onReady(it); return }

        val rive = RiveAnimationView(context).apply {
            setRiveResource(
                R.raw.avatar,
                stateMachineName = "State Machine 1",
                autoplay = true,
                fit = Fit.COVER
            )
        }

        // Host the view invisibly so it can lay out and draw
        val host = FrameLayout(context)
        host.addView(rive, FrameLayout.LayoutParams(sizePx, sizePx))

        rive.post {
            try {
                val file = rive.controller.file
                val vm = file?.getViewModelByName("ViewModel1")
                val vmi = vm?.createDefaultInstance()
                if (vmi != null) {
                    rive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi
                    avatarNumberKeys.forEach { k ->
                        val v = (config[k] as? Number)?.toInt() ?: 1
                        vmi.getNumberProperty(k)?.value = v.toFloat()
                        if (k == "hat") vmi.getBooleanProperty("hatOn")?.value = v > 1
                    }
                    (config["backgroundColor"] as? String)?.let { hex ->
                        runCatching { Color.parseColor(hex) }.getOrNull()?.let {
                            vmi.getColorProperty("backgroundColor")?.value = it
                        }
                    }
                }

                rive.measure(
                    View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
                )
                rive.layout(0, 0, sizePx, sizePx)

                // Let Rive advance one frame, then capture
                rive.postDelayed({
                    try {
                        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        rive.draw(canvas)
                        cache.put(key, bmp)
                        onReady(bmp)
                    } catch (e: Exception) {
                        onReady(null)
                    } finally {
                        rive.pause()
                        host.removeAllViews()
                    }
                }, 120)
            } catch (e: Exception) {
                onReady(null)
                rive.pause()
                host.removeAllViews()
            }
        }
    }
}