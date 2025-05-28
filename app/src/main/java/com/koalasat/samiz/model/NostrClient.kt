package com.koalasat.samiz.model

import android.content.Context
import com.koalasat.samiz.Samiz
import com.koalasat.samiz.database.AppDatabase
import com.koalasat.samiz.database.EventEntity
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
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
    private var subscriptionSyncEventsId = "samizSync"
    private var subscriptionSyncPrivateId = "samizPrivate"
    private var subscriptionSyncMetaId = "samizMeta"
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
                    Logger.d("NostrClient", "New local event received : ${event.id.take(5)}...${event.id.takeLast(5)}")
                    val db = AppDatabase.getDatabase(context, "common")
                    val eventEntity = EventEntity(id = 0, eventId = event.id, createdAt = event.createdAt, local = 1)
                    db.applicationDao().insertEvent(eventEntity)
                    if (subscriptionId != subscriptionEventId) onSyncEvent(event, afterEOSE)
                }

                override fun onError(
                    error: Error,
                    subscriptionId: String,
                    relay: Relay,
                ) {
                    Logger.d("NostrClient", "Relay connection error : $error")
                    Samiz.getInstance().relayError()
                }
            }

        RelayPool.register(Client)

        if (!Client.isSubscribed(clientNotificationListener)) Client.subscribe(clientNotificationListener)

        Logger.d("NostrClient", "Connecting to local relay")
        connectRelays()

        Logger.d("NostrClient", "Sending request")
        val currentTimeMillis = System.currentTimeMillis()
        val oneHourAgoMillis = currentTimeMillis - 3600000
        val twoDaysAgoMillis = currentTimeMillis - 172800000
        Client.sendFilter(
            subscriptionSyncEventsId,
            listOf(
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            since = RelayPool.getAll().associate { it.url to EOSETime(oneHourAgoMillis / 1000) },
                        ),
                ),
            ),
        )
        Client.sendFilter(
            subscriptionSyncPrivateId,
            listOf(
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(1059),
                            since = RelayPool.getAll().associate { it.url to EOSETime(twoDaysAgoMillis / 1000) },
                        ),
                ),
            ),
        )
        Client.sendFilter(
            subscriptionSyncMetaId,
            listOf(
                TypedFilter(
                    types = EVENT_FINDER_TYPES,
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(0),
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
            Logger.d("BluetoothReconciliation", "Nostr note published to local relay : ${event.id.take(5)}...${event.id.takeLast(5)}")
            val db = AppDatabase.getDatabase(context, "common")
            val eventEntity = EventEntity(id = 0, eventId = event.id, createdAt = event.createdAt, local = 0)
            db.applicationDao().insertEvent(eventEntity)
        }
    }
}
