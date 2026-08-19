package com.era.assistant.executor.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.era.assistant.executor.capability.CurrentLocation
import com.era.assistant.executor.capability.LocationCapability
import com.era.assistant.executor.capability.LocationCapabilityResult
import com.era.assistant.executor.capability.LocationCapabilityState

class AndroidLocationCapability(context: Context) : LocationCapability {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())

    override fun getCurrentLocation(onResult: (LocationCapabilityResult) -> Unit) {
        if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onResult(LocationCapabilityResult(LocationCapabilityState.PERMISSION_REQUIRED, error = "ACCESS_COARSE_LOCATION permission is required")); return
        }
        val provider = try { manager.getBestProvider(Criteria().apply { accuracy = Criteria.ACCURACY_COARSE }, true) } catch (_: Exception) { null }
        if (provider == null) { onResult(LocationCapabilityResult(LocationCapabilityState.UNAVAILABLE, error = "No enabled location provider")); return }
        lateinit var timeout: Runnable
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handler.removeCallbacks(timeout); manager.removeUpdates(this)
                if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) onResult(LocationCapabilityResult(LocationCapabilityState.FAILED, error = "Location coordinates are invalid"))
                else onResult(LocationCapabilityResult(LocationCapabilityState.COMPLETED, CurrentLocation(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null, location.provider, location.time)))
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        timeout = Runnable { manager.removeUpdates(listener); onResult(LocationCapabilityResult(LocationCapabilityState.UNAVAILABLE, error = "Location fix timed out")) }
        try { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()); handler.postDelayed(timeout, 10_000L) }
        catch (_: SecurityException) { onResult(LocationCapabilityResult(LocationCapabilityState.PERMISSION_REQUIRED, error = "Location permission is required")) }
        catch (error: RuntimeException) { onResult(LocationCapabilityResult(LocationCapabilityState.FAILED, error = error.message ?: "Location request failed")) }
    }
}
