package com.example.mcoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val clicks = ArrayDeque<Long>()
    private var hud: TextView? = null
    private var cross: View? = null
    private var catcher: View? = null
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            updateHud()
            handler.postDelayed(this, 100)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        addHud()
        addTouchCatcher()
        if (prefs.getBoolean("cross", false)) addCrosshair()
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        listOf(hud, cross, catcher).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ov", "Overlay", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "ov")
            .setContentTitle("MC Overlay running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun params(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
    )

    private fun addHud() {
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            textSize = 14f
            setPadding(16, 8, 16, 8)
        }
        val lp = params(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 24 }
        wm.addView(tv, lp)
        hud = tv
    }

    // A 1x1 window that receives ACTION_OUTSIDE for every tap made anywhere else (i.e. in Minecraft).
    private fun addTouchCatcher() {
        val v = View(this)
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) {
                clicks.addLast(System.currentTimeMillis())
            }
            false
        }
        val lp = params(
            1, 1,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(v, lp)
        catcher = v
    }

    private fun addCrosshair() {
        val size = (28 * resources.displayMetrics.density).toInt()
        val v = object : View(this) {
            val outline = Paint().apply { color = Color.BLACK; strokeWidth = 7f; isAntiAlias = true }
            val line = Paint().apply { color = Color.WHITE; strokeWidth = 3f; isAntiAlias = true }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = width / 2f - 4f
                for (p in listOf(outline, line)) {
                    c.drawLine(cx - r, cy, cx + r, cy, p)
                    c.drawLine(cx, cy - r, cx, cy + r, p)
                }
            }
        }
        val lp = params(
            size, size,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply { gravity = Gravity.CENTER }
        wm.addView(v, lp)
        cross = v
    }

    private fun updateHud() {
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        while (clicks.isNotEmpty() && now - clicks.first > 1000) clicks.removeFirst()
        val parts = mutableListOf<String>()
        if (prefs.getBoolean("cps", true)) parts += "CPS ${clicks.size}"
        if (prefs.getBoolean("clock", true)) parts += timeFmt.format(Date(now))
        if (prefs.getBoolean("battery", true)) {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (lvl >= 0) parts += "${lvl * 100 / scale}%"
        }
        hud?.text = parts.joinToString("  |  ")
        hud?.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }
}
