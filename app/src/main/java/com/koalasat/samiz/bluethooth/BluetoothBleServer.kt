package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.util.Log
import com.koalasat.samiz.util.Compression.Companion.joinChunks
import com.koalasat.samiz.util.Compression.Companion.splitInChunks
import java.io.Closeable
import kotlin.collections.set

interface BluetoothBleServerCallback {
    fun onReadRequest(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
    ): ByteArray?

    fun onWriteRequest(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        message: ByteArray,
    )
}

class BluetoothBleServer(private var bluetoothBle: BluetoothBle, private val callback: BluetoothBleServerCallback) : Closeable {
    private lateinit var bluetoothGattServer: BluetoothGattServer
    private lateinit var deviceReadCharacteristic: BluetoothGattCharacteristic
    private lateinit var deviceWriteCharacteristic: BluetoothGattCharacteristic

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
                        Log.d("BluetoothBleServer", "Service added ${service.uuid}")
                        for (characteristic in service.characteristics) {
                            Log.d(
                                "BluetoothBleServer",
                                "Characteristic: ${characteristic.uuid}, " +
                                    "Properties: ${characteristic.properties}, " +
                                    "Permissions: ${characteristic.permissions}",
                            )
                        }
                    }

                    override fun onCharacteristicReadRequest(
                        device: BluetoothDevice,
                        requestId: Int,
                        offset: Int,
                        characteristic: BluetoothGattCharacteristic,
                    ) {
                        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                        if (characteristic.uuid.equals(bluetoothBle.readCharacteristicUUID)) {
                            sendReadMessage(device, characteristic, requestId)
                        } else {
                            Log.e("BluetoothBleServer", "${device.address} - Read not permitted")
                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                        }
                    }

                    override fun onDescriptorWriteRequest(
                        device: BluetoothDevice?,
                        requestId: Int,
                        descriptor: BluetoothGattDescriptor?,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray?,
                    ) {
                        super.onDescriptorWriteRequest(
                            device,
                            requestId,
                            descriptor,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value,
                        )
                        if (bluetoothBle.descriptorUUID == descriptor?.uuid) {
                            Log.d("BluetoothBleServer", "${device?.address} - Descriptor write success")
                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                        } else {
                            Log.e("BluetoothBleServer", "${device?.address} - Wrong descriptor")
                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
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
                        }
                        if (responseNeeded) {
                            bluetoothGattServer.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                null,
                            )
                        }
                    }
                },
            )

        addCharacteristics()
    }

    @SuppressLint("MissingPermission")
    private fun sendReadMessage(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        requestId: Int,
    ) {
        var jsonBytes = readMessages.getOrDefault(device.address, emptyArray())
        if (jsonBytes.isEmpty()) {
            var message = callback.onReadRequest(device, characteristic)
            jsonBytes =
                if (message != null) {
                    Log.d("BluetoothBleServer", "${device.address} - Created read response : ${String(message)}")
                    var chunks = splitInChunks(message)
                    Log.d("BluetoothBleServer", "${device.address} - Split into ${chunks.size} read chunks")
                    chunks
                } else {
                    emptyArray()
                }
        }

        if (jsonBytes.isNotEmpty()) {
            val nextChunk = jsonBytes.first()
            readMessages[device.address] = jsonBytes.copyOfRange(1, jsonBytes.size)

            val chunkIndex = nextChunk[0].toByte()
            Log.d("BluetoothBleServer", "${device.address} - Sending read Chunk $chunkIndex")

            val isLastChunk = nextChunk[nextChunk.size - 1] == 1.toByte()
            if (isLastChunk) {
                Log.d("BluetoothBleServer", "${device.address} - Last read chunk sent")
                readMessages.remove(device.address)
            }

            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, nextChunk)
        } else {
            Log.e("BluetoothBleServer", "${device.address} - Device not found")
        }
    }

    private fun processWriteMessage(
        device: BluetoothDevice,
        message: ByteArray,
    ): ByteArray? {
        val address = device.address

        if (address != null) {
            val chunkIndex = message[0].toByte()

            Log.d("BluetoothBleServer", "$address - Received write chunk $chunkIndex")
            writeMessages[address] = writeMessages.getOrDefault(address, emptyArray()) + message

            val totalChunks = message[message.size - 1].toInt()
            var chunks = writeMessages.getOrDefault(address, emptyArray())
            if (totalChunks == chunks.size) {
                Log.d("BluetoothBleServer", "$address - Last write chunk received")

                val decompressMessage = joinChunks(chunks)
                Log.d("BluetoothBleServer", "$address - Received full write message")
                writeMessages.remove(address)
                return decompressMessage
            }
        } else {
            Log.e("BluetoothBleServer", "$address - Address not found")
        }

        return null
    }

    @SuppressLint("MissingPermission")
    fun notifyClient(device: BluetoothDevice) {
        deviceReadCharacteristic.value = "HOLI".toByteArray()
        val success = bluetoothGattServer.notifyCharacteristicChanged(device, deviceReadCharacteristic, false)
        if (success) {
            Log.d("BluetoothBleServer", "${device.address} - Notification successfully to send")
        } else {
            Log.e("BluetoothBleServer", "${device.address} - Notification failed to sent")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addCharacteristics() {
        val service = BluetoothGattService(bluetoothBle.serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val readCharacteristic =
            BluetoothGattCharacteristic(
                bluetoothBle.readCharacteristicUUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        val descriptor =
            BluetoothGattDescriptor(
                bluetoothBle.descriptorUUID,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        readCharacteristic.addDescriptor(descriptor)

        val writeCharacteristic =
            BluetoothGattCharacteristic(
                bluetoothBle.writeCharacteristicUUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        writeCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        Log.d("BluetoothBleAdvertiser", "Adding service")
        service.addCharacteristic(readCharacteristic)
        deviceReadCharacteristic = readCharacteristic
        service.addCharacteristic(writeCharacteristic)
        deviceWriteCharacteristic = writeCharacteristic
        val status = bluetoothGattServer.addService(service)
        if (!status) {
            Log.e("BluetoothBleServer", "Failed to add service.")
        }
    }
}
