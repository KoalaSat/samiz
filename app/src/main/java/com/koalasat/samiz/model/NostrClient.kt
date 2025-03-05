package com.koalasat.samiz.model

import android.util.Log
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.events.Event

class NostrClient {
    private var subscriptionSyncId = "samizSync"
    private var subscriptionEventId = "samizEvent"

    private var defaultRelayUrls =
        listOf(
            "ws://127.0.0.1:4869",
        )

    fun start() {
        RelayPool.register(Client)

        Log.d("NostrClient", "Connecting to local relay")
        connectRelays()
    }

    fun close() {
        RelayPool.unloadRelays()
    }

    private fun connectRelays() {
        defaultRelayUrls.forEach {
            Client.sendFilterOnlyIfDisconnected()
            if (RelayPool.getRelays(it).isEmpty()) {
                RelayPool.addRelay(
                    Relay(
                        it,
                        read = true,
                        write = false,
                        forceProxy = false,
                        activeTypes = COMMON_FEED_TYPES,
                    ),
                )
            }
        }
    }

    fun getEvents(onResponse: (Event) -> Unit) {
        Client.sendFilterAndStopOnFirstResponse(
            subscriptionSyncId,
            listOf(
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter = SincePerRelayFilter(),
                ),
            ),
            onResponse = { event ->
                onResponse(event)
            },
        )
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
                        ),
                ),
            ),
            onResponse = { event ->
                onResponse(event)
            },
        )
    }
}
