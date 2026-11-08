package dev.co508.ticketspace.ar.providers

import com.google.ar.core.Anchor

data class HostedAnchor(
    val anchorId: String,
    val expiresAt: String? = null,
)

interface AnchorProvider {
    val id: String

    suspend fun host(
        anchor: Anchor,
        ttlDays: Int,
    ): HostedAnchor

    suspend fun resolve(anchorId: String): Anchor?
}
