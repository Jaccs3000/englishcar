package com.englishcar.voicecoach.ai

import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class BackendConversationClient @Inject constructor() {
    suspend fun transcribe(
        backendUrl: String,
        appApiToken: String,
        wavAudio: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = backendUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) throw BackendException.BackendUnavailable
        if (appApiToken.isBlank()) throw BackendException.Unauthorized

        val boundary = "EnglishCar${System.currentTimeMillis()}"
        val connection = (URL("$baseUrl/v1/transcribe").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $appApiToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            connection.outputStream.use { output ->
                output.writeMultipartAudio(boundary, wavAudio)
            }
            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (responseCode !in 200..299) {
                throw BackendException.fromHttp(responseCode, responseText)
            }

            JSONObject(responseText).optString("text").trim()
        } catch (error: Exception) {
            throw BackendException.fromThrowable(error)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchModels(
        backendUrl: String,
        appApiToken: String
    ): AvailableModels = withContext(Dispatchers.IO) {
        val baseUrl = backendUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) throw BackendException.BackendUnavailable
        if (appApiToken.isBlank()) throw BackendException.Unauthorized

        val connection = (URL("$baseUrl/v1/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $appApiToken")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (responseCode !in 200..299) {
                throw BackendException.fromHttp(responseCode, responseText)
            }

            JSONObject(responseText).toAvailableModels()
        } catch (error: Exception) {
            throw BackendException.fromThrowable(error)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun send(
        backendUrl: String,
        appApiToken: String,
        request: ConversationRequest
    ): AiFinalResponse = withContext(Dispatchers.IO) {
        val baseUrl = backendUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) error("Backend URL is not configured.")
        if (appApiToken.isBlank()) error("App API token is not configured.")

        val connection = (URL("$baseUrl/v1/conversation/stream").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $appApiToken")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            val bytes = request.toJson().toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (responseCode !in 200..299) {
                throw BackendException.fromHttp(responseCode, responseText)
            }

            parseFinalEvent(responseText)
        } catch (error: Exception) {
            throw BackendException.fromThrowable(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFinalEvent(streamText: String): AiFinalResponse {
        var currentEvent: String? = null
        val dataLines = mutableListOf<String>()

        fun flush(): AiFinalResponse? {
            val event = currentEvent
            val data = dataLines.joinToString(separator = "\n")
            currentEvent = null
            dataLines.clear()
            return when {
                event == "final" && data.isNotBlank() -> JSONObject(data).toAiFinalResponse()
                event == "error" && data.isNotBlank() -> throw BackendException.BackendUnavailable
                else -> null
            }
        }

        streamText.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> flush()?.let { return it }
                line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trim()
            }
        }

        flush()?.let { return it }
        throw IOException("Backend did not return a final event.")
    }

    private fun ConversationRequest.toJson(): JSONObject {
        val context = JSONArray()
        recentContext.forEach { turn ->
            context.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("text", turn.text)
            )
        }

        return JSONObject()
            .put("requestId", requestId)
            .put("type", type)
            .put("userText", userText)
            .put("assistantId", assistantId)
            .put("assistantName", assistantName)
            .put("assistantPersonality", assistantPersonality)
            .put("userName", userName)
            .put("model", model)
            .put("feedbackLevel", feedbackLevel)
            .put("locale", locale)
            .put("recentContext", context)
    }

    private fun JSONObject.toAiFinalResponse(): AiFinalResponse {
        return AiFinalResponse(
            spokenReply = getString("spokenReply"),
            correction = optNullableString("correction"),
            naturalAlternative = optNullableString("naturalAlternative"),
            shortExplanation = optNullableString("shortExplanation"),
            shouldSaveFeedback = optBoolean("shouldSaveFeedback", false)
        )
    }

    private fun JSONObject.toAvailableModels(): AvailableModels {
        val modelsJson = getJSONArray("models")
        val models = buildList {
            for (index in 0 until modelsJson.length()) {
                val model = modelsJson.optString(index).trim()
                if (model.isNotBlank()) add(model)
            }
        }
        return AvailableModels(
            models = models,
            defaultModel = optString("defaultModel", models.firstOrNull().orEmpty())
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun OutputStream.writeMultipartAudio(boundary: String, wavAudio: ByteArray) {
        fun writeText(value: String) = write(value.toByteArray(Charsets.UTF_8))
        writeText("--$boundary\r\n")
        writeText("Content-Disposition: form-data; name=\"audio\"; filename=\"speech.wav\"\r\n")
        writeText("Content-Type: audio/wav\r\n\r\n")
        write(wavAudio)
        writeText("\r\n--$boundary--\r\n")
        flush()
    }
}

sealed class BackendException(message: String) : IOException(message) {
    data object InternetUnavailable : BackendException("Internet connection lost.")
    data object Unauthorized : BackendException("Backend token is not valid.")
    data object BackendUnavailable : BackendException("AI backend is temporarily unavailable.")
    data object Timeout : BackendException("AI response timed out.")

    companion object {
        fun fromThrowable(error: Throwable): BackendException {
            return when (error) {
                is BackendException -> error
                is UnknownHostException -> InternetUnavailable
                is SocketTimeoutException -> Timeout
                else -> BackendUnavailable
            }
        }

        fun fromHttp(code: Int, body: String): BackendException {
            return when (code) {
                401, 403 -> Unauthorized
                408, 504 -> Timeout
                502, 503 -> BackendUnavailable
                else -> BackendUnavailable
            }
        }
    }
}
