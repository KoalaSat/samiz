package com.koalasat.samiz.bluethooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.koalasat.samiz.Samiz
import com.koalasat.samiz.database.AppDatabase
import com.koalasat.samiz.database.EventEntity
import com.koalasat.samiz.model.Logger
import com.koalasat.samiz.model.NostrClient
import com.koalasat.samiz.util.Compression
import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.events.Event
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BluetoothReconciliation(var context: Context) {
    private var deviceSendIds = HashMap<String, MutableList<String>>()
    private var deviceReconciliation = HashMap<String, ByteArray>()

    private lateinit var deviceNegentropy: Negentropy

    private val nostrClient = NostrClient()

    fun start() {
        Logger.d("BluetoothReconciliation", "Starting BLE")
        bluetoothBle.start()

        Logger.d("BluetoothReconciliation", "Cleaning DB")
        val db = AppDatabase.getDatabase(context, "common")
        db.applicationDao().deleteAll()

        Logger.d("BluetoothReconciliation", "Starting nostr client")
        nostrClient.start(context) { event, afterEOSE ->
            if (afterEOSE) broadcastEvent(event, null)
        }
    }

    fun close() {
        nostrClient.close()
        bluetoothBle.close()
    }

    private val bluetoothBle =
        BluetoothBle(
            context,
            object : BluetoothBleCallback {
                override fun onConnection(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                ) {
                    Logger.d("BluetoothReconciliation", "${device.address} - Generating negentropy init message")
                    generateNegentropy()
                    val msg = deviceNegentropy.initiate()
                    val negOpenMsg = negOpenMessage(device, msg)
                    Logger.d("BluetoothReconciliation", "${device.address} - Sending OPEN message")
                    bluetoothBle.writeMessage(device, negOpenMsg.toString().toByteArray())
                }

                override fun onReadResponse(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                    message: ByteArray,
                ) {
                    var jsonArray: JSONArray = JSONArray()
                    var type: String? = null

                    try {
                        jsonArray = JSONArray(String(message))
                        type = jsonArray.getString(0)
                        Logger.d("BluetoothReconciliation", "${device.address} - Read response received")
                    } catch (e: JSONException) {
                        Logger.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                    }

                    if (type == "NEG-MSG") {
                        var msg: String? = null
                        try {
                            msg = jsonArray.getString(2)
                        } catch (e: JSONException) {
                            Logger.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                        }
                        if (msg != null) {
                            Logger.d("BluetoothReconciliation", "${device.address} - Received negentropy reconciliation message")
                            val result = deviceNegentropy.reconcile(Compression.hexStringToByteArray(msg))
                            if (result.sendIds.isNotEmpty() || result.needIds.isNotEmpty()) {
                                Logger.d(
                                    "BluetoothReconciliation",
                                    "${device.address} - Found ${result.sendIds.size} events to send",
                                )
                                deviceSendIds[device.address] = result.sendIds.map { it.toHexString() }.toMutableList()
                                Logger.d(
                                    "BluetoothReconciliation",
                                    "${device.address} - Found ${result.needIds.size} events to receive",
                                )
                                if (result.needIds.isNotEmpty()) {
                                    sendSubscriptionEvent(device, result.needIds.map { it.toHexString() })
                                } else {
                                    sendHaveEvent(device, true)
                                }
                            } else {
                                Logger.d("BluetoothReconciliation", "${device.address} - No reconciliation needed")
                            }
                        } else {
                            Logger.e("BluetoothReconciliation", "${device.address} - Bad formated negentropy reconciliation message")
                        }
                    } else if (type == "EVENT") {
                        try {
                            val msg = jsonArray.getString(2)
                            Logger.d("BluetoothReconciliation", "${device.address} - Received missing nostr note")
                            Samiz.updateReceivedEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
                            val event = Event.fromJson(msg)
                            newExternalEvent(event, device)
                        } catch (e: JSONException) {
                            Logger.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                        }
                        sendHaveEvent(device, true)
                    } else if (type == "EOSE") {
                        Logger.d("BluetoothReconciliation", "${device.address} - All missing events received")
                        sendHaveEvent(device, false)
                    }
                }

                override fun onReadRequest(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                ): ByteArray? {
                    Logger.d("BluetoothReconciliation", "${device.address} - Received read request")
                    val reconciliation = deviceReconciliation.getOrDefault(device.address, null)
                    val needIds = deviceSendIds.getOrDefault(device.address, null)
                    if (reconciliation != null) {
                        Logger.d("BluetoothReconciliation", "${device.address} - Sending reconciliation messages")
                        deviceReconciliation.remove(device.address)
                        return negMessage(device, reconciliation).toString().toByteArray()
                    } else if (needIds != null) {
                        Logger.d("BluetoothReconciliation", "${device.address} - Checking needed event")
                        if (needIds.isNotEmpty()) {
                            val eventId = needIds.last().toString()
                            deviceSendIds[device.address] = needIds.dropLast(1).toMutableList()
                            var event = getEvent(eventId)
                            if (event != null) {
                                try {
                                    var json = event.toJson()
                                    Logger.d("BluetoothReconciliation", "${device.address} - Generating missing event : $eventId")
                                    Logger.d(
                                        "BluetoothReconciliation",
                                        "${device.address} - ${deviceSendIds[device.address]?.size} events left",
                                    )
                                    Samiz.updateSentEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
                                    return eventMessage(device, json).toString().toByteArray()
                                } catch (e: JSONException) {
                                    Logger.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadRequest : $e")
                                    return eventMessage(device, "").toString().toByteArray()
                                }
                            } else {
                                Logger.d("BluetoothReconciliation", "${device.address} - Event $eventId not found")
                            }
                        } else {
                            Logger.d("BluetoothReconciliation", "${device.address} - No more events to send")
                        }
                    }

                    return endMessage(device).toString().toByteArray()
                }

                override fun onWriteRequest(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                    message: ByteArray,
                ) {
                    var jsonArray: JSONArray = JSONArray()
                    var type: String? = null

                    try {
                        jsonArray = JSONArray(String(message))
                        type = jsonArray.getString(0)
                        Logger.d("BluetoothReconciliation", "${device.address} - Write request received")
                    } catch (e: JSONException) {
                        Logger.d("BluetoothReconciliation", "${device.address} - invalid JSON onWriteRequest : $e")
                    }
                    if (type == "NEG-OPEN") {
                        Logger.d("BluetoothReconciliation", "${device.address} - NEG-OPEN")
                        generateNegentropy()
                        val byteArray = Compression.hexStringToByteArray(jsonArray.getString(3))
                        val result = deviceNegentropy.reconcile(byteArray)
                        val msg = result.msg
                        if (msg != null) {
                            deviceReconciliation[device.address] = msg
                            Logger.d(
                                "BluetoothReconciliation",
                                "${device.address} - Reconciliation message ${result.msgToString()}",
                            )
                        } else {
                            Logger.d(
                                "BluetoothReconciliation",
                                "${device.address} - Not reconciliation needed",
                            )
                        }
                    } else if (type == "EVENT") {
                        val msg = jsonArray.getString(2)
                        Logger.d("BluetoothReconciliation", "${device.address} - Received missing nostr note")
                        Samiz.updateReceivedEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
                        val event = Event.fromJson(msg)
                        newExternalEvent(event, device)
                    } else if (type == "REQ") {
                        try {
                            val filtersString = jsonArray.getString(2)
                            val filters = JSONObject(filtersString)
                            val jsonIds = filters.getJSONArray("ids")
                            val ids = (0 until jsonIds.length()).map { jsonIds.getString(it) }
                            if (ids.isNotEmpty()) {
                                deviceSendIds[device.address] = ids.toMutableList()
                                Logger.d("BluetoothReconciliation", "${device.address} - Device needs ${ids.size} events")
                            } else {
                                Logger.d("BluetoothReconciliation", "${device.address} - Device needs no events")
                            }
                        } catch (e: JSONException) {
                            Logger.d("BluetoothReconciliation", "${device.address} - invalid JSON onWriteRequest: $e")
                        }
                    }
                }

                override fun onWriteSuccess(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                ) {
                    val sendIds = deviceSendIds[device.address]
                    if (sendIds != null && sendIds.isNotEmpty()) {
                        sendHaveEvent(device, true)
                    } else {
                        bluetoothBle.readMessage(device)
                    }
                }

                override fun onCharacteristicChanged(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                ) {
                    bluetoothBle.readMessage(device)
                }
            },
        )

    private fun sendHaveEvent(
        device: BluetoothDevice,
        read: Boolean,
    ) {
        Logger.d("BluetoothReconciliation", "${device.address} - Checking for needed messages")
        var sendIds = deviceSendIds[device.address]
        if (sendIds != null) {
            if (sendIds.isNotEmpty()) {
                val eventId = sendIds.last().toString()
                deviceSendIds[device.address] = sendIds.dropLast(1).toMutableList()
                var event = getEvent(eventId)
                if (event != null) {
                    writeEvent(device, event)
                } else {
                    Logger.d("BluetoothReconciliation", "${device.address} - Event $eventId not found")
                }
            } else {
                Logger.d("BluetoothReconciliation", "${device.address} - No more events to send")
                if (read) bluetoothBle.readMessage(device)
            }
        } else {
            Logger.d("BluetoothReconciliation", "${device.address} - No more events to send")
        }
    }

    private fun writeEvent(
        device: BluetoothDevice,
        event: Event,
    ) {
        try {
            var json = event.toJson()
            Logger.d("BluetoothReconciliation", "${device.address} - Sending missing event : ${event.id.take(5)}...${event.id.takeLast(5)}")
            val message = eventMessage(device, json)
            bluetoothBle.writeMessage(device, message.toString().toByteArray())
            Samiz.updateSentEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
            Logger.d("BluetoothReconciliation", "${device.address} - ${deviceSendIds[device.address]?.size} events left")
            return
        } catch (e: JSONException) {
            Logger.d("BluetoothReconciliation", "${device.address} - invalid JSON sendHaveEvent: $e")
        }
    }

    private fun sendSubscriptionEvent(
        device: BluetoothDevice,
        needIds: List<String>,
    ) {
        val filters = JSONObject()
        filters.put("ids", JSONArray(needIds))
        val message = subscriptionMessage(device, filters)
        bluetoothBle.writeMessage(device, message.toString().toByteArray())
    }

    fun negOpenMessage(
        device: BluetoothDevice,
        msg: ByteArray,
    ): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "NEG-OPEN") // type
        jsonArray.put(1, device.address.replace(":", "")) // subscription ID
        jsonArray.put(2, "{}") // nostr filters
        jsonArray.put(3, Compression.byteArrayToHexString(msg)) // initial message
        return jsonArray
    }

    fun negMessage(
        device: BluetoothDevice,
        msg: ByteArray,
    ): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "NEG-MSG") // type
        jsonArray.put(1, device.address.replace(":", "")) // subscription ID
        jsonArray.put(2, Compression.byteArrayToHexString(msg)) // reconciliation message
        return jsonArray
    }

    fun eventMessage(
        device: BluetoothDevice,
        msg: String,
    ): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "EVENT") // type
        jsonArray.put(1, device.address.replace(":", "")) // subscription ID
        jsonArray.put(2, msg) // reconciliation message
        return jsonArray
    }

    fun subscriptionMessage(
        device: BluetoothDevice,
        filter: JSONObject,
    ): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "REQ") // type
        jsonArray.put(1, device.address.replace(":", "")) // subscription ID
        jsonArray.put(2, filter.toString()) // reconciliation message
        return jsonArray
    }

    fun endMessage(device: BluetoothDevice): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "EOSE") // type
        jsonArray.put(1, device.address.replace(":", "")) // subscription ID
        return jsonArray
    }

    private fun generateNegentropy() {
        val sessionEvents =
            StorageVector().apply {
                val db = AppDatabase.getDatabase(context, "common")

                try {
                    val events = db.applicationDao().getEvents()
                    events.forEach {
                        insert(it.createdAt, it.eventId)
                    }
                } finally {
                    seal()
                }
            }

        Logger.d("BluetoothReconciliation", "Negentropy generated for ${sessionEvents.size()} events")
        deviceNegentropy = Negentropy(sessionEvents, 50_000)
    }

    private fun getEvent(eventId: String): Event? {
        var timeout: Long = 5
        var timeUnit = TimeUnit.SECONDS
        val future = CompletableFuture<Event>()

        nostrClient.getEvent(eventId) { event ->
            future.complete(event)
        }

        return try {
            future.get(timeout, timeUnit)
        } catch (e: TimeoutException) {
            Logger.d("BluetoothReconciliation", "Response from local relay timeout for eventId : $eventId")
            null
        }
    }

    private fun broadcastEvent(
        event: Event,
        device: BluetoothDevice?,
    ) {
        try {
            var json = event.toJson()
            bluetoothBle.servers.values.forEach {
                if (it.address != device?.address) {
                    val message = eventMessage(it, json)
                    Logger.d(
                        "BluetoothReconciliation",
                        "Broadcasting event id ${event.id.take(5)}...${event.id.takeLast(5)} to ${it.address}",
                    )
                    bluetoothBle.writeMessage(it, message.toString().toByteArray())
                    Samiz.updateSentEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
                }
            }
            bluetoothBle.clients.values.forEach { device ->
                var list = deviceSendIds[device.address]
                if (list == null || list.isEmpty()) {
                    list = mutableListOf<String>()
                    list.add(event.id)
                    deviceSendIds[device.address] = list
                    Logger.d("BluetoothReconciliation", "Notifying to ${device.address}")
                    bluetoothBle.notifyClient(device)
                } else {
                    Logger.d("BluetoothReconciliation", "Queuing event to ${device.address}")
                    list.add(event.id)
                    deviceSendIds[device.address] = list
                }
            }
        } catch (e: JSONException) {
            Logger.d("BluetoothReconciliation", "Invalid JSON sendHaveEvent: $e")
        }
    }

    private fun newExternalEvent(
        event: Event,
        device: BluetoothDevice,
    ) {
        Logger.d("BluetoothReconciliation", "${device.address} - New External event id : ${event.id.take(5)}...${event.id.takeLast(5)}")
        val db = AppDatabase.getDatabase(context, "common")
        val existsEvent = db.applicationDao().existsEvent(event.id)

        if (existsEvent < 1) {
            val eventEntity = EventEntity(id = 0, eventId = event.id, createdAt = event.createdAt, local = 1)
            db.applicationDao().insertEvent(eventEntity)

            nostrClient.publishEvent(event, context)
            generateNegentropy()
            broadcastEvent(event, null)
        }
    }
}
