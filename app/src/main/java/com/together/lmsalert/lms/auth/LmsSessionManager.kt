package com.together.lmsalert.lms.auth

import android.webkit.CookieManager
import com.together.lmsalert.lms.LmsConfig
import com.together.lmsalert.lms.api.LmsApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** LMS 로그인 상태 관리. 실제 로그인 화면은 [LmsLoginScreen]. */
class LmsSessionManager(private val api: LmsApiClient) {

    /** 저장된 LMS 쿠키가 있는지만 빠르게 확인 (네트워크 X). 유효성은 [isLoggedIn] 으로 확인. */
    fun hasStoredSession(): Boolean =
        !CookieManager.getInstance().getCookie(LmsConfig.BASE_URL).isNullOrBlank()

    /** 서버에 실제로 물어봐서 세션이 유효한지 확인한다. 네트워크 오류 시 IOException. */
    suspend fun isLoggedIn(): Boolean = hasStoredSession() && api.isSessionValid()

    /** WebView/OkHttp 가 공유하는 쿠키를 모두 지워 로그아웃한다. */
    suspend fun logout() = withContext(Dispatchers.Main) {
        val manager = CookieManager.getInstance()
        suspendCancellableCoroutine { cont ->
            manager.removeAllCookies { cont.resume(Unit) }
        }
        manager.flush()
    }
}
