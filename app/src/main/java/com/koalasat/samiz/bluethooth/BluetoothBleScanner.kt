package com.koalasat.samiz.bluethooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.UUID

interface BluetoothBleScannerCallback {
    fun onDeviceFound(
        device: BluetoothDevice,
        remoteUuid: UUID,
    )
}

class BluetoothBleScanner(private var bluetoothBle: BluetoothBle, private val callback: BluetoothBleScannerCallback) : Closeable {
    private val discovered = mutableSetOf<String>()

    @SuppressLint("MissingPermission")
    override fun close() {
        bluetoothBle.bluetoothManager.adapter.bluetoothLeScanner.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val scanFilter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(bluetoothBle.serviceUUID))
                .build()

        val scanSettings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        bluetoothBle.bluetoothManager.adapter.bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                super.onScanResult(callbackType, result)
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(bluetoothBle.serviceUUID))

                if (serviceData != null) {
                    val buffer = ByteBuffer.wrap(serviceData)
                    val remoteUuid = UUID(buffer.long, buffer.long)
                    val key = result.device.address + remoteUuid
                    if (key in discovered) return

                    Log.d("BluetoothBleScanner", "${result.device.address} - Service data found")
                    discovered += key

                    callback.onDeviceFound(result.device, remoteUuid)
                } else {
                    Log.d("BluetoothBleScanner", "${result.device.address} - Service data not found")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BluetoothBleScanner", "Scan failed with error code: $errorCode")
            }
        }
}
