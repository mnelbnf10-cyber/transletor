package com.example.instanttranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val requestCode = 44
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        val start = findViewById<Button>(R.id.start)
        val stop = findViewById<Button>(R.id.stop)

        start.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode + 1)
            }
            val intent = Intent(this, TranslationService::class.java)
            ContextCompat.startForegroundService(this, intent)
            status.text = "يعمل الآن… تكلم بشكل طبيعي"
        }

        stop.setOnClickListener {
            stopService(Intent(this, TranslationService::class.java))
            status.text = "متوقف"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 44 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            findViewById<Button>(R.id.start).performClick()
        }
    }
}
