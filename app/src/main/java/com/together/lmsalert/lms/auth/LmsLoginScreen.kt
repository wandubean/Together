package com.together.lmsalert.lms.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.together.lmsalert.lms.LmsConfig
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * WebView 로 학교 SSO 로그인을 진행하는 화면.
 *
 * LMS 도메인 페이지가 로드될 때마다 [verifySession] 으로 세션을 확인하고,
 * 성공하면 [onLoginSuccess] 를 한 번 호출한다.
 * 작업 B 는 이 Composable 을 내비게이션에 넣기만 하면 된다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LmsLoginScreen(
    verifySession: suspend () -> Boolean,
    onLoginSuccess: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onCancel()
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true // SSO 페이지가 JS 를 사용
                    settings.domStorageEnabled = true
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            if (finished || !isLmsPage(url)) return
                            CookieManager.getInstance().flush()
                            scope.launch {
                                val ok = try {
                                    verifySession()
                                } catch (_: IOException) {
                                    false
                                }
                                if (ok && !finished) {
                                    finished = true
                                    onLoginSuccess()
                                }
                            }
                        }
                    }
                    loadUrl(LmsConfig.LOGIN_URL)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
        )
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

private fun isLmsPage(url: String?): Boolean {
    val uri = url?.let(Uri::parse) ?: return false
    return uri.host == LmsConfig.HOST && uri.path?.startsWith("/login") != true
}
