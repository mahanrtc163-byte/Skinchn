package com.example.mcoverlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 18f; root.addView(this) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { f() }; root.addView(this)
        }
        fun chk(t: String, k: String, def: Boolean) = CheckBox(this).apply {
            text = t
            isChecked = prefs.getBoolean(k, def)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(k, v).apply() }
            root.addView(this)
        }

        label("MC Overlay")
        chk("Show CPS", "cps", true)
        chk("Show clock", "clock", true)
        chk("Show battery", "battery", true)
        chk("Crosshair (restart overlay after change)", "cross", false)

        btn("1) Allow draw over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2) Start overlay") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
            } else {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }
        btn("Stop overlay") { stopService(Intent(this, OverlayService::class.java)) }
        btn("Launch Minecraft") {
            val i = packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
            if (i != null) startActivity(i)
            else Toast.makeText(this, "Minecraft not found", Toast.LENGTH_LONG).show()
        }
        setContentView(root)
    }
}
