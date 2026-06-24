package com.example

import android.app.DownloadManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DownloadDatabase
import com.example.data.DownloadRepository
import com.example.download.DownloadCompletedReceiver
import com.example.ui.AboutScreen
import com.example.ui.HistoryScreen
import com.example.ui.MainScreen
import com.example.ui.SettingsScreen
import com.example.ui.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

enum class Tab {
    BROWSER,
    DOWNLOADS,
    SETTINGS,
    ABOUT
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }
    
    private lateinit var downloadReceiver: DownloadCompletedReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register the dynamic download complete receiver
        val database = DownloadDatabase.getDatabase(this)
        val repository = DownloadRepository(database.downloadDao())
        downloadReceiver = DownloadCompletedReceiver(repository)
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }

        // Pre-create WebView Code Cache directories to prevent harmless Chromium enumeration warning/error logs
        try {
            val codeCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            val jsDir = java.io.File(codeCacheDir, "js")
            val wasmDir = java.io.File(codeCacheDir, "wasm")
            if (!jsDir.exists()) jsDir.mkdirs()
            if (!wasmDir.exists()) wasmDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            val darkModeTheme by viewModel.darkModeTheme.collectAsStateWithLifecycle()
            
            MyApplicationTheme(darkModePreference = darkModeTheme) {
                var isSplashActive by remember { mutableStateOf(true) }

                if (isSplashActive) {
                    SplashScreen(onTimeout = { isSplashActive = false })
                } else {
                    MainAppLayout(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainAppLayout(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(Tab.BROWSER) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == Tab.BROWSER,
                    onClick = { currentTab = Tab.BROWSER },
                    icon = { Icon(imageVector = Icons.Default.Web, contentDescription = "Browser") },
                    label = { Text("Browser") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.DOWNLOADS,
                    onClick = { currentTab = Tab.DOWNLOADS },
                    icon = { Icon(imageVector = Icons.Default.Download, contentDescription = "Downloads") },
                    label = { Text("Downloads") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.SETTINGS,
                    onClick = { currentTab = Tab.SETTINGS },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.ABOUT,
                    onClick = { currentTab = Tab.ABOUT },
                    icon = { Icon(imageVector = Icons.Default.Info, contentDescription = "About") },
                    label = { Text("About") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Keep the WebView (Browser) composed and alive off-screen to preserve state!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (currentTab == Tab.BROWSER) 1f else 0f
                        translationX = if (currentTab == Tab.BROWSER) 0f else 100000f
                    }
            ) {
                MainScreen(viewModel = viewModel)
            }

            // Other screens are standard compose pages
            when (currentTab) {
                Tab.BROWSER -> {
                    // Handled off-screen above
                }
                Tab.DOWNLOADS -> {
                    HistoryScreen(viewModel = viewModel)
                }
                Tab.SETTINGS -> {
                    SettingsScreen(viewModel = viewModel)
                }
                Tab.ABOUT -> {
                    AboutScreen()
                }
            }
        }
    }
}
