package com.miradio.app.playback

import com.miradio.app.domain.model.RadioStation
import com.miradio.app.domain.model.StationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun `frase vacia no se reconoce`() {
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse(""))
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse("   "))
    }

    @Test
    fun `nombre de emisora simple`() {
        val command = VoiceCommandParser.parse("cope madrid")
        assertEquals(VoiceCommand.PlayStation("cope madrid", null), command)
    }

    @Test
    fun `emisora con retardo en la misma frase`() {
        val command = VoiceCommandParser.parse("cope madrid con un retardo de 5 segundos")
        assertTrue(command is VoiceCommand.PlayStation)
        command as VoiceCommand.PlayStation
        assertEquals(5, command.delaySeconds)
        assertEquals("cope madrid", command.stationQuery)
    }

    @Test
    fun `emisora con retardo, variante 'con X segundos de retardo'`() {
        val command = VoiceCommandParser.parse("los 40 con 10 segundos de retraso")
        assertTrue(command is VoiceCommand.PlayStation)
        assertEquals(10, (command as VoiceCommand.PlayStation).delaySeconds)
    }

    @Test
    fun `solo retardo, sin nombre de emisora`() {
        val command = VoiceCommandParser.parse("retrasa 3 segundos la radio")
        assertEquals(VoiceCommand.SetDelay(3), command)
    }

    @Test
    fun `anular retardo`() {
        assertEquals(VoiceCommand.ClearDelay, VoiceCommandParser.parse("anula el retardo"))
        assertEquals(VoiceCommand.ClearDelay, VoiceCommandParser.parse("quita el retraso"))
    }

    @Test
    fun `el retardo se limita entre 0 y 300 segundos`() {
        val command = VoiceCommandParser.parse("retrasa 999 segundos la radio")
        assertEquals(VoiceCommand.SetDelay(300), command)
    }

    @Test
    fun `quita la mencion a Radio Dari de la consulta de emisora`() {
        val command = VoiceCommandParser.parse("pon cope madrid en radio dari")
        assertTrue(command is VoiceCommand.PlayStation)
        assertEquals("pon cope madrid", (command as VoiceCommand.PlayStation).stationQuery)
    }

    @Test
    fun `findBestMatch prioriza coincidencia exacta`() {
        val stations = listOf(
            station("cope_madrid", "COPE Madrid", "Madrid"),
            station("cope_leon", "COPE León", "León"),
        )
        val match = VoiceCommandParser.findBestMatch(stations, "cope madrid")
        assertEquals("cope_madrid", match?.id)
    }

    @Test
    fun `findBestMatch tolera acentos distintos`() {
        val stations = listOf(station("cope_leon", "COPE León", "León"))
        val match = VoiceCommandParser.findBestMatch(stations, "cope leon")
        assertEquals("cope_leon", match?.id)
    }

    @Test
    fun `findBestMatch devuelve null sin candidatos`() {
        val stations = listOf(station("cope_madrid", "COPE Madrid", "Madrid"))
        assertNull(VoiceCommandParser.findBestMatch(stations, "una emisora que no existe"))
    }

    private fun station(id: String, name: String, city: String) = RadioStation(
        id = id,
        name = name,
        city = city,
        streamUrl = "https://example.com/$id.mp3",
        logoUrl = null,
        description = null,
        category = null,
        isFavorite = false,
        isAvailable = true,
        source = StationSource.SEED,
    )
}
