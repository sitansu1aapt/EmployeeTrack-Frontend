package com.yatri.checkin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SiteAssignment(
    @SerialName("assignment_id") val assignmentId: String? = null,
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("geofence_id") val geofenceId: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("geofence_shape_data") val geofenceShapeData: GeofenceShapeData? = null
)

@Serializable
data class GeofenceShapeData(
    val coordinates: List<LatLngData>? = null
)

@Serializable
data class LatLngData(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)
