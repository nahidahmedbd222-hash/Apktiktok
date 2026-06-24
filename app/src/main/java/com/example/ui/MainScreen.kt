package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.data.DownloadRepository
import com.example.data.SettingsManager
import com.example.download.AppDownloadManager
import com.example.viewmodel.MainViewModel

data class DetectedVideo(
    val url: String,
    val title: String,
    val mimeType: String
)

class VideoDetectorInterface(private val onVideosDetected: (String) -> Unit) {
    @JavascriptInterface
    fun onVideosDetected(json: String) {
        onVideosDetected(json)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val isNetworkConnected by viewModel.isNetworkConnected.collectAsState()
    val cacheEnabled by viewModel.cacheEnabled.collectAsState()
    val desktopMode by viewModel.desktopMode.collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var webProgress by remember { mutableStateOf(0) }
    var isOffline by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("https://tik.porn/") }
    var showExitDialog by remember { mutableStateOf(false) }

    // Video Detector / IDM state
    var detectedVideos by remember { mutableStateOf<List<DetectedVideo>>(emptyList()) }
    var showVideoSheet by remember { mutableStateOf(false) }

    // Full screen video state
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val appDownloadManager = remember {
        val db = com.example.data.DownloadDatabase.getDatabase(context)
        val repo = DownloadRepository(db.downloadDao())
        AppDownloadManager(context, repo, viewModel.settingsManager)
    }

