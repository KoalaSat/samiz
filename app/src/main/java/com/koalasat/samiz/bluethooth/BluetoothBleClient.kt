package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.koalasat.samiz.util.Compression.Companion.joinChunks
import com.koalasat.samiz.util.Compression.Companion.splitInChunks
import java.io.Closeable
import kotlin.collections.set

interface BluetoothBleClientCallback {
    fun onDisconnection(device: BluetoothDevice)

    fun onCharacteristicDiscovered(
        device: BluetoothDevice,
        characteristics: BluetoothGattCharacteristic,
    )

    fun onReadResponse(
        device: BluetoothDevice,
        message: ByteArray,
    )

    fun onWriteSuccess(device: BluetoothDevice)
}

class BluetoothBleClient(private var bluetoothBle: BluetoothBle, private val callback: BluetoothBleClientCallback) : Closeable {
    private var deviceGatt = HashMap<String, BluetoothGatt>()
    private var readMessages = HashMap<String, Array<ByteArray>>()
    private var writeMessages = HashMap<String, Array<ByteArray>>()

    @SuppressLint("MissingPermission")
    override fun close() {
        deviceGatt.values.forEach { it.close() }
    }

    val bluetoothGattCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                super.onConnectionStateChange(gatt, status, newState)
                val address = gatt.device?.address
                Log.d("BluetoothBleClient", "$address - Connection state changed: status=$status newState=$newState")
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        gatt.requestMtu(bluetoothBle.mtuSize)
                        Log.d("BluetoothBleClient", "$address - Setting MTU")
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.d("BluetoothBleClient", "$address - Disconnected from GATT server")
                        deviceGatt.remove(address)
                        gatt.close()
                        callback.onDisconnection(gatt.device)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onMtuChanged(
                gatt: BluetoothGatt?,
                mtu: Int,
                status: Int,
            ) {
                super.onMtuChanged(gatt, mtu, status)
                val address = gatt?.device?.address
                Log.d("BluetoothBleClient", "$address - MTU changed : $mtu")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (address != null) deviceGatt[address] = gatt
                    if (gatt != null) {
                        gatt.discoverServices()
                        Log.d("BluetoothBleClient", "$address - Discovering services")
                    }
                } else {
                    Log.d("BluetoothBleClient", "$address - Setting MTU failed")
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                super.onServicesDiscovered(gatt, status)
                val address = gatt.device?.address
                Log.d("BluetoothBleClient", "$address - Service discovered")
                Log.d("BluetoothBleClient", "$address - GATT Status: $status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(bluetoothBle.serviceUUID)
                    if (service == null || address == null) {
                        Log.d("BluetoothBleClient", "$address - Service not existing")
                    } else {
                        Log.d("BluetoothBleClient", "$address - Discovered Service: ${service.uuid}")
                        val readCharacteristic = service.getCharacteristic(bluetoothBle.readCharacteristicUUID)
                        if (readCharacteristic != null) {
                            Log.d("BluetoothBleClient", "$address - Read characteristic")
                            deviceGatt[address] = gatt
                            callback.onCharacteristicDiscovered(gatt.device, readCharacteristic)
                        }
                        val writeCharacteristic = service.getCharacteristic(bluetoothBle.writeCharacteristicUUID)
                        if (writeCharacteristic != null) {
                            Log.d("BluetoothBleClient", "$address - Read characteristic")
                            deviceGatt[address] = gatt
                            callback.onCharacteristicDiscovered(gatt.device, writeCharacteristic)
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                // Handle the response message
                Log.d("BluetoothBleClient", "Characteristic changed")
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)

                val device = gatt?.device
                val address = device?.address
                if (status == BluetoothGatt.GATT_SUCCESS && address != null && characteristic != null) {
                    deviceGatt[address] = gatt
                    Log.d("BluetoothBleClient", "$address - Write successful")
                    if (!sendNextWriteChunk(device, characteristic)) {
                        callback.onWriteSuccess(device)
                    }
                } else {
                    Log.e("BluetoothBleClient", "$address - Write failed with status $status")
                }
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int,
            ) {
                val device = gatt?.device
                val address = device?.address
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null && address != null) {
                    deviceGatt[address] = gatt
                    Log.d("BluetoothBleClient", "$address - Read response")
                    var jsonBytes = characteristic.getValue()
                    if (jsonBytes != null) {
                        var message = processReadMessage(gatt, characteristic, jsonBytes)
                        if (message != null) {
                            Log.d("BluetoothBleClient", "$address - Read message received : $message")
                            callback.onReadResponse(device, message)
                        } else {
                            Log.e("BluetoothBleClient", "$address - Invalid Read")
                        }
                    }
                } else {
                    Log.e("BluetoothBleClient", "$address - Read failed with status $status")
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun processReadMessage(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        jsonBytes: ByteArray,
    ): ByteArray? {
        val address = gatt?.device?.address

        if (characteristic != null && address != null) {
            val chunkIndex = jsonBytes[0].toByte()

            Log.d("BluetoothBleClient", "$address - Received read chunk $chunkIndex")
            readMessages[address] = readMessages.getOrDefault(address, emptyArray()) + jsonBytes

            val totalChunks = jsonBytes[jsonBytes.size - 1].toInt()
            var chunks = readMessages.getOrDefault(address, emptyArray())
            if (totalChunks == chunks.size) {
                Log.d("BluetoothBleClient", "$address - Last read chunk received : total chunks  $totalChunks")
                val decompressMessage = joinChunks(chunks)
                readMessages.remove(address)
                return decompressMessage
            } else {
                gatt.readCharacteristic(characteristic)
            }
        }

        return null
    }

    @SuppressLint("MissingPermission")
    fun sendWriteMessage(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        message: ByteArray,
    ) {
        Log.d("BluetoothBleClient", "${device.address} - Sending write message")
        val gatt = deviceGatt[device.address]
        if (gatt != null) {
            var chunks = splitInChunks(message)
            writeMessages[device.address] = chunks
            Log.d("BluetoothBleClient", "${device.address} - Split into ${chunks.size} write chunks")

            sendNextWriteChunk(device, characteristic)
        } else {
            Log.d("BluetoothBleClient", "${device.address} - Gatt not found")
        }
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val gatt = deviceGatt[device.address]
        if (gatt != null) {
            Log.d("BluetoothBleClient", "${device.address} - Read sent")
            gatt.readCharacteristic(characteristic)
        } else {
            Log.d("BluetoothBleClient", "${device.address} - Gatt not found")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNextWriteChunk(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val gatt = deviceGatt[device.address]
        if (gatt != null) {
            var chunks = writeMessages.getOrDefault(device.address, emptyArray())
            if (chunks.isNotEmpty()) {
                characteristic.value = chunks.first()
                val status = gatt.writeCharacteristic(characteristic)
                if (status) {
                    writeMessages[device.address] = chunks.copyOfRange(1, chunks.size)
                    Log.d("BluetoothBleClient", "${device.address} - Sent write chunk ${chunks.size} - $status")
                    return true
                } else {
                    Log.d("BluetoothBleClient", "${device.address} - Error sending write chunk ${chunks.size}")
                }
            } else {
                Log.d("BluetoothBleClient", "${device.address} - No more write chunks to send")
            }
        }

        return false
    }
}
