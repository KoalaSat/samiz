package com.koalasat.samiz.bluethooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.koalasat.samiz.Samiz
import com.koalasat.samiz.database.AppDatabase
import com.koalasat.samiz.database.EventEntity
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
        Log.d("BluetoothReconciliation", "Starting BLE")
        bluetoothBle.start()

        Log.d("BluetoothReconciliation", "Cleaning DB")
        val db = AppDatabase.getDatabase(context, "common")
        db.applicationDao().deleteAll()

        Log.d("BluetoothReconciliation", "Starting nostr client")
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
                    Log.d("BluetoothReconciliation", "${device.address} - Generating negentropy init message")
                    generateNegentropy()
                    val msg = deviceNegentropy.initiate()
                    val negOpenMsg = negOpenMessage(device, msg)
                    Log.d("BluetoothReconciliation", "${device.address} - Sending OPEN message - $negOpenMsg")
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
                        Log.d("BluetoothReconciliation", "${device.address} - Read response received : $jsonArray")
                    } catch (e: JSONException) {
                        Log.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                    }

                    if (type == "NEG-MSG") {
                        var msg: String? = null
                        try {
                            msg = jsonArray.getString(2)
                        } catch (e: JSONException) {
                            Log.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                        }
                        if (msg != null) {
                            Log.d("BluetoothReconciliation", "${device.address} - Received negentropy reconciliation message")
                            val result = deviceNegentropy.reconcile(Compression.hexStringToByteArray(msg))
                            if (result.sendIds.isNotEmpty() || result.needIds.isNotEmpty()) {
                                Log.d(
                                    "BluetoothReconciliation",
                                    "${device.address} - Found ${result.sendIds.size} events to send",
                                )
                                deviceSendIds[device.address] = result.sendIds.map { it.toHexString() }.toMutableList()
                                Log.d(
                                    "BluetoothReconciliation",
                                    "${device.address} - Found ${result.needIds.size} events to receive",
                                )
                                if (result.needIds.isNotEmpty()) {
                                    sendSubscriptionEvent(device, result.needIds.map { it.toHexString() })
                                } else {
                                    sendHaveEvent(device, true)
                                }
                            } else {
                                Log.d("BluetoothReconciliation", "${device.address} - No reconciliation needed")
                            }
                        } else {
                            Log.e("BluetoothReconciliation", "${device.address} - Bad formated negentropy reconciliation message")
                        }
                    } else if (type == "EVENT") {
                        try {
                            val msg = jsonArray.getString(2)
                            Log.d("BluetoothReconciliation", "${device.address} - Received missing nostr note : $msg")
                            val event = Event.fromJson(msg)
                            newExternalEvent(event, device)
                        } catch (e: JSONException) {
                            Log.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                        }
                        sendHaveEvent(device, true)
                    } else if (type == "EOSE") {
                        Log.d("BluetoothReconciliation", "${device.address} - All missing events received")
                        sendHaveEvent(device, false)
                    }
                }

                override fun onReadRequest(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                ): ByteArray? {
                    Log.d("BluetoothReconciliation", "${device.address} - Received read request")
                    val reconciliation = deviceReconciliation.getOrDefault(device.address, null)
                    val needIds = deviceSendIds.getOrDefault(device.address, null)
                    if (reconciliation != null) {
                        Log.d("BluetoothReconciliation", "${device.address} - Sending reconciliation messages")
                        deviceReconciliation.remove(device.address)
                        return negMessage(device, reconciliation).toString().toByteArray()
                    } else if (needIds != null) {
                        Log.d("BluetoothReconciliation", "${device.address} - Checking needed event")
                        if (needIds.isNotEmpty()) {
                            val eventId = needIds.last().toString()
                            deviceSendIds[device.address] = needIds.dropLast(1).toMutableList()
                            var event = getEvent(eventId)
                            if (event != null) {
                                try {
                                    var json = event.toJson()
                                    Log.d("BluetoothReconciliation", "${device.address} - Generating missing event : $eventId")
                                    Log.d(
                                        "BluetoothReconciliation",
                                        "${device.address} - ${deviceSendIds[device.address]?.size} events left",
                                    )
                                    Samiz.updateSentEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
                                    return eventMessage(device, json).toString().toByteArray()
                                } catch (e: JSONException) {
                                    Log.e("BluetoothReconciliation", "${device.address} - invalid JSON onReadRequest : $e")
                                    return eventMessage(device, "").toString().toByteArray()
                                }
                            } else {
                                Log.d("BluetoothReconciliation", "${device.address} - Event $eventId not found")
                            }
                        } else {
                            Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
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
                        Log.d("BluetoothReconciliation", "${device.address} - Write request received : ${String(message)}")
                    } catch (e: JSONException) {
                        Log.d("BluetoothReconciliation", "${device.address} - invalid JSON onWriteRequest : $e")
                    }
                    if (type == "NEG-OPEN") {
                        Log.d("BluetoothReconciliation", "${device.address} - NEG-OPEN")
                        generateNegentropy()
                        val byteArray = Compression.hexStringToByteArray(jsonArray.getString(3))
                        val result = deviceNegentropy.reconcile(byteArray)
                        val msg = result.msg
                        if (msg != null) {
                            deviceReconciliation[device.address] = msg
                            Log.d(
                                "BluetoothReconciliation",
                                "${device.address} - Reconciliation message ${result.msgToString()}",
                            )
                        } else {
                            Log.d(
                                "BluetoothReconciliation",
                                "${device.address} - Not reconciliation needed",
                            )
                        }
                    } else if (type == "EVENT") {
                        val msg = jsonArray.getString(2)
                        Log.d("BluetoothReconciliation", "${device.address} - Received missing nostr note : $msg")
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
                                Log.d("BluetoothReconciliation", "${device.address} - Device needs ${ids.size} events")
                            } else {
                                Log.d("BluetoothReconciliation", "${device.address} - Device needs no events")
                            }
                        } catch (e: JSONException) {
                            Log.d("BluetoothReconciliation", "${device.address} - invalid JSON onWriteRequest: $e")
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
        Log.d("BluetoothReconciliation", "${device.address} - Checking for needed messages")
        var sendIds = deviceSendIds[device.address]
        if (sendIds != null) {
            if (sendIds.isNotEmpty()) {
                val eventId = sendIds.last().toString()
                deviceSendIds[device.address] = sendIds.dropLast(1).toMutableList()
                var event = getEvent(eventId)
                if (event != null) {
                    writeEvent(device, event)
                } else {
                    Log.d("BluetoothReconciliation", "${device.address} - Event $eventId not found")
                }
            } else {
                Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
                if (read) bluetoothBle.readMessage(device)
            }
        } else {
            Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
        }
    }

    private fun writeEvent(
        device: BluetoothDevice,
        event: Event,
    ) {
        try {
            var json = event.toJson()
            Log.d("BluetoothReconciliation", "${device.address} - Sending missing event : ${event.id}")
            val message = eventMessage(device, json)
            bluetoothBle.writeMessage(device, message.toString().toByteArray())
            Samiz.updateSentEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
            Log.d("BluetoothReconciliation", "${device.address} - ${deviceSendIds[device.address]?.size} events left")
            return
        } catch (e: JSONException) {
            Log.d("BluetoothReconciliation", "${device.address} - invalid JSON sendHaveEvent: $e")
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

        Log.d("BluetoothReconciliation", "Negentropy generated for ${sessionEvents.size()} events")
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
            Log.d("BluetoothReconciliation", "Response from local relay timeout for eventId : $eventId")
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
                    Log.d("BluetoothReconciliation", "Broadcasting event id ${event.id} to ${it.address}")
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
                    Log.d("BluetoothReconciliation", "Notifying to ${device.address}")
                    bluetoothBle.notifyClient(device)
                } else {
                    Log.d("BluetoothReconciliation", "Queuing event to ${device.address}")
                    list.add(event.id)
                    deviceSendIds[device.address] = list
                }
            }
        } catch (e: JSONException) {
            Log.d("BluetoothReconciliation", "Invalid JSON sendHaveEvent: $e")
        }
    }

    private fun newExternalEvent(
        event: Event,
        device: BluetoothDevice,
    ) {
        Log.d("BluetoothReconciliation", "${device.address} - New External event id : ${event.id}")
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
