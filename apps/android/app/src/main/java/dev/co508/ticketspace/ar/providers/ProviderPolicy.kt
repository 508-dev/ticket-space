package dev.co508.ticketspace.ar.providers

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class ProviderKind(
    val id: String,
    val label: String,
) {
    LOCAL("local", "Local (session-only, dev)"),
    CLOUD("cloud", "Google Cloud"),
    ;

    companion object {
        fun fromId(id: String): ProviderKind =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown anchor provider: $id")
    }
}

/** Cloud Anchors are hosted with keyless authorization, which allows a TTL of up to 365 days. */
const val CLOUD_TTL_DAYS = 365

fun anchorExpiry(
    now: Instant,
    ttlDays: Int,
): String = now.plus(ttlDays.toLong(), ChronoUnit.DAYS).toString()

/** Shared by host and resolve, so queued work can never exceed ARCore's 40-operation limit. */
class AnchorOperations(
    limit: Int = 40,
    private val timeoutMillis: Long = 30_000,
) {
    private val slots = Semaphore(limit)

    suspend fun <T> run(block: suspend () -> T): T? = slots.withPermit { withTimeoutOrNull(timeoutMillis) { block() } }
}

fun <T> selectProvider(
    recordedId: String,
    local: T,
    cloud: T,
): T =
    when (ProviderKind.fromId(recordedId)) {
        ProviderKind.LOCAL -> local
        ProviderKind.CLOUD -> cloud
    }
