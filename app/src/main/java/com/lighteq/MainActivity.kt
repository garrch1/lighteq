package com.lighteq

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

private const val BG = 0xFF0E0F14.toInt()
private const val CARD = 0xFF181A22.toInt()
private const val CHIP = 0xFF242736.toInt()
private const val ACCENT = 0xFF7C5CFF.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val MUTED = 0xFF8D90A3.toInt()

class MainActivity : Activity() {
    private lateinit var p: SharedPreferences
    private lateinit var eqView: EqView
    private lateinit var content: LinearLayout
    private val chips = mutableListOf<TextView>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun label(t: String, sp: Float, color: Int = WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 20)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = EqEngine.prefs(this)
        EqEngine.start()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUi()
        if (p.getBoolean("on", false)) setOn(true)
    }

    private fun buildUi() {
        val root = ScrollView(this).apply { setBackgroundColor(BG) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        root.addView(col)

        // Шапка
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("LightEQ", 28f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this).apply {
            isChecked = p.getBoolean("on", false)
            setOnCheckedChangeListener { _, on -> setOn(on) }
        }
        header.addView(sw)
        col.addView(header)

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(content)

        val n = EqEngine.bands
        if (n == 0) {
            content.addView(label("Эквалайзер не поддерживается на этом устройстве", 15f, MUTED).apply {
                setPadding(0, dp(24), 0, 0)
            })
            setContentView(root)
            return
        }

        // Полосы
        val eqCard = card()
        eqCard.addView(label("Частоты, дБ", 13f, MUTED))
        eqView = EqView(this).apply {
            minL = EqEngine.minLevel
            maxL = EqEngine.maxLevel
            levels = IntArray(n) { p.getInt("b$it", 0).coerceIn(minL, maxL) }
            labels = EqEngine.freqsHz.map { fmtFreq(it) }
            onChange = { i, l ->
                p.edit().putInt("b$i", l).apply()
                EqEngine.setBand(i, l)
                if (p.getInt("preset", -1) != -1) select(-1)
            }
        }
        eqCard.addView(eqView, LinearLayout.LayoutParams(-1, dp(280)))
        content.addView(eqCard)

        // Пресеты
        val presetCard = card()
        presetCard.addView(label("Пресеты", 13f, MUTED))
        val row = LinearLayout(this).apply { setPadding(0, dp(10), 0, 0) }
        val names = listOf("Плоский") + EqEngine.presets
        names.forEachIndexed { idx, name ->
            val chip = label(name, 13f).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = rounded(CHIP, 16)
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) }
                setOnClickListener {
                    val lv = if (idx == 0) IntArray(n).also { zero ->
                        for (i in 0 until n) EqEngine.setBand(i, 0)
                    } else EqEngine.usePreset(idx - 1)
                    eqView.levels = lv
                    eqView.invalidate()
                    p.edit().apply { lv.forEachIndexed { i, l -> putInt("b$i", l) } }.apply()
                    select(idx)
                }
            }
            chips.add(chip)
            row.addView(chip)
        }
        presetCard.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        content.addView(presetCard)
        select(p.getInt("preset", -1))

        // Бас и объём
        val fxCard = card()
        fxCard.addView(slider("Усиление баса", "bass", EqEngine.bass?.strengthSupported == true) { EqEngine.setBass(it) })
        fxCard.addView(slider("Объёмный звук", "virt", EqEngine.virt?.strengthSupported == true) { EqEngine.setVirt(it) })
        content.addView(fxCard)

        setContentView(root)
    }

    private fun slider(title: String, key: String, supported: Boolean, onValue: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        box.addView(label(if (supported) title else "$title (не поддерживается)", 14f, if (supported) WHITE else MUTED))
        box.addView(SeekBar(this).apply {
            max = 1000
            progress = p.getInt(key, 0)
            isEnabled = supported
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    if (fromUser) { p.edit().putInt(key, v).apply(); onValue(v) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return box
    }

    private fun select(idx: Int) {
        p.edit().putInt("preset", idx).apply()
        chips.forEachIndexed { i, c -> c.background = rounded(if (i == idx) ACCENT else CHIP, 16) }
    }

    private fun fmtFreq(hz: Int): String =
        if (hz >= 1000) { if (hz % 1000 == 0) "${hz / 1000}k" else "%.1fk".format(hz / 1000f) } else "$hz"

    private fun setOn(on: Boolean) {
        p.edit().putBoolean("on", on).apply()
        if (on) {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            try {
                val i = Intent(this, EqService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            } catch (t: Throwable) { }
            EqEngine.apply(this)
        } else {
            EqEngine.disable()
            stopService(Intent(this, EqService::class.java))
        }
        content.alpha = if (on) 1f else 0.45f
    }
}
