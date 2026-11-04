package com.together.lmsalert.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.together.lmsalert.R

/** 알림 채널 (사용자가 시스템 설정에서 종류별로 켜고 끌 수 있음). Application.onCreate 에서 생성. */
object NotificationChannels {
    const val NEW_ASSIGNMENT = "new_assignment"
    const val ASSIGNMENT_CHANGE = "assignment_change"
    const val DEADLINE = "deadline"          // 마감 임박 / 사용자 지정 알림 (AlarmManager, 다음 단계)
    const val SYNC_STATUS = "sync_status"

    fun createAll(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            listOf(
                channel(context, NEW_ASSIGNMENT, R.string.channel_new_assignment, NotificationManager.IMPORTANCE_DEFAULT),
                channel(context, ASSIGNMENT_CHANGE, R.string.channel_assignment_change, NotificationManager.IMPORTANCE_DEFAULT),
                channel(context, DEADLINE, R.string.channel_deadline, NotificationManager.IMPORTANCE_HIGH),
                channel(context, SYNC_STATUS, R.string.channel_sync_status, NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    private fun channel(context: Context, id: String, nameRes: Int, importance: Int) =
        NotificationChannel(id, context.getString(nameRes), importance)
}
