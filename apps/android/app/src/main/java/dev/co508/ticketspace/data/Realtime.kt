package dev.co508.ticketspace.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface RealtimeEvent {
    data object Connected : RealtimeEvent

    data class Disconnected(
        val message: String,
    ) : RealtimeEvent

    data class Change(
        val collection: String,
        val action: String,
        val record: JsonObject,
    ) : RealtimeEvent
}

class Realtime(
    private val api: PocketBaseClient,
) {
    private val http =
        api.http
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    fun events(): Flow<RealtimeEvent> =
        flow {
            var failures = 0
            while (kotlin.coroutines.coroutineContext.isActive) {
                try {
                    connection().collect {
                        if (it == RealtimeEvent.Connected) failures = 0
                        emit(it)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(RealtimeEvent.Disconnected("Live connection lost. Reconnecting… Check your connection."))
                }
                delay((1000L shl failures.coerceAtMost(5)).coerceAtMost(30_000L))
                failures++
            }
        }

    private fun connection(): Flow<RealtimeEvent> =
        callbackFlow {
            val producer = this
            val listener =
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        try {
                            val payload = wireJson.parseToJsonElement(data).jsonObject
                            if (type == "PB_CONNECT") {
                                launch {
                                    try {
                                        val body =
                                            buildJsonObject {
                                                put("clientId", payload.getValue("clientId"))
                                                put("subscriptions", JsonArray(TOPICS.map(::JsonPrimitive)))
                                            }
                                        api.execute(
                                            api
                                                .request(api.url("api", "realtime"))
                                                .post(PocketBaseClient.jsonBody(body))
                                                .build(),
                                        )
                                        send(RealtimeEvent.Connected)
                                    } catch (e: Exception) {
                                        producer.close(e)
                                    }
                                }
                            } else if (payload["action"] != null && payload["record"] != null) {
                                val record = payload.getValue("record").jsonObject
                                val collection =
                                    record["collectionName"]?.jsonPrimitive?.content
                                        ?: type?.substringBefore('/') ?: return
                                val event =
                                    RealtimeEvent.Change(
                                        collection,
                                        payload.getValue("action").jsonPrimitive.content,
                                        record,
                                    )
                                if (!trySend(event).isSuccess) producer.close(IOException("Realtime buffer overflow"))
                            }
                        } catch (e: Exception) {
                            producer.close(e)
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        producer.close(IOException("Realtime closed"))
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        producer.close(t ?: IOException("Realtime HTTP ${response?.code}"))
                    }
                }
            // Authorization is applied to the POST, as required by PocketBase.
            val source =
                EventSources.createFactory(http).newEventSource(
                    Request.Builder().url(api.url("api", "realtime")).build(),
                    listener,
                )
            awaitClose { source.cancel() }
        }

    companion object {
        val TOPICS = listOf("todos/*", "rooms/*", "things/*", "room_anchors/*")
    }
}
