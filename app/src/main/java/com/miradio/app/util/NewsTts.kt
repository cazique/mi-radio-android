package com.miradio.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Lee en voz alta el título y el resumen de una noticia con el motor de
 * texto a voz del propio Android: no depende de ningún servicio externo ni
 * de conexión (si el teléfono tiene un motor de voz instalado, que lo
 * traen casi todos de fábrica, funciona sin más). Pensado para quien
 * prefiere escuchar la noticia en vez de leerla en pantalla.
 */
class NewsTts(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var engine: TextToSpeech? = null
    private var ready = false
    // El motor tarda un instante en inicializarse (llamada asíncrona); si se
    // toca "Escuchar" antes de que termine, el texto se guarda aquí y se lee
    // en cuanto esté listo, en vez de rechazarlo sin más (lo que se reportó
    // como "dice que el teléfono no tiene motor de voz" al tocar justo
    // después de abrir la noticia).
    private var pendingText: String? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    engine?.language = Locale("es", "ES")
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            mainHandler.post { _isSpeaking.value = false }
                        }
                        @Deprecated("Deprecated in Java", ReplaceWith(""))
                        override fun onError(utteranceId: String?) {
                            mainHandler.post { _isSpeaking.value = false }
                        }
                    })
                    pendingText?.let { text ->
                        pendingText = null
                        speakNow(text)
                    }
                } else {
                    _error.value = "El teléfono no tiene ningún motor de voz instalado."
                    _isSpeaking.value = false
                    pendingText = null
                }
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        _error.value = null
        _isSpeaking.value = true
        if (ready) {
            speakNow(text)
        } else {
            pendingText = text
        }
    }

    private fun speakNow(text: String) {
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "news_article")
    }

    fun stop() {
        pendingText = null
        engine?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        pendingText = null
        engine?.stop()
        engine?.shutdown()
    }
}
