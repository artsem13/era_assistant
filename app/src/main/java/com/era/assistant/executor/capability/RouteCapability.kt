package com.era.assistant.executor.capability

data class RouteDestination(val latitude: Double, val longitude: Double)
data class RouteOrigin(val latitude: Double, val longitude: Double)
enum class RouteCapabilityState { OPENED, UNAVAILABLE, INVALID_DESTINATION, FAILED }
data class RouteCapabilityResult(val state: RouteCapabilityState, val deeplink: String? = null, val error: String? = null)
interface RouteCapability { fun openRoute(origin: RouteOrigin, destination: RouteDestination): RouteCapabilityResult }
