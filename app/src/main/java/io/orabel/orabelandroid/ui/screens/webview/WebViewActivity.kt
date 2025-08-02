/*
 * Copyright (C) 2024 Orabel IA
 * WebView activity to display external website within Orabel IA app
 */

package io.orabel.orabelandroid.ui.screens.webview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.theme.ModernOrabelTheme
import io.orabel.orabelandroid.utils.NetworkUtils
import org.koin.android.ext.android.inject
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.ui.theme.*

class WebViewActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val DEFAULT_URL = "https://masinformacion.usasavorwarts.com/"
    }
    
    private var webView: WebView? = null
    private val orabelPreferences by inject<OrabelPreferences>()

    // Request permissions launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Algunos permisos fueron denegados. El sitio web puede no funcionar completamente.", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.more_info)
        
        // Check internet connectivity before proceeding
        if (!NetworkUtils.isInternetAvailable(this)) {
            Toast.makeText(this, getString(R.string.no_internet_connection), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Request necessary permissions
        requestPermissions()
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                WebViewScreen(url = url, title = title)
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        webView?.let {
            if (it.canGoBack()) {
                it.goBack()
            } else {
                finish()
            }
        } ?: finish()
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WebViewScreen(url: String, title: String) {
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(true) }
        var hasError by remember { mutableStateOf(false) }
        var pageTitle by remember { mutableStateOf(title) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = pageTitle,
                            maxLines = 1
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            webView?.let {
                                if (it.canGoBack()) {
                                    it.goBack()
                                } else {
                                    finish()
                                }
                            } ?: finish()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = getString(R.string.back_desc)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            webView?.reload() 
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Recargar"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            this@WebViewActivity.webView = this
                            setupWebView(
                                webView = this, 
                                url = url,
                                onLoadingChanged = { isLoadingState -> isLoading = isLoadingState },
                                onErrorChanged = { hasErrorState -> hasError = hasErrorState },
                                onTitleChanged = { newTitle -> pageTitle = newTitle }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Loading indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = getString(R.string.loading_website),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                
                // Error state
                if (hasError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = getString(R.string.website_error),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = getString(R.string.check_connection),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    hasError = false
                                    isLoading = true
                                    webView?.reload()
                                }
                            ) {
                                Text(getString(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(
        webView: WebView, 
        url: String,
        onLoadingChanged: (Boolean) -> Unit,
        onErrorChanged: (Boolean) -> Unit,
        onTitleChanged: (String) -> Unit
    ) {
        webView.apply {
            settings.apply {
                // Enable JavaScript and DOM storage
                javaScriptEnabled = true
                domStorageEnabled = true
                
                // File access permissions
                allowFileAccess = true
                allowContentAccess = true
                // Note: allowFileAccessFromFileURLs and allowUniversalAccessFromFileURLs are deprecated
                // but may still be needed for compatibility with older web content
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                
                // Zoom and layout settings
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                
                // Enhanced settings for modern web content
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                // Note: databaseEnabled is deprecated but may still be needed for compatibility
                @Suppress("DEPRECATION")
                databaseEnabled = true
                
                // Set a modern user agent
                userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                
                // Additional settings for better compatibility
                setGeolocationEnabled(true)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onLoadingChanged(true)
                    onErrorChanged(false)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onLoadingChanged(false)
                    view?.title?.let { title ->
                        if (title.isNotEmpty()) {
                            onTitleChanged(title)
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onLoadingChanged(false)
                        onErrorChanged(true)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }

            // Load the URL
            loadUrl(url)
        }
    }
}
