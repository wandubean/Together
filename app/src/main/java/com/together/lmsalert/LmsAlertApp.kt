package com.together.lmsalert

import android.app.Application
import com.together.lmsalert.notification.NotificationChannels

class LmsAlertApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createAll(this)
    }
}
