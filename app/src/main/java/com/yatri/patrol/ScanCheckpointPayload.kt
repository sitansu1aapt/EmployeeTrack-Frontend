package com.yatri.patrol

import kotlinx.serialization.Serializable

@Serializable
data class ScanCheckpointPayload(
    val qrCodeData: String,
    val latitudeAtScan: Double,
    val longitudeAtScan: Double,
    val notes: String
)
