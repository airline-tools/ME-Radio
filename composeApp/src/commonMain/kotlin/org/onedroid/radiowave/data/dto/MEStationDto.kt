package org.onedroid.radiowave.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MEStationDto(
    val id: String,
    val name: String,
    val frequency: String,
    val category: String,
    val stream: String? = null,
    val masperoName: String? = null
)

@Serializable
data class MasperoStreamDto(
    val name: String,
    val frequency: String? = null,
    val url: String? = null
)
