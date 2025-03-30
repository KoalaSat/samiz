package com.koalasat.samiz.bluethooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.koalasat.samiz.Samiz
import com.koalasat.samiz.database.AppDatabase
import com.koalasat.samiz.model.NostrClient
import com.koalasat.samiz.util.Compression
import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.events.Event
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BluetoothReconciliation(var context: Context) {
    private var deviceSendIds = HashMap<String, List<String>>()
    private var deviceReconciliation = HashMap<String, ByteArray>()
    private var syncedDevices = HashMap<String, LocalDateTime>()

    private lateinit var deviceNegentropy: Negentropy

    private val nostrClient = NostrClient()

    fun start() {
        Log.d("NostrClient", "Starting bluetooth")
        bluetoothBle.start()

        Log.d("NostrClient", "Cleaning DB")
        val db = AppDatabase.getDatabase(context, "common")
        db.applicationDao().deleteAll()

        Log.d("NostrClient", "Starting nostr client")
        nostrClient.start(context)
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
                    syncedDevices.remove(device.address)
                    deviceNegentropy = getNegentropy()
                    val msg = deviceNegentropy.initiate()
                    val negOpenMsg = negOpenMessage(device, msg)
                    Log.d("BluetoothReconciliation", "${device.address} - Sending OPEN message - $negOpenMsg")
                    bluetoothBle.writeMessage(device, negOpenMsg.toString().toByteArray())
                    Samiz.updateFoundDevices(device.address)
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
                        Log.d("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                    }

                    if (type == "NEG-MSG") {
                        var msg: String? = null
                        try {
                            msg = jsonArray.getString(2)
                        } catch (e: JSONException) {
                            Log.d("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                        }
                        if (msg != null) {
                            Log.d("BluetoothReconciliation", "${device.address} - Received negentropy reconciliation message")
                            val result = deviceNegentropy.reconcile(Compression.hexStringToByteArray(msg))
                            if (result.sendIds.isNotEmpty() || result.needIds.isNotEmpty()) {
                                deviceSendIds[device.address] = result.sendIds.map { it.toHexString() }
                                Log.d(
                                    "BluetoothReconciliation",
                                    "${device.address} - Found ${result.sendIds.size} events to send",
                                )
                                Log.d(
                                    "BluetoothReconciliation",
                                    "${device.address} - Found ${result.needIds.size} events to receive",
                                )
                                if (result.needIds.isNotEmpty()) {
                                    sendSubscriptionEvent(device, result.needIds.map { it.toHexString() })
                                } else {
                                    sendHaveEvent(device)
                                }
                            } else {
                                Log.d("BluetoothReconciliation", "${device.address} - No reconciliation needed")
                            }
                        } else {
                            Log.d("BluetoothReconciliation", "${device.address} - Bad formated negentropy reconciliation message")
                        }
                    } else if (type == "EVENT") {
                        try {
                            val msg = jsonArray.getString(2)
                            Log.d("BluetoothReconciliation", "${device.address} - Received missing nostr note : $msg")
                            val event = Event.fromJson(msg)
                            nostrClient.publishEvent(event, context)
                            Samiz.updateReceivedEvents(Samiz.receivedEvents.value?.plus(1) ?: 0)
                        } catch (e: JSONException) {
                            Log.d("BluetoothReconciliation", "${device.address} - invalid JSON onReadResponse : $e")
                        }
                        sendHaveEvent(device)
                    } else if (type == "EOSE") {
                        Log.d("BluetoothReconciliation", "${device.address} - All missing events received")
                        syncedDevices.put(device.address, LocalDateTime.now())
                        sendHaveEvent(device)
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
                            deviceSendIds[device.address] = needIds.dropLast(1)
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
                                    Log.d("BluetoothReconciliation", "${device.address} - invalid JSON onReadRequest : $e")
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
                        Samiz.updateFoundDevices(device.address)
                        deviceNegentropy = getNegentropy()
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
                        val event = Event.fromJson(msg)
                        nostrClient.publishEvent(event, context)
                        Samiz.updateReceivedEvents(Samiz.receivedEvents.value?.plus(1) ?: 0)
                    } else if (type == "REQ") {
                        try {
                            val filtersString = jsonArray.getString(2)
                            val filters = JSONObject(filtersString)
                            val jsonIds = filters.getJSONArray("ids")
                            val ids = (0 until jsonIds.length()).map { jsonIds.getString(it) }
                            deviceSendIds[device.address] = ids
                            if (ids.isNotEmpty()) {
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
                    if (syncedDevices.containsKey(device.address)) {
                        sendHaveEvent(device)
                    } else {
                        bluetoothBle.readMessage(device)
                    }
                }
            },
        )

    private fun sendHaveEvent(device: BluetoothDevice) {
        Log.d("BluetoothReconciliation", "${device.address} - Checking for needed messages")
        var sendIds = deviceSendIds[device.address]
        if (sendIds != null) {
            if (sendIds.isNotEmpty()) {
                val eventId = sendIds.last().toString()
                deviceSendIds[device.address] = sendIds.dropLast(1)
                var event = getEvent(eventId)
                if (event != null) {
                    try {
                        var json = event.toJson()
                        Log.d("BluetoothReconciliation", "${device.address} - Sending missing event : $eventId")
                        val message = eventMessage(device, json)
                        bluetoothBle.writeMessage(device, message.toString().toByteArray())
                        Samiz.updateSentEvents(Samiz.sentEvents.value?.plus(1) ?: 0)
                        Log.d("BluetoothReconciliation", "${device.address} - ${deviceSendIds[device.address]?.size} events left")
                        return
                    } catch (e: JSONException) {
                        Log.d("BluetoothReconciliation", "${device.address} - invalid JSON sendHaveEvent: $e")
                    }
                } else {
                    Log.d("BluetoothReconciliation", "${device.address} - Event $eventId not found")
                }

                Log.d("BluetoothReconciliation", "${device.address} - ${deviceSendIds[device.address]?.size} events left")
                sendHaveEvent(device)
            } else {
                Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
                deviceSendIds.remove(device.address)
            }
        } else {
            Log.e("BluetoothReconciliation", "${device.address} - Device not found")
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

    private fun getNegentropy(): Negentropy {
        var storageVector =
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

        Log.d("BluetoothReconciliation", "Negentropy generated for ${storageVector.size()} events")
        return Negentropy(storageVector, 50_000)
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
}
