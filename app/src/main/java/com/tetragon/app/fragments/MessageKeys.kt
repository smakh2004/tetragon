package com.tetragon.app.fragments

object MessageKeys {
    const val HELLO = "HELLO"
    const val WHO_WILL_PLAY = "WHO_WILL_PLAY"
    const val ME = "ME"
    const val LETS_GO = "LETS_GO"

    fun resolve(context: android.content.Context, key: String): String {
        val r = context.resources
        return when (key) {
            HELLO         -> r.getString(com.tetragon.app.R.string.hello)
            WHO_WILL_PLAY -> r.getString(com.tetragon.app.R.string.who_will_play)
            ME            -> r.getString(com.tetragon.app.R.string.me)
            LETS_GO       -> r.getString(com.tetragon.app.R.string.let_s_go)
            else          -> key
        }
    }
}