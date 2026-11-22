package com.example.cityfixmaas.data.relay

import com.example.cityfixmaas.data.AppConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls the CityFix relay (server/relay): a free Google Apps Script web app that sends push
 * messages, emails sign-in codes and stores videos in Google Drive. Every call carries the
 * user's Firebase ID token, which the relay checks.
 */
object Relay {
    suspend fun call(action: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: error("Not signed in")
        body.put("action", action).put("idToken", idToken)
        val connection = URL(AppConfig.RELAY_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val answer = JSONObject(connection.inputStream.bufferedReader().readText())
            if (answer.has("error")) error("Relay: ${answer.getString("error")}")
            answer
        } finally {
            connection.disconnect()
        }
    }
}
