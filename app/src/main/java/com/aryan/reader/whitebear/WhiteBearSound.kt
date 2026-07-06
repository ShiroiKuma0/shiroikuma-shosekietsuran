package com.aryan.reader.whitebear

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Fork-local (白い熊) page-turn sound player. Loads the bundled page1..page5 ogg effects
 * into a SoundPool once and plays the selected one on a page-turn tap.
 */
class WhiteBearSound private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            // Route through the Media channel so it follows the media volume.
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    // index 1..5 -> SoundPool sample id (0 = not loaded)
    private val soundIds = IntArray(SOUND_COUNT + 1)

    init {
        for (index in 1..SOUND_COUNT) {
            val resId = appContext.resources.getIdentifier("page$index", "raw", appContext.packageName)
            if (resId != 0) {
                soundIds[index] = pool.load(appContext, resId, 1)
            }
        }
    }

    /** [index] 1..5; anything else is a no-op (e.g. 0 = off). */
    fun play(index: Int) {
        if (index in 1..SOUND_COUNT) {
            val sampleId = soundIds[index]
            if (sampleId > 0) {
                pool.play(sampleId, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    companion object {
        const val SOUND_COUNT = 5

        @Volatile
        private var instance: WhiteBearSound? = null

        fun get(context: Context): WhiteBearSound {
            return instance ?: synchronized(this) {
                instance ?: WhiteBearSound(context).also { instance = it }
            }
        }
    }
}
