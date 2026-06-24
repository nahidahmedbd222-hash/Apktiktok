package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DownloadDatabase.getDatabase(application)
    private val repository = DownloadRepository(database.downloadDao())
    val settingsManager = SettingsManager(application)

    // Download history
    val downloadHistory: StateFlow<List<DownloadItem>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Network status
    private val _isNetworkConnected = MutableStateFlow(NetworkUtils.isNetworkAvailable(application))
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    // Settings States
    private val _downloadLocation = MutableStateFlow(settingsManager.downloadLocationType)
    val downloadLocation: StateFlow<String> = _downloadLocation.asStateFlow()

    private val _darkModeTheme = MutableStateFlow(settingsManager.darkModeTheme)
    val darkModeTheme: StateFlow<String> = _darkModeTheme.asStateFlow()

    private val _cacheEnabled = MutableStateFlow(settingsManager.cacheEnabled)
    val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()

    private val _desktopMode = MutableStateFlow(settingsManager.desktopMode)
    val desktopMode: StateFlow<Boolean> = _desktopMode.asStateFlow()

    fun refreshNetworkStatus() {
        _isNetworkConnected.value = NetworkUtils.isNetworkAvailable(getApplication())
    }

    fun setDownloadLocation(type: String) {
        settingsManager.downloadLocationType = type
        _downloadLocation.value = type
    }

    fun setDarkModeTheme(theme: String) {
        settingsManager.darkModeTheme = theme
        _darkModeTheme.value = theme
    }

    fun setCacheEnabled(enabled: Boolean) {
        settingsManager.cacheEnabled = enabled
        _cacheEnabled.value = enabled
    }

    fun setDesktopMode(enabled: Boolean) {
        settingsManager.desktopMode = enabled
        _desktopMode.value = enabled
    }

    fun deleteDownload(item: DownloadItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun deleteDownloadById(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
