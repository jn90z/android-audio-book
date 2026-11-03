package com.example.app.api

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUser(val id: String, val username: String, val displayName: String)
data class AuthResult(val accessToken: String, val expiresAt: String, val user: AppUser)

class AppApi(private val baseUrl: String) {
    fun exchangeGoogle(idToken: String, nonce: String): JSONObject =
        request("POST", "/v1/auth/google", JSONObject().put("idToken", idToken).put("nonce", nonce))

    fun completeGoogleSetup(idToken: String, nonce: String, username: String): AuthResult {
        val body = request("POST", "/v1/auth/google/setup",
            JSONObject().put("idToken", idToken).put("nonce", nonce).put("username", username))
        return authResult(body)
    }

    fun me(accessToken: String): AppUser =
        user(request("GET", "/v1/me", token = accessToken))

    private fun authResult(o: JSONObject) = AuthResult(
        o.getString("accessToken"), o.getString("expiresAt"), user(o.getJSONObject("user"))
    )

    private fun user(o: JSONObject) = AppUser(
        o.getString("id"), o.getString("username"), o.getString("displayName")
    )

    private fun request(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = c.responseCode
        val stream = if (status in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException("API $status: $text")
        return JSONObject(text)
    }
}
