package com.roadseye.dashcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.roadseye.dashcam.settingssection.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/** App-wide singleton so only one location update stream runs. */
class LocationSpeedManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocationSpeedManager"
        private val SPEED_UNITS_KEY = stringPreferencesKey("speed_units")

        @Volatile
        private var instance: LocationSpeedManager? = null

        fun getInstance(context: Context): LocationSpeedManager {
            return instance ?: synchronized(this) {
                instance ?: LocationSpeedManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private val _latitude = MutableStateFlow("Error")
    val latitude: StateFlow<String> get() = _latitude

    private val _longitude = MutableStateFlow("Error")
    val longitude: StateFlow<String> get() = _longitude

    private val _speed = MutableStateFlow(0.0) // Speed in m/s for CameraManager
    val speed: StateFlow<Double> get() = _speed

    private val _displaySpeed = MutableStateFlow(0.0) // Speed in MPH or KM/H for UI
    val displaySpeed: StateFlow<Double> get() = _displaySpeed

    private val _speedUnit = MutableStateFlow("KM/H")
    val speedUnit: StateFlow<String> get() = _speedUnit

    private var isTracking = false
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null

    init {
        scope.launch {
            context.dataStore.data
                .map { preferences: Preferences -> preferences[SPEED_UNITS_KEY] ?: determineDefaultSpeedUnit() }
                .collectLatest { unit: String ->
                    if (_speedUnit.value != unit) {
                        _speedUnit.value = unit
                        lastLocation?.let { location -> updateLocationAndSpeed(location) }
                    }
                }
        }
        checkLocationPermissionAndStartUpdates()
    }

    private fun checkLocationPermissionAndStartUpdates() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startLocationUpdates(context)
        } else {
            Log.w(TAG, "Location permission not granted, speed updates disabled")
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted, cannot start updates")
            return
        }

        if (!isTracking) {
            isTracking = true
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdateDelayMillis(1000L)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    // Check if location is fresh (within 2 seconds)
                    if (System.currentTimeMillis() - location.time < 2000) {
                        lastLocation = location
                        updateLocationAndSpeed(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, context.mainLooper)
        }
    }

    /** Raw (lat, lon) for callers that need precise values instead of the formatted display strings. */
    fun getCurrentLatLon(): Pair<Double, Double>? =
        lastLocation?.let { it.latitude to it.longitude }

    fun stopLocationUpdates() {
        if (isTracking) {
            isTracking = false
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
            locationCallback = null
        }
    }

    private fun updateLocationAndSpeed(location: Location) {
        _latitude.value = String.format(Locale.US, "%.7f", location.latitude)
        _longitude.value = String.format(Locale.US, "%.7f", location.longitude)

        val speedInMs = location.speed.toDouble().coerceAtLeast(0.0)
        _speed.value = speedInMs

        val unitMultiplier = if (_speedUnit.value == "MPH") 2.23694 else 3.6
        _displaySpeed.value = speedInMs * unitMultiplier

        // Feed lifetime driving stats (distance/top speed while recording)
        DrivingStatsManager.getInstance(context).onLocationUpdate(location)
    }

    private fun determineDefaultSpeedUnit(): String {
        val countryCode = Locale.getDefault().country
        val mphCountries = listOf("US", "GB", "LR", "MM")
        return if (mphCountries.contains(countryCode)) "MPH" else "KM/H"
    }
}
