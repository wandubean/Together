package com.together.lmsalert

import android.content.Context
import androidx.work.WorkManager
import com.together.lmsalert.lms.api.LmsApiClient
import com.together.lmsalert.lms.auth.LmsSessionManager
import com.together.lmsalert.lms.auth.WebViewCookieJar
import com.together.lmsalert.notification.LmsNotifier
import com.together.lmsalert.sync.AssignmentStore
import com.together.lmsalert.sync.InMemoryAssignmentStore
import com.together.lmsalert.sync.SyncScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 앱 전역 객체 모음 (수동 DI). [LmsAlertApp.container] 로 접근.
 * ViewModel 에서는 `(application as LmsAlertApp).container` 로 꺼내 쓰면 된다.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val lmsApi = LmsApiClient(httpClient)
    val sessionManager = LmsSessionManager(lmsApi)

    // TODO(작업 B): Room 구현체로 교체
    val assignmentStore: AssignmentStore = InMemoryAssignmentStore()

    val notifier = LmsNotifier(appContext)
    val syncScheduler = SyncScheduler(WorkManager.getInstance(appContext))
}
