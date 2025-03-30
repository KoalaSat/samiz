package com.koalasat.samiz

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.samiz.service.SynchronizationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.also
import kotlin.jvm.java

class Samiz : Application() {
    private val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this

        updateIsEnabled(isForegroundServiceEnabled(this))
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
    }

    fun startService() {
        Log.d("Samiz", "Starting service...")
        val serviceIntent = Intent(this, SynchronizationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        saveForegroundServicePreference(this@Samiz, true)
    }

    fun stopService() {
        val intent = Intent(applicationContext, SynchronizationService::class.java)
        applicationContext.stopService(intent)
        saveForegroundServicePreference(this, false)
    }

    fun contentResolverFn(): ContentResolver = contentResolver

    companion object {
        private val _isEnabled = MutableLiveData(false)
        val isEnabled: LiveData<Boolean> get() = _isEnabled

        private val _receivedEvents = MutableLiveData(0)
        val receivedEvents: LiveData<Int> get() = _receivedEvents
        private val _sentEvents = MutableLiveData(0)
        val sentEvents: LiveData<Int> get() = _sentEvents
        private val _foundDevices = MutableLiveData<MutableSet<String>>(mutableSetOf())
        val foundDevices: LiveData<MutableSet<String>> get() = _foundDevices

        @Volatile
        private var instance: Samiz? = null

        fun getInstance(): Samiz =
            instance ?: synchronized(this) {
                instance ?: Samiz().also { instance = it }
            }

        fun updateIsEnabled(value: Boolean) {
            _isEnabled.postValue(value)
        }

        fun updateReceivedEvents(value: Int) {
            _receivedEvents.postValue(value)
        }

        fun updateSentEvents(value: Int) {
            _sentEvents.postValue(value)
        }

        fun updateFoundDevices(value: String) {
            var set = _foundDevices.value
            if (set != null) {
                set.add(value)
                _foundDevices.postValue(set)
            }
        }

        fun isForegroundServiceEnabled(context: Context): Boolean {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("SamizPreferences", MODE_PRIVATE)
            return sharedPreferences.getBoolean("foreground_service_enabled", false)
        }

        private fun saveForegroundServicePreference(
            context: Context,
            value: Boolean,
        ) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("SamizPreferences", MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("foreground_service_enabled", value)
            editor.apply()
            updateIsEnabled(value)
        }
    }
}
