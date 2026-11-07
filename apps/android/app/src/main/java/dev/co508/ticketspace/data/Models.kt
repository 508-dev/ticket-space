package dev.co508.ticketspace.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val wireJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

enum class Priority(
    val value: Int,
    val label: String,
) {
    LOW(0, "Low"),
    NORMAL(1, "Normal"),
    HIGH(2, "High"),
    URGENT(3, "Urgent"),
    ;

    companion object {
        fun from(value: Int): Priority = entries.first { it.value == value }
    }
}

@Serializable
enum class TodoStatus {
    @SerialName("open")
    OPEN,

    @SerialName("done")
    DONE,
}

interface Record {
    val id: String
    val updated: String get() = ""
}

@Serializable
data class User(
    override val id: String,
    val email: String = "",
    val name: String = "",
) : Record

@Serializable
data class Site(
    override val id: String,
    val name: String,
    val created: String = "",
    override val updated: String = "",
) : Record

@Serializable
data class Room(
    override val id: String,
    val site: String,
    val name: String,
    val panorama: String = "",
    val created: String = "",
    override val updated: String = "",
) : Record

@Serializable
data class Thing(
    override val id: String,
    val room: String,
    val label: String = "",
    @SerialName("pose_in_room") val poseInRoom: JsonObject? = null,
    val created: String = "",
    override val updated: String = "",
) : Record

@Serializable
data class RoomAnchor(
    override val id: String,
    val room: String,
    val provider: String,
    @SerialName("anchor_id") val anchorId: String,
    @SerialName("pose_in_room") val poseInRoom: JsonObject,
    @SerialName("expires_at") val expiresAt: String = "",
    val created: String = "",
    override val updated: String = "",
) : Record

@Serializable
data class Todo(
    override val id: String,
    val title: String,
    val description: String = "",
    val priority: Int,
    val tags: List<String> = emptyList(),
    val status: TodoStatus,
    val photos: List<String> = emptyList(),
    val room: String,
    val thing: String = "",
    @SerialName("created_by") val createdBy: String,
    @SerialName("done_by") val doneBy: String = "",
    @SerialName("done_at") val doneAt: String = "",
    val created: String = "",
    override val updated: String = "",
) : Record {
    init {
        require(priority in 0..3)
    }

    val priorityLevel: Priority get() = Priority.from(priority)
}

@Serializable
data class AuthResponse(
    val token: String,
    val record: User,
)

@Serializable
data class RecordPage<T>(
    val items: List<T>,
    val totalPages: Int = 1,
)

data class TodoQuery(
    val room: String? = null,
    val tag: String? = null,
    val showDone: Boolean = false,
) {
    val filter: String
        get() =
            buildList {
                room?.let { add("room = ${filterLiteral(it)}") }
                // PocketBase JSON fields do not support :each. Match a JSON-quoted string;
                // matching() additionally enforces exact membership (LIKE has wildcard semantics).
                // https://github.com/pocketbase/pocketbase/discussions/2332#discussioncomment-9238250
                tag?.let { add("tags ~ ${filterLiteral(wireJson.encodeToString(it))}") }
                if (!showDone) add("status = 'open'")
            }.joinToString(" && ")

    companion object {
        const val SORT = "-priority,created"
    }
}

fun filterLiteral(value: String): String = "'${value.replace("\\", "\\\\").replace("'", "\\'")}'"

val todoOrder: Comparator<Todo> = compareByDescending<Todo> { it.priority }.thenBy { it.created }.thenBy { it.id }

fun List<Todo>.matching(query: TodoQuery): List<Todo> =
    filter {
        (query.room == null || it.room == query.room) &&
            (query.tag == null || query.tag in it.tags) &&
            (query.showDone || it.status == TodoStatus.OPEN)
    }.sortedWith(todoOrder)

fun <T : Record> mergeEvent(
    records: List<T>,
    action: String,
    record: T,
): List<T> {
    // A queued pre-snapshot event must not overwrite a newer HTTP snapshot.
    val existing = records.find { it.id == record.id }
    if (existing != null && existing.updated.isNotEmpty() && existing.updated > record.updated) return records
    return when (action) {
        "delete" -> records.filterNot { it.id == record.id }
        "create", "update" -> records.filterNot { it.id == record.id } + record
        else -> records
    }
}
