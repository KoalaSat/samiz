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
import java.io.Closeable
import kotlin.collections.set


interface BluetoothBleServerCallback {
    fun onReadRequest(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic): ByteArray?
    fun onWriteRequest(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, message: ByteArray)
}

class BluetoothBleServer(private var bluetoothBle: BluetoothBle, private val callback: BluetoothBleServerCallback) : Closeable {
    private lateinit var bluetoothGattServer: BluetoothGattServer
    private var readMessages = HashMap<String, Array<ByteArray>>()
    private var writeMessages = HashMap<String, Array<ByteArray>>()

    @SuppressLint("MissingPermission")
    override fun close() {
        bluetoothGattServer.close()
    }

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
                        if (characteristic.uuid.equals(bluetoothBle.readCharacteristicUUID)) {
                            sendReadMessage(device, characteristic, requestId)
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
                        Log.d("BluetoothBleServer", "${device.address} - Write request : $value")
                        Log.d("BluetoothBleServer", "${device.address} - Response needed $responseNeeded")
                        // Get the value of the characteristic
                        var message = processWriteMessage(device, value)
                        if (message != null) {
                            callback.onWriteRequest(device, characteristic, message)
                            if (responseNeeded) {
                                bluetoothGattServer.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    0,
                                    null
                                )
                            }
                        } else {
                            Log.e("BluetoothBleServer", "${device.address} - Invalid Write request")
                            if (responseNeeded) {
                                bluetoothGattServer.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_FAILURE,
                                    0,
                                    null
                                )
                            }
                        }
                    }
                },
            )
    }

    @SuppressLint("MissingPermission")
    private fun sendReadMessage(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        requestId: Int,
    ) {
        val jsonBytes =
            if (readMessages.containsKey(device.address)) {
                readMessages.getOrDefault(device.address, emptyArray())
            } else {
                var message = callback.onReadRequest(device, characteristic)
                if (message != null) {
                    Log.d("BluetoothBleServer", "${device.address} - Created read response : ${String(message)}")
                    var chunks = splitInChunks(message)
                    Log.d("BluetoothBleServer", "${device.address} - Split into ${chunks.size} chunks")
                    chunks
                } else {
                    emptyArray()
                }
            }

        if (jsonBytes.isNotEmpty()) {
            val nextChunk = jsonBytes.first()
            readMessages[device.address] = jsonBytes.copyOfRange(1, jsonBytes.size)

            val chunkIndex = nextChunk[0].toByte()
            Log.d("BluetoothBleServer", "${device.address} - Sending Chunk $chunkIndex")

            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, nextChunk)

            val isLastChunk = nextChunk[nextChunk.size - 1] == 1.toByte()
            if (isLastChunk) {
                Log.d("BluetoothBleServer", "${device.address} - Last chunk sent")
                readMessages.remove(device.address)
            }
        }
    }

    private fun processWriteMessage(device: BluetoothDevice, message: ByteArray): ByteArray? {
        val address = device.address

        if (address != null) {
            val chunkIndex = message[0].toByte()

            Log.d("BluetoothBle", "$address - Received chunk ${chunkIndex + 1}")
            writeMessages[address] = writeMessages.getOrDefault(address, emptyArray()) + message

            val isLastChunk = message[message.size - 1] == 1.toByte()
            if (isLastChunk) {
                Log.d("BluetoothBle", "$address - Last chunk received")

                var chunks = writeMessages.getOrDefault(address, emptyArray())
                val decompressMessage = joinChunks(chunks)
                Log.d("BluetoothBle", "$address - Received full write message")
                writeMessages.remove(address)
                return decompressMessage
            }
        } else {
            Log.e("BluetoothBle", "$address - Address not found")
        }

        return null
    }

    @SuppressLint("MissingPermission")
    fun addService(service: BluetoothGattService) {
       bluetoothGattServer.addService(service)
    }
}