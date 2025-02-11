package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.util.Log
import com.koalasat.samiz.util.Compression.Companion.joinChunks
import com.koalasat.samiz.util.Compression.Companion.splitInChunks
import kotlin.collections.set

class BluetoothBleServer(private var bluetoothBle: BluetoothBle) {
    private lateinit var bluetoothGattServer: BluetoothGattServer
    private var initialMessages = HashMap<String, Array<ByteArray>>()
    private var reconciliationMessages = HashMap<String, Array<ByteArray>>()
    private var eventMessages = HashMap<String, Array<ByteArray>>()

    @SuppressLint("MissingPermission")
    fun createGattServer() {
        bluetoothGattServer =
            bluetoothBle.bluetoothManager.openGattServer(
                bluetoothBle.context,
                object : BluetoothGattServerCallback() {
                    override fun onServiceAdded(
                        status: Int,
                        service: BluetoothGattService,
                    ) {
                        super.onServiceAdded(status, service)
                    }

                    override fun onCharacteristicReadRequest(
                        device: BluetoothDevice,
                        requestId: Int,
                        offset: Int,
                        characteristic: BluetoothGattCharacteristic,
                    ) {
                        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                        Log.d("BluetoothBleServer", "${device.address} - Read request")
                        if (characteristic.uuid.equals(bluetoothBle.characteristicUUID)) {
                            sendInitialMessage(device, requestId, characteristic)
                        } else {
                            Log.e("BluetoothBleServer", "${device.address} - Read not permitted")
                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                        }
                    }

                    override fun onCharacteristicWriteRequest(
                        device: BluetoothDevice,
                        requestId: Int,
                        characteristic: BluetoothGattCharacteristic,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray,
                    ) {
                        super.onCharacteristicWriteRequest(
                            device,
                            requestId,
                            characteristic,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value,
                        )
                        Log.d(
                            "BluetoothBleServer",
                            "${device.address} - Write request",
                        )
                        // Get the value of the characteristic
                        processReconciliationMessage(device, characteristic, value)
                        processEvent(device, characteristic, value)
                    }
                },
            )
    }

    @SuppressLint("MissingPermission")
    private fun sendReadData(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        jsonBytes: ByteArray,
    ) {
        var offset = 0

        while (offset < jsonBytes.size) {
            val chunkSize = minOf(bluetoothBle.mtuSize, jsonBytes.size + offset)
            val chunk = jsonBytes.copyOfRange(offset, minOf(offset + chunkSize, jsonBytes.size))
            characteristic.value = chunk
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
            offset += chunkSize
        }
    }

    private fun sendInitialMessage(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,) {
        val jsonBytes =
            if (initialMessages.containsKey(device.address)) {
                initialMessages.getOrDefault(device.address, emptyArray())
            } else {
                var message = BluetoothReconciliation().getInitialMessage()
                Log.d("BluetoothBleServer", "${device.address} - Created initial message : $message")
                if (message != null) {
                    var chunks = splitInChunks(message)
                    Log.d("BluetoothBleServer", "${device.address} - Created ${chunks.size} chunks")
                    chunks
                } else {
                    emptyArray()
                }
            }

        if (jsonBytes.isNotEmpty()) {
            Log.d("BluetoothBleServer", "${device.address} - Sending Chunk $jsonBytes ${jsonBytes.size}")
            val nextChunk = jsonBytes.first()
            initialMessages[device.address] = jsonBytes.copyOfRange(1, jsonBytes.size)

            sendReadData(device, requestId, characteristic, nextChunk)

            val isLastChunk = nextChunk[nextChunk.size - 1] == 1.toByte()
            if (isLastChunk) {
                Log.d("BluetoothBleServer", "${device.address} - Last chunk sent")
                initialMessages.remove(device.address)
            }
        }
    }

    private fun processReconciliationMessage(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, message: ByteArray) {
        val address = device.address

        if (address != null) {
            val chunkIndex = message[0].toByte()

            Log.d("BluetoothBle", "$address - Received reconciliation chunk $chunkIndex")
            reconciliationMessages[address] = reconciliationMessages.getOrDefault(address, emptyArray()) + message

            val isLastChunk = message[message.size - 1] == 1.toByte()
            if (isLastChunk) {
                Log.d("BluetoothBle", "$address - Last chunk received")

                var chunks = reconciliationMessages.getOrDefault(address, emptyArray())
                val decompressMessage = joinChunks(chunks)
                Log.d("BluetoothBle", "$address - Received reconciliation message $decompressMessage")
                val resultClient = BluetoothReconciliation().getReconcile(decompressMessage)
                Log.d("BluetoothBle", "$address - Peer needs ${resultClient.needIds.map { it.toHexString() }}")

                reconciliationMessages.remove(address)

                Log.d("BluetoothBle", "$address - Sending to peer")
                bluetoothBle.sendEvent(device, characteristic, "HOLA".toByteArray())
            }
        } else {
            Log.e("BluetoothBle", "$address - Address not found")
        }
    }

    private fun processEvent(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, message: ByteArray) {
        val address = device.address

        if (address != null) {
            val chunkIndex = message[0].toByte()

            Log.d("BluetoothBle", "$address - Received reconciliation chunk $chunkIndex")
            eventMessages[address] = eventMessages.getOrDefault(address, emptyArray()) + message

            val isLastChunk = message[message.size - 1] == 1.toByte()
            if (isLastChunk) {
                Log.d("BluetoothBle", "$address - Last chunk received")

                var chunks = eventMessages.getOrDefault(address, emptyArray())
                val decompressMessage = joinChunks(chunks)
                Log.d("BluetoothBle", "$address - Received Event $decompressMessage")
            }
        } else {
            Log.e("BluetoothBle", "$address - Address not found")
        }
    }

    @SuppressLint("MissingPermission")
    fun addService(service: BluetoothGattService) {
       bluetoothGattServer.addService(service)
    }
}