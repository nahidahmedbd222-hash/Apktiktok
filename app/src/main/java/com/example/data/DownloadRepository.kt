package com.example.data

import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun insert(item: DownloadItem): Long {
        return downloadDao.insertDownload(item)
    }

    suspend fun update(item: DownloadItem) {
        downloadDao.updateDownload(item)
    }

    suspend fun delete(item: DownloadItem) {
        downloadDao.deleteDownload(item)
    }

    suspend fun deleteById(id: Long) {
        downloadDao.deleteDownloadById(id)
    }

    suspend fun clearAll() {
        downloadDao.clearAll()
    }
}
