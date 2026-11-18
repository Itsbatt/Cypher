package com.cypher.desktop

import com.cypher.desktop.app.AppState
import com.cypher.desktop.connect.ConnectServer
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectServerTest {
    @Test
    fun testConnectServerStartsAndServesStatus() {
        val appState = AppState()
        println("PORT IN APPSTATE: " + appState.state.value.data.preferences.connectServerPort)
        val server = ConnectServer(appState, 8490)
        try {
            server.start()
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8490/api/status"))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            println("BODY: " + response.body())
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("connected"), "Body was: " + response.body())
        } finally {
            server.stop()
            appState.shutdown()
        }
    }
}
