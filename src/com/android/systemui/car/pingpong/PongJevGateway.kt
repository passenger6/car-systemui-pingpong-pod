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

import org.json.JSONObject

/** How one question to Jev ended. */
sealed interface PongJevOutcome {
    data class Answer(val body: String, val latencyMs: Long) : PongJevOutcome
    data class Failure(val reason: String) : PongJevOutcome
    object NoKey : PongJevOutcome
}

/**
 * Sends a question to Jev; the outcome comes back later on the shell main thread.
 * [PongJevClient] is the HTTPS implementation; a test drops in its own.
 */
interface PongJevGateway {
    fun ask(endpoint: String, request: JSONObject, onOutcome: (PongJevOutcome) -> Unit)
}

/** Failure reasons go on the court's status line: first line only, at most [REASON_MAX_CHARS]. */
fun oneLineReason(text: String): String =
    text.lineSequence().firstOrNull()?.trim()?.take(REASON_MAX_CHARS) ?: ""

const val REASON_MAX_CHARS = 80
