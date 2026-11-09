package dev.co508.ticketspace.ar.providers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProviderPolicyTest {
    private class FakeProvider(
        val result: String,
        val delayMillis: Long = 0,
    ) {
        suspend fun resolve(): String {
            delay(delayMillis)
            return result
        }
    }

    @Test
    fun `resolution uses recorded provider independent of current default`() =
        runTest {
            val local = FakeProvider("local-anchor")
            val cloud = FakeProvider("cloud-anchor")
            assertSame(local, selectProvider("local", local, cloud))
            assertEquals("cloud-anchor", selectProvider("cloud", local, cloud).resolve())
        }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown provider is never silently resolved as local or cloud`() {
        selectProvider("future", "local", "cloud")
    }

    @Test
    fun `cloud anchors use a 365 day TTL and exact expiry`() {
        assertEquals(365, CLOUD_TTL_DAYS)
        assertEquals(
            "2027-11-08T03:00:00Z",
            anchorExpiry(Instant.parse("2026-11-08T03:00:00Z"), CLOUD_TTL_DAYS),
        )
    }

    @Test
    fun `slow fake times out and callback operation is cancelled`() =
        runTest {
            val operations = AnchorOperations(timeoutMillis = 100)
            assertNull(operations.run { FakeProvider("late", 200).resolve() })
            var cancelled = false
            assertNull(
                operations.run {
                    suspendCancellableCoroutine<String> { continuation ->
                        continuation.invokeOnCancellation { cancelled = true }
                    }
                },
            )
            assertTrue(cancelled)
            assertEquals("next", operations.run { FakeProvider("next").resolve() })
        }

    @Test
    fun `more than forty hosts and resolves queue without exceeding shared limit`() =
        runTest {
            val operations = AnchorOperations(timeoutMillis = 1000)
            var active = 0
            var maximum = 0
            val results =
                (1..85)
                    .map {
                        async {
                            operations.run {
                                active++
                                maximum = maxOf(maximum, active)
                                try {
                                    delay(100)
                                    it
                                } finally {
                                    active--
                                }
                            }
                        }
                    }.awaitAll()
            assertEquals(40, maximum)
            assertEquals((1..85).toList(), results)
            assertEquals(0, active)
        }
}
