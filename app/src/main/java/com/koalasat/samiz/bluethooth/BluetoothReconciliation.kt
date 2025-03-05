package com.koalasat.samiz.bluethooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.koalasat.samiz.database.AppDatabase
import com.koalasat.samiz.database.EventEntity
import com.koalasat.samiz.model.NostrClient
import com.koalasat.samiz.util.Compression
import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.events.Event
import org.json.JSONArray
import java.util.concurrent.CompletableFuture

class BluetoothReconciliation(var context: Context) {
    private var deviceSendIds = HashMap<String, List<String>>()

    private lateinit var deviceNegentropy: Negentropy

    private val nostrClient = NostrClient()

    fun start() {
        Log.d("NostrClient", "Starting bluetooth")
        bluetoothBle.start()

        Log.d("NostrClient", "Starting nostr client")
        nostrClient.start()
        nostrClient.getEvents {
            Log.d("NostrClient", "Nostr note received : ${it.toJson()}")
            val db = AppDatabase.getDatabase(context, "common")
            val eventEntity = EventEntity(id = 0, eventId = it.id, createdAt = it.createdAt)
            db.applicationDao().insertEvent(eventEntity)
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
                    bluetoothBle.readMessage(device)
                }

                override fun onReadResponse(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                    message: ByteArray,
                ) {
                    val jsonArray = JSONArray(String(message))
                    val type = jsonArray.getString(0)
                    Log.d("BluetoothReconciliation", "${device.address} - Read response received : $jsonArray")
                    if (type == "NEG-OPEN") {
                        deviceNegentropy = getNegentropy()
                        val byteArray = Compression.hexStringToByteArray(jsonArray.getString(3))
                        val result = deviceNegentropy.reconcile(byteArray)
                        val msg = result.msg
                        if (msg != null) {
                            deviceSendIds[device.address] = result.sendIds.map { it.toHexString() }
                            Log.d("BluetoothReconciliation", "${device.address} - Found ${result.sendIds.size} events to send")
                            val negMessage = negMessage(device, msg)
                            Log.d("BluetoothReconciliation", "${device.address} - Sending reconciliation response : $negMessage")
                            bluetoothBle.writeMessage(device, negMessage.toString().toByteArray())
                        } else {
                            Log.d("BluetoothReconciliation", "${device.address} - Not reconciliation needed")
                        }
                    } else if (type == "EVENT" || type == "EOSE") {
                        Log.d("BluetoothReconciliation", "${device.address} - Received missing nostr note")
                        Log.d("BluetoothReconciliation", "${device.address} - Checking for needed messages")
                        var haveIds = deviceSendIds[device.address]
                        if (haveIds != null) {
                            if (haveIds.isNotEmpty()) {
                                val eventId = haveIds.last().toString()
                                deviceSendIds[device.address] = haveIds.dropLast(1)

                                Log.d("BluetoothReconciliation", "${device.address} - Found missing event id : $eventId")
                                var event = getEvent(eventId).toJson()
                                Log.d("BluetoothReconciliation", "${device.address} - Sending missing event : $event")
                                bluetoothBle.writeMessage(device, event.toByteArray())
                            } else {
                                Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
                                if (type == "EVENT") {
                                    Log.d("BluetoothReconciliation", "${device.address} - Peers are fully Sync")
                                    val endMessage = endMessage(device).toString()
                                    Log.d("BluetoothReconciliation", "${device.address} - Sending end message : $endMessage")
                                    bluetoothBle.writeMessage(device, endMessage.toByteArray())
                                }
                            }
                        } else {
                            Log.e("BluetoothReconciliation", "${device.address} - Device not found")
                        }
                    } else {
                        Log.e("BluetoothReconciliation", "${device.address} - Unknown event type")
                    }
                }

                override fun onReadRequest(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                ): ByteArray? {
                    Log.d("BluetoothReconciliation", "${device.address} - Received read request")
                    Log.d("BluetoothReconciliation", "${device.address} - Checking for needed messages")
                    var haveIds = deviceSendIds[device.address]
                    if (haveIds != null) {
                        if (haveIds.isNotEmpty()) {
                            val eventId = haveIds.last().toString()
                            Log.d("BluetoothReconciliation", "${device.address} - Obtaining missing event id : $eventId")
                            deviceSendIds[device.address] = haveIds.dropLast(1)
                            var event = getEvent(eventId).toJson()
                            Log.d("BluetoothReconciliation", "${device.address} - Sending missing event : $event")
                            return eventMessage(device, event).toString().toByteArray()
                        } else {
                            Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
                            return endMessage(device).toString().toByteArray()
                        }
                    } else {
                        Log.d("BluetoothReconciliation", "${device.address} - Generating negentropy init message")
                        deviceNegentropy = getNegentropy()
                        val msg = deviceNegentropy.initiate()
                        deviceSendIds[device.address] = emptyList()
                        return negOpenMessage(device, msg).toString().toByteArray()
                    }

                    return null
                }

                override fun onWriteRequest(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                    message: ByteArray,
                ) {
                    Log.d("BluetoothReconciliation", "${device.address} - Write request received : ${String(message)}")
                    val jsonArray = JSONArray(String(message))
                    val type = jsonArray.getString(0)
                    if (type == "NEG-MSG") {
                        val msg = jsonArray.getString(2)
                        if (msg != null) {
                            Log.d("BluetoothReconciliation", "${device.address} - Received negentropy reconciliation message")
                            val result = deviceNegentropy.reconcile(Compression.hexStringToByteArray(msg))
                            if (result.sendIds.isNotEmpty()) {
                                deviceSendIds[device.address] = result.sendIds.map { it.toHexString() }
                                Log.d(
                                    "BluetoothReconciliation",
                                    "${device.address} - Found ${result.sendIds.size} events to send",
                                )
                            } else {
                                Log.d("BluetoothReconciliation", "${device.address} - No reconciliation needed")
                            }
                        } else {
                            Log.d("BluetoothReconciliation", "${device.address} - Bad formated negentropy reconciliation message")
                        }
                    } else if (type == "EVENT") {
                        val msg = jsonArray.getString(1)
                        Log.d("BluetoothReconciliation", "${device.address} - Received missing nostr note : $msg")
                    } else {
                        Log.d("BluetoothReconciliation", "${device.address} - Unknown message")
                    }
                }

                override fun onWriteSuccess(
                    bluetoothBle: BluetoothBle,
                    device: BluetoothDevice,
                ) {
                    bluetoothBle.readMessage(device)
                }
            },
        )

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
                        Log.d("BluetoothReconciliation", "Found event : ${it.createdAt} : ${it.eventId}")
                        insert(it.createdAt, it.eventId)
                    }
                } finally {
                    seal()
                }
            }

        Log.d("BluetoothReconciliation", "Negentropy generated for ${storageVector.size()} events")
        return Negentropy(storageVector, 50_000)
    }

    private fun getEvent(eventId: String): Event {
        val future = CompletableFuture<Event>()
        nostrClient.getEvent(eventId) { event ->
            Log.d("BluetoothReconciliation", "event ${event.toJson()}")
            future.complete(event)
        }

        return future.get()
    }
}
