package dev.co508.ticketspace.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class Session(
    val token: String = "",
    val user: User? = null,
)

object SessionSerializer : Serializer<Session> {
    override val defaultValue = Session()

    override suspend fun readFrom(input: InputStream): Session =
        wireJson.decodeFromString(input.readBytes().decodeToString())

    override suspend fun writeTo(
        t: Session,
        output: OutputStream,
    ) {
        output.write(wireJson.encodeToString(t).encodeToByteArray())
    }
}

class AuthStore(
    context: Context,
    private val api: PocketBaseClient,
) {
    private val store: DataStore<Session> =
        DataStoreFactory.create(SessionSerializer) {
            context.dataStoreFile("session.json")
        }
    private val mutex = Mutex()
    private val mutableSession = MutableStateFlow(Session())
    val session = mutableSession.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    suspend fun restore() =
        mutex.withLock {
            try {
                val saved = store.data.first()
                api.token = saved.token
                if (saved.token.isNotEmpty()) persist(api.refreshAuth())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never expose stored, unvalidated data as an offline session.
                api.token = ""
                mutableError.value = "Could not restore session. Check your connection and sign in again."
            } finally {
                mutableReady.value = true
            }
        }

    suspend fun signIn(
        email: String,
        password: String,
    ) = mutex.withLock {
        persist(api.authWithPassword(email, password))
        mutableError.value = null
    }

    suspend fun refresh() =
        mutex.withLock {
            if (api.token.isNotEmpty()) persist(api.refreshAuth())
        }

    suspend fun signOut() =
        mutex.withLock {
            api.token = ""
            mutableSession.value = Session()
            store.updateData { Session() }
        }

    private suspend fun persist(response: AuthResponse) {
        val value = Session(response.token, response.record)
        store.updateData { value }
        api.token = value.token
        mutableSession.value = value
    }
}
