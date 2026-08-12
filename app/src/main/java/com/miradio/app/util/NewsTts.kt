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

    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
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
            }
        }
    }

    /** Devuelve false si el motor de voz no está listo (p. ej. sin ningún
     *  motor de texto a voz instalado en el sistema), para que la pantalla
     *  pueda avisar en vez de quedarse callada sin explicación. */
    fun speak(text: String): Boolean {
        if (!ready || text.isBlank()) return false
        _isSpeaking.value = true
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "news_article")
        return true
    }

    fun stop() {
        engine?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
    }
}
