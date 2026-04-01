package com.webdavmusic.data

import androidx.room.*
import com.webdavmusic.data.model.*
import kotlinx.coroutines.flow.Flow

// ─── Account DAO ───────────────────────────────────────────────────────────────

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<WebDavAccount>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): WebDavAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: WebDavAccount): Long

    @Update
    suspend fun updateAccount(account: WebDavAccount)

    @Delete
    suspend fun deleteAccount(account: WebDavAccount)

    @Query("UPDATE accounts SET lastScanned = :time WHERE id = :id")
    suspend fun updateLastScanned(id: Long, time: Long)
}

// ─── Song DAO ─────────────────────────────────────────────────────────────────

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY artist ASC, album ASC, trackNumber ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE accountId = :accountId ORDER BY artist, album, trackNumber")
    fun getSongsByAccount(accountId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC")
    fun getSongsByAlbum(album: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, trackNumber")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT DISTINCT artist FROM songs WHERE accountId = :accountId OR :accountId = -1 ORDER BY artist ASC")
    fun getArtists(accountId: Long = -1): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM songs WHERE accountId = :accountId OR :accountId = -1 ORDER BY album ASC")
    fun getAlbums(accountId: Long = -1): Flow<List<String>>

    @Query("""
        SELECT * FROM songs WHERE 
        title LIKE '%' || :query || '%' OR
        artist LIKE '%' || :query || '%' OR
        album LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun search(query: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("DELETE FROM songs WHERE accountId = :accountId")
    suspend fun deleteSongsByAccount(accountId: Long)

    @Query("SELECT COUNT(*) FROM songs WHERE accountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM songs")
    fun getTotalCount(): Flow<Int>
}

// ─── Playlist DAO ─────────────────────────────────────────────────────────────

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(item: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """)
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [WebDavAccount::class, Song::class, Playlist::class, PlaylistSong::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "webdav_music.db"
    }
}
