package com.yatri.checkin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SiteAssignment(
    @SerialName("assignment_id") val assignmentId: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("site_name") val siteName: String,
    @SerialName("geofence_id") val geofenceId: String,
    @SerialName("address") val address: String,
    @SerialName("geofence_shape_data") val geofenceShapeData: GeofenceShapeData
)

@Serializable
data class GeofenceShapeData(
    val coordinates: List<LatLngData>
)

@Serializable
data class LatLngData(
    val lat: Double,
    val lng: Double
)
