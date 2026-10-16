package com.balaovoz

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER }
        val start = Button(this).apply {
            text = "Ativar balão"
            setOnClickListener { activate() }
        }
        val stop = Button(this).apply {
            text = "Desativar balão"
            setOnClickListener {
                stopService(Intent(this@MainActivity, BubbleService::class.java))
                status.text = "Balão desativado."
            }
        }
        root.addView(status); root.addView(start); root.addView(stop)
        setContentView(root)
        status.text = "Toque em \"Ativar balão\". Depois toque no balão e fale, por exemplo: \"abrir WhatsApp\"."
    }

    override fun onResume() {
        super.onResume()
        if (hasAll()) status.text = "Permissões ok. Toque em \"Ativar balão\"."
    }

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasAll() = hasMic() && Settings.canDrawOverlays(this)

    private fun activate() {
        val perms = mutableListOf<String>()
        if (!hasMic()) perms += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) perms += Manifest.permission.POST_NOTIFICATIONS
        if (perms.isNotEmpty()) {
            requestPermissions(perms.toTypedArray(), 1)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            status.text = "Permita \"Exibir sobre outros apps\" e volte aqui."
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        startForegroundService(Intent(this, BubbleService::class.java))
        status.text = "Balão ativo! Você pode sair do app."
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        if (hasMic()) activate() else status.text = "Sem permissão de microfone, o app não funciona."
    }
}
