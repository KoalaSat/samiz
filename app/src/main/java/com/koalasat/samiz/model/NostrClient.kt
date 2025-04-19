package com.koalasat.samiz.model

import android.content.Context
import android.util.Log
import com.koalasat.samiz.database.AppDatabase
import com.koalasat.samiz.database.EventEntity
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
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

    fun start(
        context: Context,
        onSyncEvent: (Event, Boolean) -> Unit,
    ) {
        clientNotificationListener =
            object : Client.Listener {
                override fun onEvent(
                    event: Event,
                    subscriptionId: String,
                    relay: Relay,
                    afterEOSE: Boolean,
                ) {
                    Log.d("NostrClient", "New local event received : ${event.id}")
                    val db = AppDatabase.getDatabase(context, "common")
                    val eventEntity = EventEntity(id = 0, eventId = event.id, createdAt = event.createdAt, local = 1)
                    db.applicationDao().insertEvent(eventEntity)
                    if (subscriptionId == subscriptionSyncId) onSyncEvent(event, afterEOSE)
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
        val currentTimeMillis = System.currentTimeMillis()
        val oneHourAgoMillis = currentTimeMillis - 3600000
        val oneHourAgoTimestamp = oneHourAgoMillis / 1000
        Client.sendFilter(
            subscriptionSyncId,
            listOf(
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(1),
                            since = RelayPool.getAll().associate { it.url to EOSETime(oneHourAgoTimestamp) },
                        ),
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

    fun publishEvent(
        event: Event,
        context: Context,
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            RelayPool.send(event)
            Log.d("BluetoothReconciliation", "Nostr note published to local relay : ${event.id}")
            val db = AppDatabase.getDatabase(context, "common")
            val eventEntity = EventEntity(id = 0, eventId = event.id, createdAt = event.createdAt, local = 0)
            db.applicationDao().insertEvent(eventEntity)
        }
    }
}
