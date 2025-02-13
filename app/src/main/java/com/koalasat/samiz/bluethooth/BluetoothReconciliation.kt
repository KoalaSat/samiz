package com.koalasat.samiz.bluethooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.koalasat.samiz.util.Compression
import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import org.json.JSONArray
import org.json.JSONObject

class BluetoothReconciliation(context: Context) {
    private var reconciliationMessages = HashMap<String, ByteArray>()
    private var fullDevice = false

    var storageVectorServer: StorageVector = StorageVector().apply {
        insert(1678011277, "eb6b05c2e3b008592ac666594d78ed83e7b9ab30f825b9b08878128f7500008c")
        seal()
    }

    private val bluetoothBle = BluetoothBle(context, object : BluetoothBleCallback {
        override fun onConnection(bluetoothBle: BluetoothBle, device: BluetoothDevice) {
            bluetoothBle.readMessage(device)
        }

        override fun onReadResponse(
            bluetoothBle: BluetoothBle,
            device: BluetoothDevice,
            message: ByteArray
        ) {
            val jsonArray = JSONArray(String(message))
            val type = jsonArray.getString(0)
            Log.d("BluetoothReconciliation", "${device.address} - Read response received : $jsonArray")
            if (type == "NEG-OPEN") {
                val ne = Negentropy(storageVectorServer, 50_000)
                val byteArray = Compression.hexStringToByteArray(jsonArray.getString(3))
                val result = ne.reconcile(byteArray)
                val msg = result.msg
                if (msg != null) {
                    reconciliationMessages[device.address] = msg
                    val negMessage = negMessage(device, msg)
                    Log.d("BluetoothReconciliation", "${device.address} - Sending reconciliation response : $negMessage")
                    bluetoothBle.writeMessage(device, negMessage.toString().toByteArray())
                } else {
                    Log.d("BluetoothReconciliation", "${device.address} - Not reconciliation needed")
                }
            } else if (type == "EVENT") {
                Log.d("BluetoothReconciliation", "${device.address} - Received missing nostr note")
                val ne = Negentropy(storageVectorServer, 50_000)
                var reconciliation = reconciliationMessages[device.address]
                Log.d("BluetoothReconciliation", "${device.address} - Checking for needed messages")
                if (reconciliation != null) {
//                    val result = ne.reconcile(reconciliation)
                    if (!fullDevice) {
                        fullDevice = true
                        val event = nostrEvent()
                        val eventMessage = eventMessage(device, event)
                        Log.d("BluetoothReconciliation", "${device.address} - Sending missing event : $eventMessage")
                        bluetoothBle.writeMessage(device, eventMessage.toString().toByteArray())
                    } else {
                        Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
                        bluetoothBle.writeMessage(device, endMessage(device).toString().toByteArray())
                    }
                }
            } else if (type == "EOSE") {
                Log.d("BluetoothReconciliation", "${device.address} - No more events needed")
                val ne = Negentropy(storageVectorServer, 50_000)
                var reconciliation = reconciliationMessages[device.address]

                if (reconciliation != null) {
//                    val result = ne.reconcile(reconciliation)
                    if (!fullDevice) {
                        Log.d("BluetoothReconciliation", "${device.address} - Sending missing event")
                        fullDevice = true
                        val event = nostrEvent()
                        val eventMessage = eventMessage(device, event)
                        Log.d("BluetoothReconciliation", "${device.address} - Sending needed event : $eventMessage")
                        bluetoothBle.writeMessage(device, eventMessage.toString().toByteArray())
                    }
                } else {
                    Log.d("BluetoothReconciliation", "${device.address} - Peers are fully sync")
                }
            }
        }

        override fun onReadRequest(bluetoothBle: BluetoothBle, device: BluetoothDevice): ByteArray? {
            Log.d("BluetoothReconciliation", "${device.address} - Received read request")
            val ne = Negentropy(storageVectorServer, 50_000)
            var reconciliation = reconciliationMessages[device.address]
            if (reconciliation != null) {
                Log.d("BluetoothReconciliation", "${device.address} - Checking for needed messages")
//                val result = ne.reconcile(reconciliation)
                if (!fullDevice) {
                    Log.d("BluetoothReconciliation", "${device.address} - Sending missing event")
                    fullDevice = true
                    return eventMessage(device, nostrEvent()).toString().toByteArray()
                } else {
                    Log.d("BluetoothReconciliation", "${device.address} - No more events to send")
                    return endMessage(device).toString().toByteArray()
                }
            } else {
                Log.d("BluetoothReconciliation", "${device.address} - Generating negentropy init message")
                val msg = ne.initiate()
                return negOpenMessage(device, msg).toString().toByteArray()
            }

            return null
        }

        override fun onWriteRequest(bluetoothBle: BluetoothBle, device: BluetoothDevice, message: ByteArray) {
            Log.d("BluetoothReconciliation", "${device.address} - Write request received : ${String(message)}")
            val jsonArray = JSONArray(String(message))
            val type = jsonArray.getString(0)
            val msg = jsonArray.getString(2)
            if (type == "NEG-MSG") {
                if (msg != null) {
                    Log.d("BluetoothReconciliation", "${device.address} - Received negentropy reconciliation message")
                    reconciliationMessages[device.address] = Compression.hexStringToByteArray(msg)
                } else {
                    Log.d("BluetoothReconciliation", "${device.address} - Bad formated negentropy reconciliation message")
                }
            } else if (type == "EVENT") {
                Log.d("BluetoothReconciliation", "${device.address} - Received missing nostr note")
            } else {
                Log.d("BluetoothReconciliation", "${device.address} - Unknown message")
            }
        }

        override fun onWriteSuccess(
            bluetoothBle: BluetoothBle,
            device: BluetoothDevice
        ) {
            bluetoothBle.readMessage(device)
        }
    })

    fun start() {
        bluetoothBle.start()
    }

    fun nostrEvent(): String {

        val jsonObject = JSONObject()
        jsonObject.put("id", "4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65")
        jsonObject.put("pubkey", "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93")
        jsonObject.put("created_at", "1673347337")
        val jsonTags = JSONArray()
        jsonObject.put("tags", jsonTags)
        return jsonObject.toString()
    }

    fun negOpenMessage(device: BluetoothDevice, msg: ByteArray): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "NEG-OPEN") // type
        jsonArray.put(1, device.address.replace(":", ""))  // subscription ID
        jsonArray.put(2, "{}") // nostr filters
        jsonArray.put(3, Compression.byteArrayToHexString(msg))  // initial message
        return jsonArray
    }

    fun negMessage(device: BluetoothDevice, msg: ByteArray): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "NEG-MSG") // type
        jsonArray.put(1, device.address.replace(":", ""))  // subscription ID
        jsonArray.put(2, Compression.byteArrayToHexString(msg))  // reconciliation message
        return jsonArray
    }

    fun eventMessage(device: BluetoothDevice, msg: String): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "EVENT") // type
        jsonArray.put(1, device.address.replace(":", ""))  // subscription ID
        jsonArray.put(2, msg)  // reconciliation message
        return jsonArray
    }

    fun endMessage(device: BluetoothDevice): JSONArray {
        val jsonArray = JSONArray()
        jsonArray.put(0, "EOSE") // type
        jsonArray.put(1, device.address.replace(":", ""))  // subscription ID
        return jsonArray
    }
}