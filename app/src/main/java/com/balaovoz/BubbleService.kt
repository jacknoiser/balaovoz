package com.balaovoz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.text.Normalizer
import kotlin.math.abs

class BubbleService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        recognizer?.destroy()
        bubble?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private fun startAsForeground() {
        val channelId = "balao"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Balão de Voz", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, channelId)
            .setContentTitle("Balão de Voz ativo")
            .setContentText("Toque no balão e diga o app que quer abrir")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(1, n)
    }

    private fun createBubble() {
        val density = resources.displayMetrics.density
        val size = (60 * density).toInt()

        val view = TextView(this).apply {
            text = "🎤"
            textSize = 24f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC1565C0"))
            }
            elevation = 8f
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20; y = 400
        }

        var startX = 0; var startY = 0
        var touchX = 0f; var touchY = 0f
        var moved = false
        var downTime = 0L

        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false; downTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        wm.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (System.currentTimeMillis() - downTime > 1200) {
                            stopSelf()
                        } else startListening()
                    }
                }
            }
            true
        }

        bubble = view
        wm.addView(view, params)
    }

    private fun setBubble(emoji: String, color: String) {
        bubble?.text = emoji
        (bubble?.background as? GradientDrawable)?.setColor(Color.parseColor(color))
    }

    private fun startListening() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Reconhecimento de voz indisponível. Instale/ative o app Google.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    listening = true
                    setBubble("👂", "#CCE53935")
                }
                override fun onResults(results: Bundle?) {
                    finish()
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) toast("Não ouvi nada") else handleCommand(text)
                }
                override fun onError(error: Int) {
                    finish()
                    toast("Não entendi, tente de novo")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                private fun finish() {
                    listening = false
                    setBubble("🎤", "#CC1565C0")
                }
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val filler = setOf(
        "abrir", "abre", "abra", "iniciar", "inicia", "open", "o", "a", "app",
        "aplicativo", "o app", "por", "favor", "para", "pra", "do", "da"
    )

    private fun handleCommand(spoken: String) {
        val query = norm(spoken).split(" ").filter { it !in filler }.joinToString(" ")
        if (query.isBlank()) { toast("Diga o nome do app"); return }

        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }

        var best: Pair<String, String>? = null
        var bestScore = 0
        for ((label, pkg) in apps) {
            val l = norm(label)
            if (l.isBlank()) continue
            val score = when {
                l == query -> 100
                l.contains(query) -> 80
                query.contains(l) -> 70
                else -> {
                    val common = query.split(" ").count { it.length > 2 && l.contains(it) }
                    common * 20
                }
            }
            if (score > bestScore) { bestScore = score; best = label to pkg }
        }

        val target = best
        if (target == null || bestScore < 20) {
            toast("App não encontrado: \"$spoken\"")
            return
        }
        val launch = pm.getLaunchIntentForPackage(target.second)
        if (launch == null) {
            toast("Não consegui abrir ${target.first}")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        toast("Abrindo ${target.first}")
        startActivity(launch)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
