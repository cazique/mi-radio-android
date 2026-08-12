package com.miradio.app.domain.usecase

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateStreamUrlUseCaseTest {

    private lateinit var server: MockWebServer
    private lateinit var useCase: ValidateStreamUrlUseCase

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        useCase = ValidateStreamUrlUseCase(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `looksValid exige esquema http o https`() {
        assertTrue(useCase.looksValid("https://example.com/stream.mp3"))
        assertTrue(useCase.looksValid("http://example.com/stream.mp3"))
        assertEquals(false, useCase.looksValid(""))
        assertEquals(false, useCase.looksValid("ftp://example.com/stream.mp3"))
        assertEquals(false, useCase.looksValid("no es una url"))
    }

    @Test
    fun `content-type de audio conocido se acepta sin mirar el cuerpo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "audio/mpeg"))
        val result = useCase(server.url("/stream").toString())
        assertEquals(UrlValidationResult.Valid, result)
    }

    @Test
    fun `content-type html se rechaza aunque el HTTP sea 200`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html")
                .setBody("<html><body>Stream no disponible</body></html>"),
        )
        val result = useCase(server.url("/stream").toString())
        assertTrue(result is UrlValidationResult.Unreachable)
    }

    @Test
    fun `sin content-type util, se reconoce un mp3 por sus primeros bytes (ID3)`() = runTest {
        val mp3Bytes = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0)
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(Buffer().write(mp3Bytes)),
        )
        val result = useCase(server.url("/stream").toString())
        assertEquals(UrlValidationResult.Valid, result)
    }

    @Test
    fun `sin content-type util, se reconoce un mp3 por el frame sync sin ID3`() = runTest {
        // 0xFF 0xFB: cabecera de frame MPEG (MP3) sin etiqueta ID3.
        val mp3Bytes = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 0x00, 0x00)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(Buffer().write(mp3Bytes)),
        )
        val result = useCase(server.url("/stream").toString())
        assertEquals(UrlValidationResult.Valid, result)
    }

    @Test
    fun `sin content-type util y sin cabecera de audio reconocible, se rechaza`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("esto no es audio, es texto plano"),
        )
        val result = useCase(server.url("/stream").toString())
        assertTrue(result is UrlValidationResult.Unreachable)
    }

    @Test
    fun `codigo HTTP de error se rechaza`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = useCase(server.url("/stream").toString())
        assertEquals(UrlValidationResult.Unreachable("HTTP 404"), result)
    }

    @Test
    fun `url con formato invalido no llega a hacer una peticion de red`() = runTest {
        val result = useCase("no-es-una-url")
        assertEquals(UrlValidationResult.InvalidFormat, result)
        assertEquals(0, server.requestCount)
    }
}
