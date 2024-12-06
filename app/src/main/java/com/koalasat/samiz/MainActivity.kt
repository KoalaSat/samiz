package com.koalasat.samiz

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.koalasat.samiz.databinding.ActivityMainBinding
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.disposables.Disposable

class MainActivity : AppCompatActivity() {
    private val requestPermissionCode = 1001
    private lateinit var binding: ActivityMainBinding
    private lateinit var rxBleClient: RxBleClient
    private lateinit var scanSubscription: Disposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        rxBleClient = RxBleClient.create(this)

        // Start scanning for BLE devices
        checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dispose of the subscription if needed
        scanSubscription.dispose()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestPermissionCode)
        } else {
            startScanning()
        }
    }

    private fun startScanning() {
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val scanFilters = ScanFilter.Builder().build()

        scanSubscription = rxBleClient.scanBleDevices(scanSettings, scanFilters)
            .subscribe(
                { scanResult ->
                    // Handle the scan result
                    Toast.makeText(this, "Found device: ${scanResult.bleDevice.name} - ${scanResult.bleDevice.macAddress}", Toast.LENGTH_SHORT).show()
                    Log.d("BLE", "Found device: ${scanResult.bleDevice.name} - ${scanResult.bleDevice.macAddress}")
                },
                { throwable ->
                    // Handle errors
                    Toast.makeText(this, "Scan failed: ${throwable.message}", Toast.LENGTH_SHORT).show()
                    Log.e("BLE", "Scan failed: ${throwable.message}")
                }
            )
    }
}