package com.serwylo.beatgame.levels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.Since
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import ktx.async.newSingleThreadAsyncContext
import java.io.File

private const val TAG = "levelsNet"

private const val WORLDS_JSON_URL = "https://beat-feet.github.io/beat-feet-levels/worlds.json"

private const val JSON_VERSION = 1

private val ID_REGEX = Regex("[\\w.-]+")

private val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

private val gson = Gson()

suspend fun loadAllWorlds(forceUncached: Boolean = false): List<World> {
    val remoteWorlds = fetchWorldsList(forceUncached).getWorlds().map { worldSummaryDto ->
        val worldDto = fetchWorld(worldSummaryDto, forceUncached)
        RemoteWorld(worldSummaryDto, worldDto)
    }

    return listOf(TheOriginalWorld) + remoteWorlds
}

private suspend fun fetchWorldsList(forceUncached: Boolean = false): WorldsDTO {
    Gdx.app.log(TAG, "Fetching list of worlds from $WORLDS_JSON_URL")
    val string = downloadAndCacheString(WORLDS_JSON_URL, Gdx.files.local(".cache/worlds/worlds.json"), forceUncached)
    return gson.fromJson(string, WorldsDTO::class.java)
}

private suspend fun fetchWorld(summary: WorldsDTO.WorldSummaryDTO, forceUncached: Boolean = false): WorldDTO {
    Gdx.app.log(TAG, "Fetching list of levels for world \"${summary.id}\" at ${summary.url}")
    val string = downloadAndCacheString(summary.url, Gdx.files.local(".cache/worlds/${summary.id}/world.json"), forceUncached)
    return gson.fromJson(string, WorldDTO::class.java)
}

private suspend fun downloadAndCacheString(url: String, cachedFile: FileHandle, forceUncached: Boolean = false): String {
    if (cachedFile.exists() && !forceUncached) {
        Gdx.app.log(TAG, "Reading cached string from $url (from cache file ${cachedFile.file().absolutePath})")
        return cachedFile.readString()
    }

    cachedFile.parent().mkdirs()

    Gdx.app.log(TAG, "Downloading string from $url (and caching to ${cachedFile.file().absolutePath})")
    val string: String = httpClient.get(url)
    cachedFile.writeString(string, false)

    return string
}

suspend fun downloadAndCacheFile(url: String, cachedFile: FileHandle): FileHandle {
    if (cachedFile.exists()) {
        Gdx.app.debug(TAG, "Reading cached data file from $url (from cache file ${cachedFile.file().absolutePath})")
        return cachedFile
    }

    cachedFile.parent().mkdirs()

    Gdx.app.log(TAG, "Downloading data file from $url (and caching to ${cachedFile.file().absolutePath})")
    val response: HttpResponse = httpClient.request(url)
    response.content.copyAndClose(cachedFile.file().writeChannel(newSingleThreadAsyncContext("downloadAndCacheFile")))

    return cachedFile
}

fun getCachedLevelDataFile(level: RemoteLevel): FileHandle {
    return Gdx.files.local(".cache/worlds/${level.getWorld().getId()}/${level.getId()}.json")
}

fun getCachedMp3File(level: RemoteLevel): FileHandle {
    return Gdx.files.local(".cache/worlds/${level.getWorld().getId()}/${level.getId()}.mp3")
}

private suspend fun fetchLevelMp3(url: String, output: File) {
    Gdx.app.log(TAG, "Fetching level mp3 $url and saving to $output")
}

private suspend fun fetchLevelData(url: String, output: File) {
    Gdx.app.log(TAG, "Fetching level data $url and saving to $output")
}

data class WorldDTO(

    @SerializedName("levels")
    @Since(1.0)
    private val levels: List<LevelDTO>,

    @SerializedName("attribution")
    @Since(1.0)
    val attribution: AttributionDTO?,

) {

    fun getLevels() = levels.filter { world ->
        if (world.id.matches(ID_REGEX)) {
            true
        } else {
            Gdx.app.log(TAG, "Ignoring level with id: \"${world.id}\" because it does not match the regex: \"${ID_REGEX.pattern}\". This id is used to create files on disk, so we are conservative in what we accept here for security reasons.")
            false
        }
    }

    data class LevelDTO(

        @SerializedName("id")
        @Since(1.0)
        val id: String,

        @SerializedName("label")
        @Since(1.0)
        val label: String,

        @SerializedName("mp3Url")
        @Since(1.0)
        val mp3Url: String,

        @SerializedName("dataUrl")
        @Since(1.0)
        val dataUrl: String,

        @SerializedName("unlockRequirements")
        @Since(1.0)
        val unlockRequirements: UnlockRequirementsDTO,

        @SerializedName("attribution")
        @Since(1.0)
        val attribution: AttributionDTO?,

    )
}

data class UnlockRequirementsDTO(

    @SerializedName("type")
    @Since(1.0)
    val type: String,

    @SerializedName("numRequired")
    @Since(1.0)
    val numRequired: Int? = null

)

data class AttributionDTO(

    @SerializedName("license")
    @Since(1.0)
    val licenseName: String,

    @SerializedName("sourceUrl")
    @Since(1.0)
    val sourceUrl: String,

    @SerializedName("author")
    @Since(1.0)
    val author: String?,

)

data class WorldsDTO(

    @SerializedName("worlds")
    @Since(1.0)
    private val worlds: List<WorldSummaryDTO>

) {

    fun getWorlds() = worlds.filter { world ->
        if (world.id.matches(ID_REGEX)) {
            true
        } else {
            Gdx.app.log(TAG, "Ignoring world with id: \"${world.id}\" because it does not match the regex: \"${ID_REGEX.pattern}\". This id is used to create files on disk, so we are conservative in what we accept here for security reasons.")
            false
        }
    }

    data class WorldSummaryDTO(

        @SerializedName("id")
        @Since(1.0)
        val id: String,

        @SerializedName("name")
        @Since(1.0)
        val name: String,

        @SerializedName("url")
        @Since(1.0)
        val url: String,

        @SerializedName("unlockRequirements")
        @Since(1.0)
        val unlockRequirements: UnlockRequirementsDTO,

    )
}
