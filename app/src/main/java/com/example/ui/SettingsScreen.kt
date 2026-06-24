package com.example.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SettingsManager
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val downloadLocation by viewModel.downloadLocation.collectAsStateWithLifecycle()
    val darkModeTheme by viewModel.darkModeTheme.collectAsStateWithLifecycle()
    val cacheEnabled by viewModel.cacheEnabled.collectAsStateWithLifecycle()
    val desktopMode by viewModel.desktopMode.collectAsStateWithLifecycle()

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section: Storage
            SettingsSectionHeader(title = "Download Location")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .selectableGroup()
                        .padding(8.dp)
                ) {
                    LocationOption(
                        title = "Public Downloads Folder",
                        description = "Saves to system /Download folder (Recommended for gallery visibility)",
                        selected = downloadLocation == SettingsManager.VAL_LOC_PUBLIC_DOWNLOADS,
                        onClick = { viewModel.setDownloadLocation(SettingsManager.VAL_LOC_PUBLIC_DOWNLOADS) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    LocationOption(
                        title = "App Private Storage",
                        description = "Saves in app-specific isolated folder (Hidden from photo galleries)",
                        selected = downloadLocation == SettingsManager.VAL_LOC_PRIVATE_INTERNAL,
                        onClick = { viewModel.setDownloadLocation(SettingsManager.VAL_LOC_PRIVATE_INTERNAL) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    LocationOption(
                        title = "App Private SD Card",
                        description = "Saves to external SD card directory if available",
                        selected = downloadLocation == SettingsManager.VAL_LOC_PRIVATE_EXTERNAL,
                        onClick = { viewModel.setDownloadLocation(SettingsManager.VAL_LOC_PRIVATE_EXTERNAL) }
                    )
                }
            }

            // Section: Display & Appearance
            SettingsSectionHeader(title = "Theme")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .selectableGroup()
                        .padding(8.dp)
                ) {
                    ThemeOption(
                        title = "Follow System Settings",
                        selected = darkModeTheme == "auto",
                        onClick = { viewModel.setDarkModeTheme("auto") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    ThemeOption(
                        title = "Always Light Mode",
                        selected = darkModeTheme == "light",
                        onClick = { viewModel.setDarkModeTheme("light") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    ThemeOption(
                        title = "Always Dark Mode",
                        selected = darkModeTheme == "dark",
                        onClick = { viewModel.setDarkModeTheme("dark") }
                    )
                }
            }

            // Section: Browser Preferences
            SettingsSectionHeader(title = "Browser Preferences")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Cache toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Website Cache", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Improves page load speeds significantly", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = cacheEnabled,
                            onCheckedChange = { viewModel.setCacheEnabled(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Desktop mode toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force Desktop Mode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Requests desktop layout for full website features", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = desktopMode,
                            onCheckedChange = { viewModel.setDesktopMode(it) }
                        )
                    }
                }
            }

            // Section: Storage Cleaner
            SettingsSectionHeader(title = "Maintenance")
            Button(
                onClick = { showClearConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Web Cache & Databases")
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Web Data?") },
            text = { Text("This will permanently clear browser cache, cookies, databases, history records and login sessions inside the WebView. Downloads on your device storage will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            // Clear cookies and web storage
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            WebStorage.getInstance().deleteAllData()
                            
                            // Instantiate helper WebView to clear general cache
                            val tempWebView = WebView(context)
                            tempWebView.clearCache(true)
                            
                            Toast.makeText(context, "All web storage & cache cleared successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        } finally {
                            showClearConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFFE2C55),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun LocationOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // Selected by the row container
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
