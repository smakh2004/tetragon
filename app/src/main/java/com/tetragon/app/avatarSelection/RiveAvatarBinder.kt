package com.tetragon.app.avatarSelection

import android.graphics.Color
import app.rive.runtime.kotlin.RiveAnimationView

object RiveAvatarBinder {

    private val numberKeys = listOf("face", "hair", "glasses", "hat", "mustache", "body")
    private val colorKeys = listOf("skinColor", "hairColor", "glassColor", "capColor", "mustacheColor", "clothColor")

    fun apply(rive: RiveAnimationView, config: Map<*, *>) {
        rive.post {
            try {
                val file = rive.controller.file ?: return@post
                val vm = file.getViewModelByName("ViewModel1") ?: return@post
                val vmi = vm.createDefaultInstance()
                rive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

                numberKeys.forEach { key ->
                    val value = (config[key] as? Number)?.toInt() ?: 1
                    vmi.getNumberProperty(key)?.value = value.toFloat()
                    if (key == "hat") vmi.getBooleanProperty("hatOn")?.value = value > 1
                }

                (config["backgroundColor"] as? String)?.let { hex ->
                    runCatching { Color.parseColor(hex) }.getOrNull()?.let {
                        vmi.getColorProperty("backgroundColor")?.value = it
                    }
                }

                colorKeys.forEach { key ->
                    (config[key] as? String)?.let { hex ->
                        runCatching { Color.parseColor(hex) }.getOrNull()?.let {
                            vmi.getColorProperty(key)?.value = it
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RiveAvatarBinder", "apply error: ${e.message}")
            }
        }
    }
}