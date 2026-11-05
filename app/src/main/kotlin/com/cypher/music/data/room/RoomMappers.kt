package com.cypher.music.data.room

import com.cypher.music.data.Playlist
import com.cypher.music.data.RecentlyPlayedItem
import com.cypher.music.data.RecentlyPlayedType
import com.cypher.music.data.TopArtistStat
import com.cypher.music.data.TopTrackStat
import com.cypher.music.model.AudioSourceType
import com.cypher.music.model.Track

fun TrackEntity.toDomain(): Track {
    val src = runCatching { AudioSourceType.valueOf(sourceType) }.getOrDefault(AudioSourceType.LOCAL)
    return Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        sourceType = src,
        path = path,
        isLiked = isLiked,
        isAvailable = true,
        isDownloaded = isDownloaded
    )
}

fun Track.toEntity(isDownloaded: Boolean = this.isDownloaded, downloadedUri: String? = null): TrackEntity {
    return TrackEntity(
        id,
        title,
        artist,
        album,
        durationMs,
        artworkUrl,
        streamUrl,
        sourceType.name,
        path,
        isLiked,
        isDownloaded,
        downloadedUri,
        System.currentTimeMillis()
    )
}

fun PlaylistEntity.toDomain(tracks: List<Track> = emptyList()): Playlist {
    return Playlist(
        id = id,
        name = name,
        description = description,
        coverUri = coverUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPrivate = isPrivate,
        tracks = tracks
    )
}

fun Playlist.toEntity(): PlaylistEntity {
    return PlaylistEntity(
        id,
        name,
        description,
        coverUri,
        createdAt,
        updatedAt,
        isPrivate
    )
}

/**
 * Converts a Room @Relation PlaylistWithTracks directly to domain Playlist.
 */
fun PlaylistWithTracks.toDomain(): Playlist {
    val domainTracks = tracks.map { it.toDomain() }
    return playlist.toDomain(domainTracks)
}

fun RecentlyPlayedEntity.toDomain(): RecentlyPlayedItem {
    val t = runCatching { RecentlyPlayedType.valueOf(itemType) }.getOrDefault(RecentlyPlayedType.TRACK)
    val src = runCatching { AudioSourceType.valueOf(sourceType) }.getOrDefault(AudioSourceType.LOCAL)
    return RecentlyPlayedItem(
        id = id,
        type = t,
        title = title,
        subtitle = subtitle,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        sourceType = src,
        extraId = extraId,
        timestamp = timestamp
    )
}

fun RecentlyPlayedItem.toEntity(): RecentlyPlayedEntity {
    return RecentlyPlayedEntity(
        id,
        type.name,
        title,
        subtitle,
        artworkUrl,
        streamUrl,
        sourceType.name,
        extraId,
        timestamp
    )
}

fun PlaybackAnalyticsDao.ArtistStatTuple.toStat(): TopArtistStat {
    return TopArtistStat(
        artist = artist ?: "Desconocido",
        totalTimeMs = totalTimeMs,
        playCount = playCount
    )
}

fun PlaybackAnalyticsDao.TrackStatTuple.toStat(): TopTrackStat {
    return TopTrackStat(
        trackId = trackId ?: "",
        title = title ?: "",
        artist = artist ?: "",
        artworkUrl = artworkUrl,
        totalTimeMs = totalTimeMs,
        playCount = playCount
    )
}
