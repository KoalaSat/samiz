package com.koalasat.samiz.model

import android.content.Context
import android.util.Log
import com.koalasat.samiz.database.AppDatabase
import com.koalasat.samiz.database.EventEntity
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.events.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NostrClient {
    private var subscriptionSyncId = "samizSync"
    private var subscriptionEventId = "samizEvent"
    private var defaultRelayUrls =
        listOf(
            "ws://127.0.0.1:4869",
        )
    private lateinit var clientNotificationListener: Client.Listener

    fun start(context: Context) {
        clientNotificationListener =
            object : Client.Listener {
                override fun onEvent(
                    event: Event,
                    subscriptionId: String,
                    relay: Relay,
                    afterEOSE: Boolean,
                ) {
                    Log.d("NostrClient", "New event : ${event.id}")
                    val db = AppDatabase.getDatabase(context, "common")
                    val eventEntity = EventEntity(id = 0, eventId = event.id, createdAt = event.createdAt)
                    db.applicationDao().insertEvent(eventEntity)
                }

                override fun onError(
                    error: Error,
                    subscriptionId: String,
                    relay: Relay,
                ) {
                    Log.d("NostrClient", "Relay connection error : $error")
                }
            }

        RelayPool.register(Client)

        if (!Client.isSubscribed(clientNotificationListener)) Client.subscribe(clientNotificationListener)

        Log.d("NostrClient", "Connecting to local relay")
        connectRelays()

        Log.d("NostrClient", "Sending request")
        Client.sendFilter(
            subscriptionSyncId,
            listOf(
                TypedFilter(
                    types = setOf(FeedType.PUBLIC_CHATS),
                    filter = SincePerRelayFilter(limit = 10),
                ),
            ),
        )
    }

    fun close() {
        Client.unsubscribe(clientNotificationListener)
        RelayPool.unloadRelays()
    }

    private fun connectRelays() {
        defaultRelayUrls.forEach {
            if (RelayPool.getRelays(it).isEmpty()) {
                RelayPool.addRelay(
                    Relay(
                        it,
                        read = true,
                        write = true,
                        forceProxy = false,
                        activeTypes = COMMON_FEED_TYPES,
                    ),
                )
            }
        }
    }

    fun getEvent(
        id: String,
        onResponse: (Event) -> Unit,
    ) {
        Client.sendFilterAndStopOnFirstResponse(
            subscriptionEventId,
            listOf(
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            ids = listOf(id),
                            limit = 1,
                        ),
                ),
            ),
            onResponse = { event ->
                onResponse(event)
            },
        )
    }

    fun publishEvent(event: Event) {
        CoroutineScope(Dispatchers.Default).launch {
            RelayPool.send(event)
            Log.d("BluetoothReconciliation", "Nostr note published to local relay : ${event.id}")
        }
    }
}
