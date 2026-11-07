package com.example.speech.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.speech.SpeechApp
import com.example.speech.presentation.TranscriptViewModel
import com.example.speech.service.CaptureService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: TranscriptViewModel by viewModels {
        TranscriptViewModel.Factory((application as SpeechApp).repository)
    }
    private lateinit var text: TextView
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) startCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        status = TextView(this)
        val row = LinearLayout(this)
        row.addView(Button(this).apply { this.text = "Старт"; setOnClickListener { requestAndStart() } })
        row.addView(Button(this).apply { this.text = "Стоп"; setOnClickListener { stopCapture() } })
        text = TextView(this).apply { textSize = 16f }
        scroll = ScrollView(this).apply { addView(text) }
        root.addView(status); root.addView(row)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.transcript.combine(vm.status) { t, s -> t to s }.collect { (segments, st) ->
                    status.text = st
                    text.text = segments.joinToString("\n") { "[Спикер ${it.speaker + 1}] ${it.text}" }
                    scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
    }

    private fun requestAndStart() {
        perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun startCapture() {
        ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java))
        vm.setStatus("Запись…")
    }
    private fun stopCapture() {
        startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
        vm.setStatus("Остановлено")
    }
}
