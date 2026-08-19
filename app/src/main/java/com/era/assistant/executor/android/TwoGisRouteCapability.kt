package com.era.assistant.executor.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.era.assistant.executor.capability.RouteCapability
import com.era.assistant.executor.capability.RouteCapabilityResult
import com.era.assistant.executor.capability.RouteCapabilityState
import com.era.assistant.executor.capability.RouteDestination
import com.era.assistant.executor.capability.RouteOrigin
import java.util.Locale

class TwoGisRouteCapability(context: Context) : RouteCapability {
    private val app = context.applicationContext
    override fun openRoute(origin: RouteOrigin, destination: RouteDestination): RouteCapabilityResult {
        if (!valid(origin.latitude, origin.longitude) || !valid(destination.latitude, destination.longitude)) return RouteCapabilityResult(RouteCapabilityState.INVALID_DESTINATION, error = "Route coordinates are invalid")
        val link = String.format(Locale.US, "dgis://2gis.ru/routeSearch/rsType/car/from/%s,%s/to/%s,%s", origin.longitude, origin.latitude, destination.longitude, destination.latitude)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(TWO_GIS_PACKAGE)
        if (app.packageManager.resolveActivity(intent, 0) == null) return RouteCapabilityResult(RouteCapabilityState.UNAVAILABLE, deeplink = link, error = "2GIS is not installed or route intent is unavailable")
        return try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); app.startActivity(intent); RouteCapabilityResult(RouteCapabilityState.OPENED, deeplink = link) }
        catch (error: RuntimeException) { RouteCapabilityResult(RouteCapabilityState.FAILED, deeplink = link, error = error.message ?: "2GIS route launch failed") }
    }
    private fun valid(latitude: Double, longitude: Double) = latitude in -90.0..90.0 && longitude in -180.0..180.0
    companion object { const val TWO_GIS_PACKAGE = "ru.dublgis.dgismobile" }
}
