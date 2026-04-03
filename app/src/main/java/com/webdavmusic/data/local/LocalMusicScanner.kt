package com.webdavmusic.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.webdavmusic.data.model.AudioFormat
import com.webdavmusic.data.model.Song
import com.webdavmusic.data.model.SongSource
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicScanner @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    /** Scan device storage. If folderPaths is non-empty, only include files from those folders. */
    fun scan(folderPaths: List<String> = emptyList(), onProgress: (Int) -> Unit = {}): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC}!=0 AND ${MediaStore.Audio.Media.DURATION}>10000"

        try {
            ctx.contentResolver.query(collection, projection, sel, null,
                "${MediaStore.Audio.Media.ARTIST} ASC")?.use { cur ->
                val idCol   = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleC  = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistC = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumC  = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val aaC     = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val trackC  = cur.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val durC    = cur.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeC   = cur.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeC   = cur.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dataC   = cur.getColumnIndex(MediaStore.Audio.Media.DATA)
                val addedC  = cur.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val modC    = cur.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cur.moveToNext()) {
                    val mediaId  = cur.getLong(idCol)
                    val filePath = if (dataC >= 0) cur.getString(dataC) ?: "" else ""

                    // Folder filter
                    if (folderPaths.isNotEmpty() && !folderPaths.any { filePath.startsWith(it) }) continue

                    val title   = cur.getString(titleC) ?: "Unknown"
                    val artist  = cur.getString(artistC)?.takeIf { it != "<unknown>" } ?: "未知艺术家"
                    val album   = cur.getString(albumC) ?: "未知专辑"
                    val aa      = if (aaC >= 0) cur.getString(aaC) ?: "" else ""
                    val track   = if (trackC >= 0) cur.getInt(trackC) % 1000 else 0
                    val dur     = if (durC >= 0) cur.getLong(durC) else 0L
                    val size    = if (sizeC >= 0) cur.getLong(sizeC) else 0L
                    val mime    = if (mimeC >= 0) cur.getString(mimeC) ?: "" else ""
                    val added   = if (addedC >= 0) cur.getLong(addedC) * 1000L else 0L
                    val mod     = if (modC >= 0) cur.getLong(modC) * 1000L else 0L

                    val uri = ContentUris.withAppendedId(collection, mediaId).toString()
                    val ext = filePath.substringAfterLast('.', "")
                    // Album art via MediaStore
                    val art = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), mediaId
                    ).toString()

                    songs += Song(
                        id = "local:$mediaId", accountId = -1L, source = SongSource.LOCAL,
                        path = filePath, url = uri, title = title, artist = artist,
                        album = album, albumArtist = aa, trackNumber = track,
                        duration = dur, fileSize = size, mimeType = mime,
                        format = AudioFormat.fromExtension(ext) ?: AudioFormat.MP3,
                        dateAdded = added, lastModified = mod, artworkPath = art
                    )
                    if (songs.size % 50 == 0) onProgress(songs.size)
                }
            }
        } catch (e: Exception) { Timber.e(e, "Local scan error") }
        return songs
    }

    /** Get top-level music directories for the folder picker */
    fun getTopLevelFolders(): List<String> {
        val dirs = mutableSetOf<String>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        runCatching {
            ctx.contentResolver.query(collection,
                arrayOf(MediaStore.Audio.Media.DATA),
                "${MediaStore.Audio.Media.IS_MUSIC}!=0", null, null)?.use { cur ->
                val dataCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cur.moveToNext()) {
                    val path = cur.getString(dataCol) ?: continue
                    dirs += File(path).parent ?: continue
                }
            }
        }
        return dirs.sorted()
    }
}
