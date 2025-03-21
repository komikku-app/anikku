package eu.kanade.tachiyomi.data.track.shikimori.dto

import kotlinx.serialization.Serializable

@Serializable
data class SMMetadata(
    val data: SMMetadataData,
)

@Serializable
data class SMMetadataData(
    val animes: List<SMMetadataResult>,
)

@Serializable
data class SMMetadataResult(
    val id: String,
    val name: String,
    val description: String,
    val poster: SMAnimePoster,
    val studios: List<SMAnimeStudio>,
    val personRoles: List<SMAnimePersonRoles>,
)

@Serializable
data class SMAnimePoster(
    val originalUrl: String,
)

@Serializable
data class SMAnimePersonRoles(
    val person: SMPerson,
    val rolesEn: List<String>,
)

@Serializable
data class SMPerson(
    val name: String,
)

@Serializable
data class SMAnimeStudio(
    val name: String,
)
