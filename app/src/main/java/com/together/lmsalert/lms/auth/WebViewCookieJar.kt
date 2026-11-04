package com.together.lmsalert.lms.auth

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * WebView 의 CookieManager 와 OkHttp 가 쿠키를 공유하게 해 준다.
 *
 * WebView 에서 학교 SSO 로그인을 하면 LMS 세션 쿠키가 CookieManager 에 저장되고,
 * OkHttp 요청은 그 쿠키를 그대로 사용한다. 응답으로 갱신된 쿠키도 다시 CookieManager 에 저장하므로
 * 세션 연장도 WebView 와 공유된다.
 *
 * → 학교 계정 비밀번호를 앱이 직접 저장하지 않는다.
 */
class WebViewCookieJar(
    private val cookieManager: () -> CookieManager = { CookieManager.getInstance() },
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cookieManager().getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val manager = cookieManager()
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
        manager.flush()
    }
}
