package com.daehyun.myapplication

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.browser.customtabs.CustomTabsIntent
import com.daehyun.myapplication.ui.theme.MyApplicationTheme

private const val TARGET_URL = "https://xn--vk1b177d.com" // 대현.com (punycode)
private const val CUSTOM_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WebApp() }
    }
}

@Composable
private fun WebApp() {
    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            WebViewScreen()
        }
    }
}

@Composable
private fun WebViewScreen(url: String = TARGET_URL, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.userAgentString = CUSTOM_USER_AGENT

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUri = request?.url ?: return false
                    val scheme = targetUri.scheme.orEmpty()

                    if (shouldOpenWithCustomTab(targetUri)) {
                        return openInCustomTab(context, targetUri) || openExternal(context, targetUri)
                    }

                    if (scheme == "http" || scheme == "https") return false
                    if (scheme == "intent") {
                        return handleIntentScheme(context, targetUri.toString(), view)
                    }

                    return openExternal(context, targetUri)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    canGoBack = view.canGoBack()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    // Keep window.open / target=_blank inside the same WebView.
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = view
                    resultMsg.sendToTarget()
                    return true
                }
            }
        }
    }

    LaunchedEffect(url) {
        webView.loadUrl(url)
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
        update = { canGoBack = it.canGoBack() }
    )

    BackHandler(enabled = canGoBack) {
        webView.goBack()
        canGoBack = webView.canGoBack()
    }
}

private fun shouldOpenWithCustomTab(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    if (host == "accounts.google.com" || host.endsWith(".google.com")) return true
    return false
}

private fun openInCustomTab(context: Context, uri: Uri): Boolean {
    return try {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
        true
    } catch (_: Exception) {
        false
    }
}

private fun openExternal(context: Context, uri: Uri): Boolean {
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun handleIntentScheme(context: Context, intentUri: String, webView: WebView?): Boolean {
    return try {
        val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        val fallback = intent.getStringExtra("browser_fallback_url")
        if (!fallback.isNullOrBlank() && webView != null) {
            webView.loadUrl(fallback)
            return true
        }
        if (intent.`package` != null) {
            context.startActivity(intent)
            return true
        }
        // If nothing to handle, consume to avoid bouncing out.
        true
    } catch (_: Exception) {
        true
    }
}
