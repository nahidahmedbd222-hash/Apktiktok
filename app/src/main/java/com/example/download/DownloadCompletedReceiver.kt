package com.example.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.widget.Toast
import com.example.data.DownloadDatabase
import com.example.data.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DownloadCompletedReceiver(
    private val repository: DownloadRepository
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == -1L) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor? = downloadManager.query(query)

            CoroutineScope(Dispatchers.IO).launch {
                val currentDownloads = repository.allDownloads.firstOrNull() ?: emptyList()
                val dbItem = currentDownloads.find { it.id == downloadId } ?: return@launch

                if (cursor != null && cursor.moveToFirst()) {
                    val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusColumn != -1) cursor.getInt(statusColumn) else DownloadManager.STATUS_FAILED

                    val sizeColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L

                    val updatedItem = if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        dbItem.copy(status = "COMPLETED", fileSize = size)
                    } else {
                        dbItem.copy(status = "FAILED", fileSize = size)
                    }

                    repository.update(updatedItem)

                    // Show visual feedback on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            Toast.makeText(context, "Download complete: ${dbItem.fileName}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Download failed: ${dbItem.fileName}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                cursor?.close()
            }
        }
    }
}
