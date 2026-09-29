/*
 * Copyright (C) 2026 Daniel Georg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.car.pingpong

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ShellMainThread
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * One HTTPS call to Jev (`POST /v1/systemone`, bearer key), off the shell thread and back.
 *
 * The key is read on every call, on the network thread: first from [API_KEY_FILE] in SystemUI's
 * device-protected storage (root-only, not in bugreports), then from `Settings.Global`
 * [API_KEY_SETTING], which every app can read and bugreports include, so it suits only a
 * throwaway key. A system property is not used: `setprop` cuts values at 91 characters and
 * does not survive a reboot. Without a key the outcome is [PongJevOutcome.NoKey].
 *
 * Requests run on the pod's own thread so a slow read does not stall the shell background
 * executor other panels share; the outcome is posted to the shell main thread. Only `https`
 * endpoints are called, because the bearer key travels in a header.
 */
class PongJevClient @Inject constructor(
    private val context: Context,
    @ShellMainThread private val shellMainExecutor: ShellExecutor,
) : PongJevGateway {

    private val network: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "pong.jev") }

    override fun ask(endpoint: String, request: JSONObject, onOutcome: (PongJevOutcome) -> Unit) {
        val body = request.toString()
        network.execute {
            val apiKey = readApiKey()
            val outcome =
                if (apiKey.isNullOrBlank()) PongJevOutcome.NoKey else post(endpoint, apiKey, body)
            shellMainExecutor.execute { onOutcome(outcome) }
        }
    }

    private fun readApiKey(): String? {
        val keyFile = File(context.createDeviceProtectedStorageContext().filesDir, API_KEY_FILE)
        val fromFile = try {
            if (keyFile.isFile) keyFile.readText(Charsets.UTF_8).trim() else null
        } catch (e: IOException) {
            null
        }
        if (!fromFile.isNullOrBlank()) return fromFile
        return Settings.Global.getString(context.contentResolver, API_KEY_SETTING)
    }

    private fun post(endpoint: String, apiKey: String, body: String): PongJevOutcome {
        val startedAt = SystemClock.uptimeMillis()
        val connection = try {
            URL(endpoint).openConnection() as? HttpsURLConnection
                ?: return PongJevOutcome.Failure(oneLineReason("endpoint is not https: $endpoint"))
        } catch (e: IOException) {
            return PongJevOutcome.Failure(describe(e))
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != HttpsURLConnection.HTTP_OK) return PongJevOutcome.Failure("HTTP $status")
            val text = connection.inputStream.use { readBounded(it) }
                ?: return PongJevOutcome.Failure("response over ${MAX_RESPONSE_BYTES / 1024} KiB")
            return PongJevOutcome.Answer(text, SystemClock.uptimeMillis() - startedAt)
        } catch (e: IOException) {
            return PongJevOutcome.Failure(describe(e))
        } finally {
            connection.disconnect()
        }
    }

    /** The whole body, or null once it exceeds [MAX_RESPONSE_BYTES]: an answer is a few hundred bytes. */
    private fun readBounded(input: InputStream): String? {
        val collected = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            if (collected.size() + count > MAX_RESPONSE_BYTES) return null
            collected.write(chunk, 0, count)
        }
        return collected.toString(Charsets.UTF_8.name())
    }

    private fun describe(e: IOException): String =
        oneLineReason(listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": "))

    companion object {
        const val API_KEY_SETTING = "pong_jev_api_key"
        const val API_KEY_FILE = "pong_jev_api_key"
        const val CONNECT_TIMEOUT_MS = 2_000
        const val READ_TIMEOUT_MS = 3_000
        const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val READ_CHUNK_BYTES = 4 * 1024
    }
}
