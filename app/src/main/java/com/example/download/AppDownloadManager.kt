package com.example.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import com.example.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AppDownloadManager(
    private val context: Context,
    private val repository: DownloadRepository,
    private val settingsManager: SettingsManager
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val guessedMime = mimeType ?: getMimeType(url)
            val fileName = URLUtil.guessFileName(url, contentDisposition, guessedMime)

            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(guessedMime)
            
            userAgent?.let {
                request.addRequestHeader("User-Agent", it)
            }

            request.setTitle(fileName)
            request.setDescription("Downloading file from TikPorn...")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            // Determine download location based on user settings
            val locType = settingsManager.downloadLocationType
            val destinationFile: File

            when (locType) {
                SettingsManager.VAL_LOC_PRIVATE_INTERNAL -> {
                    // App private internal folder
                    val dir = context.filesDir
                    destinationFile = File(dir, fileName)
                    request.setDestinationUri(Uri.fromFile(destinationFile))
                }
                SettingsManager.VAL_LOC_PRIVATE_EXTERNAL -> {
                    // App private SD card folder if available, else standard private
                    val dirs = context.getExternalFilesDirs(null)
                    val dir = if (dirs.size > 1 && dirs[1] != null) dirs[1] else context.getExternalFilesDir(null) ?: context.filesDir
                    destinationFile = File(dir, fileName)
                    request.setDestinationUri(Uri.fromFile(destinationFile))
                }
                else -> {
                    // Standard public Downloads directory (Default)
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    destinationFile = File(dir, fileName)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
            }

            request.setAllowedOverMetered(true)
            request.setAllowedOverRoaming(true)

            // Queue download
            val downloadId = downloadManager.enqueue(request)

            // Save record in the local DB
            scope.launch {
                val item = DownloadItem(
                    id = downloadId, // Use downloadManager's transaction ID as primary key
                    url = url,
                    fileName = fileName,
                    filePath = destinationFile.absolutePath,
                    fileSize = 0L, // Will update when queryable
                    status = "DOWNLOADING",
                    mimeType = guessedMime
                )
                repository.insert(item)
            }

            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}
