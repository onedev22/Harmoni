package com.amurayada.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.amurayada.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricsRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("lyrics_cache", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "LyricsRepository"
        private const val LRCLIB_BASE_URL = "https://lrclib.net/api/get"
        private const val USER_AGENT = "Harmony Music Player (https://github.com/harmony-music)"
    }
    
    suspend fun getLyrics(song: Song): LyricsResult = withContext(Dispatchers.IO) {
        try {
            // 1. Check local cache first
            val cached = getCachedLyrics(song.id)
            if (cached != null) {
                Log.d(TAG, "Found cached lyrics for: ${song.title}")
                return@withContext LyricsResult.Success(cached.first, cached.second)
            }
            
            // 2. Try LRCLIB API with strict matching
            Log.d(TAG, "Fetching lyrics from LRCLIB for: ${song.title} - ${song.artist}")
            val lrclibResult = fetchFromLrclib(song)
            
            if (lrclibResult != null) {
                // Cache the result with LRCLIB source
                cacheLyrics(song.id, lrclibResult, "LRCLIB")
                Log.d(TAG, "Successfully fetched and cached lyrics from LRCLIB")
                return@withContext LyricsResult.Success(lrclibResult, "LRCLIB")
            }
            
            // 3. No lyrics found
            Log.d(TAG, "No lyrics found for: ${song.title}")
            LyricsResult.NotFound
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading lyrics", e)
            LyricsResult.Error(e.message ?: "Unknown error")
        }
    }
    
    private fun fetchFromLrclib(song: Song): String? {
        try {
            val durationSeconds = (song.duration / 1000).toInt()
            
            // Build URL with query parameters
            val urlBuilder = StringBuilder(LRCLIB_BASE_URL)
            urlBuilder.append("?track_name=").append(URLEncoder.encode(song.title, "UTF-8"))
            urlBuilder.append("&artist_name=").append(URLEncoder.encode(song.artist, "UTF-8"))
            urlBuilder.append("&album_name=").append(URLEncoder.encode(song.album, "UTF-8"))
            urlBuilder.append("&duration=").append(durationSeconds)
            
            val url = URL(urlBuilder.toString())
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                // Prefer synced lyrics, fallback to plain lyrics
                val syncedLyrics = json.optString("syncedLyrics", null)
                val plainLyrics = json.optString("plainLyrics", null)
                
                // Verify duration matches (within 3 seconds tolerance)
                val apiDuration = json.optInt("duration", 0)
                if (apiDuration > 0 && kotlin.math.abs(apiDuration - durationSeconds) > 3) {
                    Log.w(TAG, "Duration mismatch: expected $durationSeconds, got $apiDuration")
                    return null
                }
                
                return when {
                    !syncedLyrics.isNullOrBlank() -> syncedLyrics
                    !plainLyrics.isNullOrBlank() -> plainLyrics
                    else -> null
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.d(TAG, "LRCLIB: No lyrics found (404)")
                return null
            } else {
                Log.w(TAG, "LRCLIB request failed with code: $responseCode")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from LRCLIB", e)
            return null
        }
    }
    
    fun importLrcFile(songId: Long, lrcContent: String): Boolean {
        return try {
            if (lrcContent.contains("[") && lrcContent.contains("]")) {
                cacheLyrics(songId, lrcContent, "SimpMusic")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing LRC file", e)
            false
        }
    }
    
    fun saveLyrics(songId: Long, lyrics: String) {
        cacheLyrics(songId, lyrics, "SimpMusic")
    }
    
    private fun getCachedLyrics(songId: Long): Pair<String, String>? {
        val lyrics = prefs.getString("lyrics_$songId", null) ?: return null
        val source = prefs.getString("source_$songId", "SimpMusic") // Default to SimpMusic for legacy/manual
        return lyrics to (source ?: "SimpMusic")
    }
    
    private fun cacheLyrics(songId: Long, lyrics: String, source: String) {
        prefs.edit()
            .putString("lyrics_$songId", lyrics)
            .putString("source_$songId", source)
            .apply()
    }
}

sealed class LyricsResult {
    data class Success(val lyrics: String, val source: String) : LyricsResult()
    data object NotFound : LyricsResult()
    data class Error(val message: String) : LyricsResult()
}
