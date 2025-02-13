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

interface BluetoothBleScannerCallback {
    fun onDeviceFound(device: BluetoothDevice)
}

class BluetoothBleScanner(private var bluetoothBle: BluetoothBle, private val callback: BluetoothBleScannerCallback) : Closeable {

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
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
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
                val device = result.device
                callback.onDeviceFound(device)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BluetoothBleScanner", "Scan failed with error code: $errorCode")
            }
        }
}