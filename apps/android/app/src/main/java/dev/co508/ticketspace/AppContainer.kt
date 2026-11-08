package dev.co508.ticketspace

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dev.co508.ticketspace.data.ArSettings
import dev.co508.ticketspace.data.AuthStore
import dev.co508.ticketspace.data.PocketBaseClient
import dev.co508.ticketspace.data.Realtime
import dev.co508.ticketspace.data.SpaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppContainer(
    context: Context,
) {
    val api = PocketBaseClient(BuildConfig.BASE_URL)
    val auth = AuthStore(context, api)
    val arSettings = ArSettings(context)
    val spaces = SpaceRepository(api, Realtime(api))

    suspend fun runForeground() {
        auth.session.collectLatest { session ->
            spaces.clear()
            if (session.user != null) {
                coroutineScope {
                    launch { spaces.watch() }
                    launch {
                        while (true) {
                            delay(15 * 60 * 1000L)
                            try {
                                auth.refresh()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (e is dev.co508.ticketspace.data.ApiException && e.code in listOf(401, 403)) {
                                    auth.signOut()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class TicketSpaceApplication :
    Application(),
    SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context).diskCache(null).build()
}
