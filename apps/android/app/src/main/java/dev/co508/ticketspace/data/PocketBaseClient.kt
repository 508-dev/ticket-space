package dev.co508.ticketspace.data

import dev.co508.ticketspace.ar.math.Pose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class ApiException(
    val code: Int,
    message: String,
) : IOException(message)

class PocketBaseClient(
    baseUrl: String,
    val http: OkHttpClient = OkHttpClient(),
) {
    val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    @Volatile
    var token: String = ""

    fun url(vararg segments: String): HttpUrl =
        base
            .newBuilder()
            .apply {
                segments.forEach(::addPathSegment)
            }.build()

    fun request(url: HttpUrl): Request.Builder =
        Request.Builder().url(url).apply {
            if (token.isNotEmpty()) header("Authorization", token)
        }

    suspend fun execute(request: Request): String =
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message =
                        when (response.code) {
                            400 -> "Check your details. The server rejected this request."
                            401, 403 -> "Session expired or access denied. Please sign in again."
                            404 -> "This record is no longer available."
                            else -> "Server error (${response.code}). Please retry."
                        }
                    throw ApiException(response.code, message)
                }
                body
            }
        }

    suspend inline fun <reified T> list(
        collection: String,
        filter: String = "",
        sort: String = "created",
    ): List<T> {
        val result = mutableListOf<T>()
        var page = 1
        do {
            val endpoint =
                url("api", "collections", collection, "records")
                    .newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("perPage", "200")
                    .addQueryParameter("sort", sort)
                    .addQueryParameter("filter", filter)
                    .build()
            val response = wireJson.decodeFromString<RecordPage<T>>(execute(request(endpoint).build()))
            result += response.items
            page++
        } while (page <= response.totalPages)
        return result
    }

    suspend fun authWithPassword(
        email: String,
        password: String,
    ): AuthResponse =
        auth(
            "auth-with-password",
            buildJsonObject {
                put("identity", email)
                put("password", password)
            },
        )

    suspend fun refreshAuth(): AuthResponse = auth("auth-refresh", JsonObject(emptyMap()))

    private suspend fun auth(
        action: String,
        data: JsonObject,
    ): AuthResponse =
        wireJson.decodeFromString(
            execute(request(url("api", "collections", "users", action)).post(jsonBody(data)).build()),
        )

    suspend fun save(
        collection: String,
        id: String?,
        body: RequestBody,
    ): String {
        val endpoint =
            if (id == null) {
                url("api", "collections", collection, "records")
            } else {
                url("api", "collections", collection, "records", id)
            }
        return execute(request(endpoint).method(if (id == null) "POST" else "PATCH", body).build())
    }

    suspend fun delete(
        collection: String,
        id: String,
    ) {
        execute(request(url("api", "collections", collection, "records", id)).delete().build())
    }

    suspend inline fun <reified T> get(
        collection: String,
        id: String,
    ): T =
        wireJson.decodeFromString(
            execute(request(url("api", "collections", collection, "records", id)).build()),
        )

    suspend fun todos(query: TodoQuery): List<Todo> = list<Todo>("todos", query.filter, TodoQuery.SORT).matching(query)

    suspend fun saveTodo(
        id: String?,
        fields: JsonObject,
        photos: List<File> = emptyList(),
    ): Todo {
        require(photos.size <= 3) { "At most three photos are allowed." }
        return wireJson.decodeFromString(save("todos", id, multipart(fields, "photos+", photos)))
    }

    suspend fun saveRoom(
        id: String?,
        site: String,
        name: String,
        panorama: File? = null,
    ): Room {
        val fields =
            buildJsonObject {
                put("site", site)
                put("name", name)
            }
        return wireJson.decodeFromString(save("rooms", id, multipart(fields, "panorama", listOfNotNull(panorama))))
    }

    fun fileUrl(
        collection: String,
        id: String,
        filename: String,
        thumb: String = "320x320",
    ): String =
        url("api", "files", collection, id, filename)
            .newBuilder()
            .addQueryParameter("thumb", thumb)
            .build()
            .toString()

    suspend fun saveThing(
        id: String?,
        room: String,
        label: String,
        pose: Pose?,
    ): Thing =
        wireJson.decodeFromString(
            save(
                "things",
                id,
                jsonBody(
                    buildJsonObject {
                        put("room", room)
                        put("label", label)
                        put("pose_in_room", pose?.toJson() ?: JsonNull)
                    },
                ),
            ),
        )

    suspend fun saveAnchor(
        id: String?,
        room: String,
        provider: String,
        anchorId: String,
        pose: Pose,
        expiresAt: String?,
    ): RoomAnchor =
        wireJson.decodeFromString(
            save(
                "room_anchors",
                id,
                jsonBody(
                    buildJsonObject {
                        put("room", room)
                        put("provider", provider)
                        put("anchor_id", anchorId)
                        put("pose_in_room", pose.toJson())
                        put("expires_at", expiresAt.orEmpty())
                    },
                ),
            ),
        )

    companion object {
        fun jsonBody(data: JsonObject): RequestBody = data.toString().toRequestBody("application/json".toMediaType())

        fun multipart(
            fields: JsonObject,
            field: String,
            files: List<File>,
        ): RequestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .apply {
                    fields.forEach { (name, value) ->
                        addFormDataPart(name, if (value is JsonPrimitive) value.content else value.toString())
                    }
                    files.forEach { file ->
                        addFormDataPart(field, file.name, file.asRequestBody("image/jpeg".toMediaType()))
                    }
                }.build()
    }
}
