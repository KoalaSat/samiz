package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.util.Log
import com.koalasat.samiz.util.Compression.Companion.compressByteArray
import com.koalasat.samiz.util.Compression.Companion.splitInChunks
import java.nio.charset.Charset
import kotlin.collections.set

class BluetoothBleServer(private var bluetoothBle: BluetoothBle) {
    private lateinit var bluetoothGattServer: BluetoothGattServer
    private var outputReadMessages = HashMap<String, Array<ByteArray>>()

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
                        if (characteristic.uuid.equals(bluetoothBle.characteristicUUID)) {
                            val jsonBytes =
                                if (outputReadMessages.containsKey(device.address)) {
                                    outputReadMessages.getOrDefault(device.address, emptyArray())
                                } else {
                                    var message = "{\"id\":\"4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65\",\"pubkey\":\"6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93\",\"created_at\":1673347337,\"kind\":1,\"tags\":[[\"e\",\"3da979448d9ba263864c4d6f14984c423a3838364ec255f03c7904b1ae77f206\"],[\"p\",\"bf2376e17ba4ec269d10fcc996a4746b451152be9031fa48e74553dde5526bce\"]],\"content\":\"Walledgardensbecameprisons,andnostristhefirststeptowardstearingdowntheprisonwalls.\",\"sig\":\"908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262\"}"
                                    var compressedData = compressByteArray(message.toByteArray(Charset.forName("UTF-8")))
                                    var chunks = splitInChunks(compressedData)

                                    Log.d("BluetoothBleServer", "${device.address} - Created ${chunks.size} chunks")
                                    chunks
                                }

                            if (jsonBytes.isNotEmpty()) {
                                Log.d("BluetoothBleServer", "${device.address} - Sending Chunk ${jsonBytes.size}")
                                val nextChunk = jsonBytes.first()
                                outputReadMessages[device.address] = jsonBytes.copyOfRange(1, jsonBytes.size)

                                sendData(device, requestId, characteristic, nextChunk)

                                val isLastChunk = nextChunk[nextChunk.size - 1] == 1.toByte()
                                if (isLastChunk) {
                                    Log.d("BluetoothBleServer", "${device.address} - Last chunk sent")
                                    outputReadMessages.remove(device.address)
                                }
                            }
                        } else {
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
                        // Get the value of the characteristic
                        val message = String(value)

                        Log.d(
                            "BluetoothBleServer",
                            "${device.address} - Write request from ${device.name} : $message",
                        )
                    }
                },
            )
    }

    @SuppressLint("MissingPermission")
    private fun sendData(
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

    fun clearOutputData(address: String) {
        outputReadMessages.remove(address)
    }

    @SuppressLint("MissingPermission")
    fun addService(service: BluetoothGattService) {
       bluetoothGattServer.addService(service)
    }
}