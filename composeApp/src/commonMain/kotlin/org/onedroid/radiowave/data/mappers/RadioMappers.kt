package org.onedroid.radiowave.data.mappers

import kotlinx.datetime.Clock
import org.onedroid.radiowave.data.database.RadioEntity
import org.onedroid.radiowave.data.dto.RadioSearchResponseDto
import org.onedroid.radiowave.data.dto.MEStationDto
import org.onedroid.radiowave.domain.Radio

fun MEStationDto.toRadio(): Radio {
    return Radio(
        id = id,
        name = name,
        url = stream.orEmpty(),
        urlResolved = stream,
        homepage = null,
        imgUrl = null,
        tags = listOf(category),
        country = "مصر",
        state = frequency,
        iso = "EG",
        language = listOf("Arabic"),
        codec = null,
        bitrate = null,
        hls = if (stream?.contains(".m3u8", ignoreCase = true) == true) 1 else 0,
        voteCount = 0,
        clickCount = 0,
        sslError = 0,
        geoLat = null,
        geoLong = null,
        hasExtendedInfo = false,
        frequency = frequency,
        category = category
    )
}

fun RadioSearchResponseDto.toRadio(): Radio {
    return Radio(
        id = id,
        name = name,
        url = url,
        urlResolved = urlResolved,
        homepage = homepage,
        imgUrl = imgUrl,
        tags = tags?.split(",")?.map { it.trim() },
        country = country,
        state = state,
        iso = iso,
        language = language?.split(",")?.map { it.trim() },
        codec = codec,
        bitrate = bitrate,
        hls = hls,
        voteCount = voteCount,
        clickCount = clickCount,
        sslError = sslError,
        geoLat = geoLat,
        geoLong = geoLong,
        hasExtendedInfo = hasExtendedInfo,
    )
}

fun RadioEntity.toRadio(): Radio {
    val storedTags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return Radio(
        id = stationuuid,
        name = name,
        url = url,
        urlResolved = urlResolved,
        homepage = homepage,
        imgUrl = favicon,
        tags = storedTags,
        country = country,
        state = state,
        iso = countrycode,
        language = language.split(",").map { it.trim() },
        codec = "",
        bitrate = bitrate,
        hls = hls,
        voteCount = clickcount,
        clickCount = clicktrend,
        sslError = 0,
        geoLat = geoLat,
        geoLong = geoLong,
        hasExtendedInfo = hasExtendedInfo,
        frequency = state.takeIf { it.isNotBlank() && it != "None" },
        category = storedTags.firstOrNull()
    )
}

fun Radio.toRadioEntity(
    isSaved: Boolean = false
): RadioEntity {
    return RadioEntity(
        stationuuid = id,
        bitrate = bitrate ?: 0,
        clickcount = voteCount ?: 0,
        clicktrend = clickCount ?: 0,
        country = country ?: "None",
        countrycode = iso ?: "None",
        favicon = imgUrl ?: "None",
        geoLat = geoLat,
        geoLong = geoLong,
        hasExtendedInfo = hasExtendedInfo ?: false,
        hls = hls ?: 0,
        homepage = homepage ?: "None",
        language = language?.joinToString(", ") ?: "",
        name = name,
        serveruuid = "None",
        state = frequency ?: state ?: "None",
        tags = category ?: tags?.joinToString(", ") ?: "",
        url = url,
        urlResolved = urlResolved ?: "None",
        isSaved = isSaved,
        timeStamp = Clock.System.now().toEpochMilliseconds()
    )
}
