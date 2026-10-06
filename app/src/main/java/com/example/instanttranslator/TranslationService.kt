package com.example.instanttranslator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class TranslationService : Service(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var currentDetected = "en"
    private var running = false
    private var lastSpoken = ""

    private val allowed = arrayListOf(
        "en-US", "fr-FR", "es-ES", "de-DE", "it-IT", "pt-PT", "tr-TR", "ru-RU",
        "ar-SA", "hi-IN", "ja-JP", "ko-KR", "cmn-Hans-CN", "nl-NL"
    )

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7, notification())
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar", "SA")
                tts?.setSpeechRate(1.05f)
            }
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        running = true
        startListening()
    }

    private fun startListening() {
        if (!running || recognizer == null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
            if (Build.VERSION.SDK_INT >= 34) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, allowed)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, allowed)
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { restartSoon() }
    }

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (text.isNotEmpty()) processText(text, currentDetected)
        restartSoon()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // We intentionally translate final chunks only to avoid speaking unstable partial text.
    }

    override fun onLanguageDetection(results: Bundle) {
        if (Build.VERSION.SDK_INT >= 34) {
            currentDetected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE)?.substringBefore('-')?.lowercase() ?: currentDetected
        }
    }

    private fun processText(text: String, detected: String) {
        if (text == lastSpoken) return
        lastSpoken = text
        val source = languageCode(detected) ?: run {
            LanguageIdentification.getClient().identifyLanguage(text)
                .addOnSuccessListener { code -> translateAndSpeak(text, languageCode(code) ?: TranslateLanguage.ENGLISH) }
            return
        }
        translateAndSpeak(text, source)
    }

    private fun translateAndSpeak(text: String, source: String) {
        if (source == TranslateLanguage.ARABIC) {
            speak(text)
            return
        }
        val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.ARABIC).build()
        val translator: Translator = Translation.getClient(options)
        translator.downloadModelIfNeeded().addOnSuccessListener {
            translator.translate(text).addOnSuccessListener { translated ->
                speak(translated)
                translator.close()
            }.addOnFailureListener { translator.close() }
        }.addOnFailureListener { translator.close() }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "translation_${System.nanoTime()}")
    }

    private fun languageCode(code: String?): String? = when (code?.substringBefore('-')?.lowercase()) {
        "en" -> TranslateLanguage.ENGLISH
        "fr" -> TranslateLanguage.FRENCH
        "es" -> TranslateLanguage.SPANISH
        "de" -> TranslateLanguage.GERMAN
        "it" -> TranslateLanguage.ITALIAN
        "pt" -> TranslateLanguage.PORTUGUESE
        "tr" -> TranslateLanguage.TURKISH
        "ru" -> TranslateLanguage.RUSSIAN
        "ar" -> TranslateLanguage.ARABIC
        "hi" -> TranslateLanguage.HINDI
        "ja" -> TranslateLanguage.JAPANESE
        "ko" -> TranslateLanguage.KOREAN
        "zh" -> TranslateLanguage.CHINESE
        "nl" -> TranslateLanguage.DUTCH
        else -> null
    }

    private fun restartSoon() {
        if (!running) return
        android.os.Handler(mainLooper).postDelayed({ startListening() }, 250)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("translator", getString(com.example.instanttranslator.R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification() = NotificationCompat.Builder(this, "translator")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle(getString(com.example.instanttranslator.R.string.notification_title))
        .setContentText(getString(com.example.instanttranslator.R.string.notification_text))
        .setOngoing(true)
        .build()

    override fun onError(error: Int) { if (running) restartSoon() }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onResults(results: Bundle?, partialResults: Boolean) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        running = false
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
