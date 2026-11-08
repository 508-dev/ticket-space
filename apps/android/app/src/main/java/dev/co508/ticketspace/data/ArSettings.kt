package dev.co508.ticketspace.data

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dev.co508.ticketspace.ar.providers.ProviderKind
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class ArPreferences(
    val defaultProvider: String = "local",
    val cloudDisclosureAccepted: Boolean = false,
)

private object ArPreferencesSerializer : Serializer<ArPreferences> {
    override val defaultValue = ArPreferences()

    override suspend fun readFrom(input: InputStream): ArPreferences =
        wireJson.decodeFromString(input.readBytes().decodeToString())

    override suspend fun writeTo(
        t: ArPreferences,
        output: OutputStream,
    ) {
        output.write(wireJson.encodeToString(t).encodeToByteArray())
    }
}

class ArSettings(
    context: Context,
) {
    private val store = DataStoreFactory.create(ArPreferencesSerializer) { context.dataStoreFile("ar-settings.json") }
    val data = store.data

    suspend fun setProvider(provider: ProviderKind) {
        store.updateData { it.copy(defaultProvider = provider.id) }
    }

    suspend fun acceptDisclosure() {
        store.updateData { it.copy(cloudDisclosureAccepted = true) }
    }
}
