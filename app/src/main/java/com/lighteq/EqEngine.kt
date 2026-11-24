package com.lighteq

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

/** Держит аудио-эффекты на глобальном выходе (сессия 0). */
object EqEngine {
    var eq: Equalizer? = null
    var bass: BassBoost? = null
    var virt: Virtualizer? = null

    var bands = 0
    var minLevel = -1500
    var maxLevel = 1500
    var freqsHz = IntArray(0)
    var presets: List<String> = emptyList()

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("eq", Context.MODE_PRIVATE)

    fun start() {
        if (eq == null) {
            try {
                val e = Equalizer(0, 0)
                eq = e
                bands = e.numberOfBands.toInt()
                val r = e.bandLevelRange
                minLevel = r[0].toInt()
                maxLevel = r[1].toInt()
                freqsHz = IntArray(bands) { e.getCenterFreq(it.toShort()) / 1000 }
                presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            } catch (t: Throwable) {
                eq = null
            }
        }
        if (bass == null) {
            try { bass = BassBoost(0, 0) } catch (t: Throwable) { bass = null }
        }
        if (virt == null) {
            try { virt = Virtualizer(0, 0) } catch (t: Throwable) { virt = null }
        }
    }

    fun apply(ctx: Context) {
        start()
        val p = prefs(ctx)
        try {
            eq?.let { e ->
                for (i in 0 until bands) {
                    val lv = p.getInt("b$i", 0).coerceIn(minLevel, maxLevel)
                    e.setBandLevel(i.toShort(), lv.toShort())
                }
                e.setEnabled(true)
            }
        } catch (t: Throwable) { }
        try {
            bass?.let {
                if (it.strengthSupported) it.setStrength(p.getInt("bass", 0).toShort())
                it.setEnabled(true)
            }
        } catch (t: Throwable) { }
        try {
            virt?.let {
                if (it.strengthSupported) it.setStrength(p.getInt("virt", 0).toShort())
                it.setEnabled(true)
            }
        } catch (t: Throwable) { }
    }

    fun disable() {
        try { eq?.setEnabled(false) } catch (t: Throwable) { }
        try { bass?.setEnabled(false) } catch (t: Throwable) { }
        try { virt?.setEnabled(false) } catch (t: Throwable) { }
    }

    fun setBand(i: Int, level: Int) {
        try { eq?.setBandLevel(i.toShort(), level.toShort()) } catch (t: Throwable) { }
    }

    fun usePreset(i: Int): IntArray {
        val e = eq ?: return IntArray(bands)
        return try {
            e.usePreset(i.toShort())
            IntArray(bands) { e.getBandLevel(it.toShort()).toInt() }
        } catch (t: Throwable) { IntArray(bands) }
    }

    fun setBass(v: Int) {
        try { bass?.let { if (it.strengthSupported) it.setStrength(v.toShort()) } } catch (t: Throwable) { }
    }

    fun setVirt(v: Int) {
        try { virt?.let { if (it.strengthSupported) it.setStrength(v.toShort()) } } catch (t: Throwable) { }
    }
}
