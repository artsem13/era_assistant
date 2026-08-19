package com.era.assistant.executor.capability

data class CurrentLocation(
    val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null,
    val provider: String? = null, val timestampMs: Long, val altitudeMeters: Double? = null,
    val bearingDegrees: Float? = null, val speedMetersPerSecond: Float? = null, val elapsedMs: Long? = null
)
enum class LocationCapabilityState { COMPLETED, PERMISSION_REQUIRED, UNAVAILABLE, FAILED }
data class LocationCapabilityResult(val state: LocationCapabilityState, val location: CurrentLocation? = null, val error: String? = null)
interface LocationCapability { fun getCurrentLocation(onResult: (LocationCapabilityResult) -> Unit) }
