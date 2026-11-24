package com.gogether.app.core.data.cloud

import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class CloudTransportException(val code: FirebaseFunctionsException.Code, message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun jsonValue(value: Any?): Any? = when (value) {
    JSONObject.NULL, null -> null
    is JSONObject -> value.keys().asSequence().associateWith { jsonValue(value.get(it)) }
    is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
    else -> value
}

/** Callable protocol: https://firebase.google.com/docs/functions/callable-reference
 * The local emulator needs Auth tokens but not Firebase Instance ID / FCM registration.
 * Firebase Functions SDK 22 requests legacy IID even for localhost, so local builds use
 * the same authenticated callable envelope directly. Production uses the official SDK.
 */
internal suspend fun FirebaseServices.invokeCallable(data: Map<String, Any?>): Map<String, Any?> {
    val endpoint = emulatorCallableUrl ?: return CloudDto.objectValue(functions.getHttpsCallable("gogether").call(data).await().data)
    val token = auth.currentUser?.getIdToken(false)?.await()?.token
    return withContext(Dispatchers.IO) {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(JSONObject().put("data", JSONObject(data)).toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(body) }.getOrNull()
            val error = response?.optJSONObject("error")
            if (status !in 200..299 || error != null) {
                val code = error?.optString("status")?.let { runCatching { FirebaseFunctionsException.Code.valueOf(it) }.getOrNull() }
                    ?: if (status == 401) FirebaseFunctionsException.Code.UNAUTHENTICATED else FirebaseFunctionsException.Code.UNAVAILABLE
                throw CloudTransportException(code, error?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "The local backend is unavailable (HTTP $status).")
            }
            check(response != null && (response.has("result") || response.has("data"))) { "The backend returned an invalid response." }
            CloudDto.objectValue(jsonValue(if (response.has("result")) response.get("result") else response.get("data")))
        } catch (error: IOException) {
            throw CloudTransportException(FirebaseFunctionsException.Code.UNAVAILABLE,
                "Could not reach the local backend. Check the emulator connection.", error)
        } finally { connection.disconnect() }
    }
}
