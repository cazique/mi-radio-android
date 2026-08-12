package com.miradio.app.playback

import com.miradio.app.domain.model.RadioStation
import java.text.Normalizer

/**
 * Interpreta la frase libre que Android Auto/Assistant entrega cuando el
 * usuario dice "Ok Google, reproduce <frase> en Radio Dari": es el mismo
 * mecanismo con el que Spotify o TuneIn responden a "pon <canción> en
 * Spotify". La frase completa llega tal cual (sin estructura), así que aquí
 * se interpreta con reglas sencillas en vez de depender de un servicio de
 * lenguaje natural externo.
 *
 * Ejemplos que reconoce:
 *  - "cope madrid" -> reproducir COPE Madrid
 *  - "cope madrid con un retraso de 5 segundos" -> reproducir + retardo 5s
 *  - "retrasa 3 segundos la radio" -> solo cambia el retardo, sin tocar la emisora
 *  - "anula el retardo" / "quita el retraso" -> retardo a 0
 */
sealed class VoiceCommand {
    data class PlayStation(val stationQuery: String, val delaySeconds: Int?) : VoiceCommand()
    data class SetDelay(val delaySeconds: Int) : VoiceCommand()
    object ClearDelay : VoiceCommand()
    object Unrecognized : VoiceCommand()
}

object VoiceCommandParser {

    // "retardo" y "retraso" son ambos válidos en español para lo mismo
    // ("retardo" es el término técnico que usa la propia app en Ajustes,
    // pero la mayoría dice "retraso" al hablar); (?:...) sin capturar para
    // no desplazar la numeración de los grupos que sí se usan más abajo.
    private val delayWord = "(?:retard[oa]|retras[oa])"
    private val delayPhraseRegex = Regex(
        "\\bcon\\s+(un\\s+)?$delayWord\\s+de\\s+(\\d+)\\s*segundos?\\b|" +
            "\\bcon\\s+(\\d+)\\s*segundos?\\s+de\\s+$delayWord\\b|" +
            "\\bretrasad[oa]\\s+(\\d+)\\s*segundos?\\b",
    )
    private val delayOnlyRegex = Regex(
        "\\b(retrasa|retrasar|retardo|retardar|retraso|atrasa|atrasar|pon)\\b.*\\b(\\d+)\\s*segundos?\\b",
    )
    private val clearDelayRegex = Regex(
        "\\b(anula|anular|quita|quitar|cancela|cancelar|desactiva|desactivar|elimina|eliminar)\\b.*\\b(retard|retras)",
    )
    private val standaloneNumberSecondsRegex = Regex("\\b(\\d+)\\s*segundos?\\b")

    fun parse(rawQuery: String): VoiceCommand {
        val normalized = normalize(rawQuery)
        if (normalized.isBlank()) return VoiceCommand.Unrecognized

        if (clearDelayRegex.containsMatchIn(normalized)) {
            return VoiceCommand.ClearDelay
        }

        // "retrasa/retardo X segundos [la radio]" sin nombre de emisora
        // reconocible: se trata como ajuste de retardo puro.
        val delayOnlyMatch = delayOnlyRegex.find(normalized)
        if (delayOnlyMatch != null && looksLikeDelayOnlyCommand(normalized)) {
            val seconds = delayOnlyMatch.groupValues[2].toIntOrNull()
            if (seconds != null) return VoiceCommand.SetDelay(seconds.coerceIn(0, 300))
        }

        // Emisora + posible retardo en la misma frase.
        val delayMatch = delayPhraseRegex.find(normalized)
        val delaySeconds = delayMatch?.groupValues
            ?.drop(1)
            ?.firstOrNull { it.isNotBlank() && it.toIntOrNull() != null }
            ?.toIntOrNull()

        val stationQuery = normalized
            .let { if (delayMatch != null) it.replace(delayMatch.value, " ") else it }
            .let { standaloneNumberSecondsRegex.replace(it, " ") }
            .replace(Regex("\\b(en|la)\\s+radio\\s+dari\\b"), " ")
            .trim()

        if (stationQuery.isBlank()) return VoiceCommand.Unrecognized
        return VoiceCommand.PlayStation(stationQuery, delaySeconds?.coerceIn(0, 300))
    }

    /** Evita que "cope madrid con 5 segundos de retraso" (que sí tiene
     *  emisora) se confunda con un comando de solo-retardo: si tras quitar
     *  los números/segundos queda texto que parece nombre de emisora, no es
     *  un comando de solo-retardo. */
    private fun looksLikeDelayOnlyCommand(normalized: String): Boolean {
        val withoutDelayWords = normalized
            .replace(delayOnlyRegex, " ")
            .replace(standaloneNumberSecondsRegex, " ")
            .replace(Regex("\\b(la\\s+)?(radio|emision|directo)\\b"), " ")
            .trim()
        return withoutDelayWords.length <= 3
    }

    /** Encuentra la emisora del catálogo cuyo nombre encaja mejor con la
     *  consulta de voz. Coincidencia exacta primero, luego "contiene", en
     *  ambas direcciones y sin acentos, para tolerar cómo transcribe
     *  Assistant nombres como "León" o "Onda Cero". */
    fun findBestMatch(stations: List<RadioStation>, query: String): RadioStation? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null

        val byExactName = stations.firstOrNull { normalize(it.name) == normalizedQuery }
        if (byExactName != null) return byExactName

        val candidates = stations.filter { station ->
            val name = normalize(station.name)
            val city = normalize(station.city)
            name.contains(normalizedQuery) || normalizedQuery.contains(name) ||
                (city.isNotBlank() && (normalizedQuery.contains(city) && normalizedQuery.contains(name.substringBefore(' '))))
        }
        return candidates.minByOrNull { kotlin.math.abs(normalize(it.name).length - normalizedQuery.length) }
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").replace(Regex("\\s+"), " ").trim()
    }
}
