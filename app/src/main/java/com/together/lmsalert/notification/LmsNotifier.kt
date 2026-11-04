package com.together.lmsalert.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.together.lmsalert.MainActivity
import com.together.lmsalert.R
import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.sync.AssignmentChanges
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 동기화 결과에 따른 시스템 알림 표시 (기획서 2.3 중 신규/마감변경/동기화 실패). */
class LmsNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun notifyChanges(changes: AssignmentChanges) {
        if (changes.isInitialSync) return // 첫 동기화에 과제 전체가 '신규'로 쏟아지는 것 방지

        val newOnes = changes.newAssignments
        if (newOnes.size > GROUP_THRESHOLD) {
            show(
                id = ID_NEW_SUMMARY,
                channel = NotificationChannels.NEW_ASSIGNMENT,
                title = context.getString(R.string.noti_new_summary_title, newOnes.size),
                text = newOnes.joinToString(", ") { it.name },
            )
        } else {
            newOnes.forEach { a ->
                show(
                    id = idFor(a, KIND_NEW),
                    channel = NotificationChannels.NEW_ASSIGNMENT,
                    title = context.getString(R.string.noti_new_title, a.name),
                    text = context.getString(R.string.noti_new_text, a.courseName, formatDue(a.dueAt)),
                    assignmentId = a.id,
                )
            }
        }

        changes.dueDateChanged.forEach { change ->
            val a = change.assignment
            show(
                id = idFor(a, KIND_DUE),
                channel = NotificationChannels.ASSIGNMENT_CHANGE,
                title = context.getString(R.string.noti_due_changed_title, a.name),
                text = context.getString(
                    R.string.noti_due_changed_text,
                    a.courseName, formatDue(change.oldDueAt), formatDue(change.newDueAt),
                ),
                assignmentId = a.id,
            )
        }
    }

    fun notifyAuthRequired() = show(
        id = ID_SYNC_PROBLEM,
        channel = NotificationChannels.SYNC_STATUS,
        title = context.getString(R.string.noti_auth_required_title),
        text = context.getString(R.string.noti_auth_required_text),
    )

    fun notifySyncFailed() = show(
        id = ID_SYNC_PROBLEM,
        channel = NotificationChannels.SYNC_STATUS,
        title = context.getString(R.string.noti_sync_failed_title),
        text = context.getString(R.string.noti_sync_failed_text),
    )

    fun clearSyncProblem() = manager.cancel(ID_SYNC_PROBLEM)

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun show(id: Int, channel: String, title: String, text: String, assignmentId: Long? = null) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(id, assignmentId))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // 권한이 확인 직후 회수된 경우
        }
    }

    /** 알림을 누르면 앱 실행. 작업 B 는 [EXTRA_ASSIGNMENT_ID] 로 과제 상세 화면을 열면 된다. */
    private fun openAppIntent(requestCode: Int, assignmentId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            assignmentId?.let { putExtra(EXTRA_ASSIGNMENT_ID, it) }
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatDue(instant: Instant?): String =
        instant?.atZone(ZoneId.systemDefault())?.format(DUE_FORMAT)
            ?: context.getString(R.string.no_due_date)

    companion object {
        const val EXTRA_ASSIGNMENT_ID = "assignment_id"

        private const val GROUP_THRESHOLD = 3
        private const val ID_SYNC_PROBLEM = 1
        private const val ID_NEW_SUMMARY = 2
        private const val KIND_NEW = 1
        private const val KIND_DUE = 2

        private val DUE_FORMAT = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN)

        private fun idFor(a: LmsAssignment, kind: Int): Int = (a.id * 10 + kind).hashCode()
    }
}