    // Permission launcher for Notifications on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkStatus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(permission)
            }
        }
    }

    // Handle offline recovery
    LaunchedEffect(isNetworkConnected) {
        if (isNetworkConnected && isOffline) {
            isOffline = false
        }
    }

    // Instantiate and remember WebView
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Settings configuration
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                allowFileAccess = true
                allowContentAccess = true
                
                // Acceleration
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            // Set DownloadListener
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                appDownloadManager.startDownload(url, userAgent, contentDisposition, mimeType)
            }

            // Add IDM style video detector interface
            addJavascriptInterface(
                VideoDetectorInterface { json ->
                    activity?.runOnUiThread {
                        val detectedList = mutableListOf<DetectedVideo>()
                        try {
                            val array = org.json.JSONArray(json)
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val url = obj.optString("url")
                                val title = obj.optString("title", "Detected Video")
                                val type = obj.optString("type", "video/mp4")
                                if (url.isNotEmpty() && !url.startsWith("blob:")) {
                                    detectedList.add(DetectedVideo(url = url, title = title, mimeType = type))
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        if (detectedList.isNotEmpty() && detectedList != detectedVideos) {
                            detectedVideos = detectedList
                        }
                    }
                },
                "VideoDetector"
            )
        }
    }

    // Periodically run script to auto-detect any playing/embedded video on screen (IDM-style)
    LaunchedEffect(isLoading, currentUrl) {
        if (!isLoading && !isOffline) {
            while (true) {
                val script = """
                    (function() {
                        var videos = [];
                        
                        // 1. Scan <video> and <source> elements
                        var videoTags = document.getElementsByTagName('video');
                        for (var i = 0; i < videoTags.length; i++) {
                            var v = videoTags[i];
                            var src = v.src;
                            if (src && !src.startsWith('blob:')) {
                                videos.push({url: src, title: document.title || 'Video Playback', type: 'video/mp4'});
                            }
                            var sources = v.getElementsByTagName('source');
                            for (var j = 0; j < sources.length; j++) {
                                var s = sources[j];
                                if (s.src && !s.src.startsWith('blob:')) {
                                    videos.push({url: s.src, title: document.title || 'Video Quality', type: s.type || 'video/mp4'});
                                }
                            }
                        }
                        
                        // 2. Scan standard <a> link elements containing video files
                        var links = document.getElementsByTagName('a');
                        for (var i = 0; i < links.length; i++) {
                            var a = links[i];
                            var href = a.href;
                            if (href && href.match(/\.(mp4|m3u8|webm|mov|mkv)(\?|$)/i)) {
                                videos.push({url: href, title: a.innerText.trim() || document.title || 'Video Link', type: 'video/mp4'});
                            }
                        }
                        
                        // 3. Remove duplicates
                        var uniqueVideos = [];
                        var seenUrls = {};
                        for (var i = 0; i < videos.length; i++) {
                            var item = videos[i];
                            if (!seenUrls[item.url]) {
                                seenUrls[item.url] = true;
                                uniqueVideos.push(item);
                            }
                        }
                        
                        if (uniqueVideos.length > 0) {
                            VideoDetector.onVideosDetected(JSON.stringify(uniqueVideos));
                        }
                    })();
                """.trimIndent()
                
                try {
                    webView.evaluateJavascript(script, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                kotlinx.coroutines.delay(3000) // Poll every 3 seconds to ensure we capture newly injected streaming URLs
            }
        }
    }

    // Apply reactive preferences to WebView
    LaunchedEffect(cacheEnabled) {
        webView.settings.cacheMode = if (cacheEnabled) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_NO_CACHE
        }
    }

    LaunchedEffect(desktopMode) {
        webView.settings.userAgentString = if (desktopMode) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        } else {
            null // Default mobile user-agent
        }
        webView.reload()
    }

    // Define custom WebChromeClient
    val webChromeClient = remember {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                webProgress = newProgress
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                
                // Keep screen awake & rotate to landscape
                activity?.apply {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                val cv = customView ?: return
                
                // Hide custom view container
                val parent = cv.parent as? ViewGroup
                parent?.removeView(cv)
                
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                
                // Restore orientation & awake settings
                activity?.apply {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    webView.webChromeClient = webChromeClient

    // Define custom WebViewClient
    webView.webViewClient = remember {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
                detectedVideos = emptyList() // Clear old downloads of previous page
                if (url != null) {
                    currentUrl = url
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    isOffline = true
                    isLoading = false
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false // Load standard websites inside the WebView
                }
                // Handle special protocols safely
                try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    // Protocol not supported
                }
                return true
            }
        }
    }

    // Load initial URL if not already loading or loaded
    LaunchedEffect(Unit) {
        if (webView.url == null) {
            webView.loadUrl(currentUrl)
        }
    }

    // Handle Back Button press
    BackHandler(enabled = true) {
        if (customView != null) {
            webChromeClient.onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            showExitDialog = true
        }
    }

    // Main Layout Container
    Box(modifier = Modifier.fillMaxSize()) {
        if (customView != null) {
            // Full Screen Video View Container
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        addView(
                            customView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Standard Web Browser Layout
            Column(modifier = Modifier.fillMaxSize()) {
                // Address & Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Progress Indicator
                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { webProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = Color(0xFFFE2C55),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Simple Custom Browser Control Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (webView.canGoBack()) webView.goBack() },
                            enabled = webView.canGoBack()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (webView.canGoBack()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = { if (webView.canGoForward()) webView.goForward() },
                            enabled = webView.canGoForward()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (webView.canGoForward()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(onClick = { webView.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // URL Display card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Secure connection",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = currentUrl.removePrefix("https://").removePrefix("http://"),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // WebView and Pull-to-refresh container
                Box(modifier = Modifier.weight(1f)) {
                    if (isOffline || !isNetworkConnected) {
                        // Display offline/error view
                        OfflineErrorView(onRetry = {
                            isOffline = false
                            viewModel.refreshNetworkStatus()
                            webView.reload()
                        })
                    } else {
                        // Native SwipeRefreshLayout hosting the WebView
                        AndroidView(
                            factory = { ctx ->
                                SwipeRefreshLayout(ctx).apply {
                                    setColorSchemeColors(android.graphics.Color.parseColor("#FE2C55"))
                                    setOnRefreshListener {
                                        webView.reload()
                                    }
                                    
                                    // Add the remembered webView
                                    val parent = webView.parent as? ViewGroup
                                    parent?.removeView(webView)
                                    addView(webView)
                                }
                            },
                            update = { swipeRefreshLayout ->
                                swipeRefreshLayout.isRefreshing = isLoading
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // IDM Floating Action Button on top of standard layout
            if (detectedVideos.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 20.dp, end = 20.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showVideoSheet = true },
                        containerColor = Color(0xFFFE2C55),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = "Videos Found"
                            )
                            Text(
                                text = "Videos Found (${detectedVideos.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // IDM Video List Bottom Sheet
    if (showVideoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVideoSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color(0xFFFE2C55)
                    )
                    Text(
                        text = "IDM Video Downloader",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = "Select any video format/source below to download it immediately to your device.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(detectedVideos) { video ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = video.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = video.url,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            appDownloadManager.startDownload(
                                                url = video.url,
                                                userAgent = webView.settings.userAgentString,
                                                contentDisposition = null,
                                                mimeType = video.mimeType
                                            )
                                            showVideoSheet = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Video URL", video.url)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "URL copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Link", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App?") },
            text = { Text("Are you sure you want to close TikPorn Player?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        activity?.finish()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFE2C55))
                ) {
                    Text("Exit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun OfflineErrorView(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color(0xFFFE2C55),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Connection Failed",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please check your network settings and try reloading the page.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again", fontWeight = FontWeight.Bold)
            }
        }
    }
}
