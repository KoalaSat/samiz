package com.koalasat.samiz.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.koalasat.samiz.Samiz

class BluetoothStateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d("BluetoothState", "Bluetooth is ON")
                    Samiz.getInstance().startService()
                }
                BluetoothAdapter.STATE_OFF -> {
                    Log.d("BluetoothState", "Bluetooth is OFF")
                    Samiz.getInstance().stopService()
                }
            }
        }
    }
}
