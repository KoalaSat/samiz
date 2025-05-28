package com.koalasat.samiz

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.koalasat.samiz.databinding.ActivityMainBinding
import com.koalasat.samiz.model.Logger
import com.koalasat.samiz.service.BluetoothStateReceiver

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothStateReceiver: BluetoothStateReceiver

    private val requestCodePermissions = 1001
    private val requiredPermissions =
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    private val requiredPermissionsNot31 =
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )

    @RequiresApi(Build.VERSION_CODES.S)
    private val requiredPermissions31 =
        arrayOf(
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requiredPermissions33 =
        arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.navigation_home,
                    R.id.navigation_logs,
                    R.id.navigation_help,
                ),
            )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        bluetoothStateReceiver = BluetoothStateReceiver()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        enableBluetooth()
        disableBatteryOptimizer()
        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePermissions) {
            val deniedPermissions = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }
            if (deniedPermissions.isNotEmpty()) {
                Logger.e("Samiz", "Permissions not granted : $deniedPermissions")
                Toast.makeText(applicationContext, getString(R.string.permissions_required), Toast.LENGTH_SHORT).show()
            } else {
                Logger.e("Samiz", "Permissions granted")
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Logger.d("Samiz", "Bluethooth enabled")
            } else {
                Toast.makeText(applicationContext, getString(R.string.bluethoot_not_enabled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(applicationContext, getString(R.string.bluethoot_not_supported), Toast.LENGTH_SHORT).show()
        } else if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disableBatteryOptimizer() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.disableBatteryOptimizerTitle))
                .setMessage(getString(R.string.disableBatteryOptimizerMessage))
                .setPositiveButton(getString(R.string.disableBatteryOptimizerAccept)) { _, _ ->
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton(getString(R.string.disableBatteryOptimizerCancel), null)
                .show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        permissionsToRequest.addAll(
            requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            },
        )

        // Add required permissions for Android 14 (API level 33) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.addAll(
                requiredPermissions33.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                },
            )
        }
        // Add required permissions for Android 12 (API level 31) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.addAll(
                requiredPermissions31.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                },
            )
        } else {
            permissionsToRequest.addAll(
                requiredPermissionsNot31.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                },
            )
        }

        if (permissionsToRequest.isNotEmpty()) {
            Logger.e("Samiz", "Permissions not granted: $permissionsToRequest")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                requestCodePermissions,
            )
        } else {
            Logger.d("Samiz", "Permissions already granted: API V${Build.VERSION.SDK_INT}")
        }
    }
}
