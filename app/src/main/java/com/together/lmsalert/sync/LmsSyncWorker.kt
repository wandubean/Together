package com.together.lmsalert.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.together.lmsalert.LmsAlertApp
import com.together.lmsalert.lms.api.LmsAuthException
import kotlinx.serialization.SerializationException
import java.io.IOException

/** 동기화 결과 종류. WorkInfo.outputData 의 [LmsSyncWorker.KEY_OUTCOME] 값. */
enum class SyncOutcome {
    SUCCESS,

    /** 세션 없음/만료 → 재로그인 필요 */
    AUTH_REQUIRED,

    /** 네트워크/서버 오류 */
    NETWORK_ERROR,

    /** 응답 형식이 예상과 다름 (LMS 개편 등) */
    PARSE_ERROR,
}

/**
 * LMS 조회 → 변경 감지 → 저장 → 알림 을 한 번 수행하는 워커.
 * 주기 실행/수동 실행 모두 이 워커를 사용한다 ([SyncScheduler]).
 */
class LmsSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val container = (appContext.applicationContext as LmsAlertApp).container
    private val isManual get() = inputData.getString(KEY_TRIGGER) == TRIGGER_MANUAL

    override suspend fun doWork(): Result {
        if (!container.sessionManager.hasStoredSession()) {
            return authRequired()
        }

        return try {
            val store = container.assignmentStore
            val isInitial = store.getLastSyncedAt() == null
            val previous = store.getKnownAssignments()

            val fetched = container.lmsApi.fetchAll()
            val changes = AssignmentChangeDetector.detect(previous, fetched, isInitial)

            store.saveSyncResult(fetched, changes)
            container.notifier.notifyChanges(changes)
            container.notifier.clearSyncProblem()

            Log.i(TAG, "동기화 완료: 과제 ${fetched.assignments.size}개, 신규 ${changes.newAssignments.size}, " +
                "마감변경 ${changes.dueDateChanged.size}, 삭제 ${changes.removed.size}, " +
                "실패 과목 ${fetched.failedCourseIds.size}")

            Result.success(
                output(SyncOutcome.SUCCESS) {
                    putLong(KEY_SYNCED_AT, fetched.fetchedAt.toEpochMilli())
                    putInt(KEY_TOTAL, fetched.assignments.size)
                    putInt(KEY_NEW, changes.newAssignments.size)
                    putInt(KEY_DUE_CHANGED, changes.dueDateChanged.size)
                    putInt(KEY_REMOVED, changes.removed.size)
                    putInt(KEY_FAILED_COURSES, fetched.failedCourseIds.size)
                }
            )
        } catch (e: LmsAuthException) {
            Log.w(TAG, "인증 필요", e)
            authRequired()
        } catch (e: IOException) {
            Log.w(TAG, "네트워크 오류 (시도 ${runAttemptCount + 1})", e)
            // 수동 갱신은 즉시 결과를 보여주고, 주기 조회는 몇 번 재시도 후 알린다.
            if (!isManual && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                if (!isManual) container.notifier.notifySyncFailed()
                Result.failure(output(SyncOutcome.NETWORK_ERROR))
            }
        } catch (e: SerializationException) {
            Log.e(TAG, "응답 파싱 실패", e)
            if (!isManual) container.notifier.notifySyncFailed()
            Result.failure(output(SyncOutcome.PARSE_ERROR))
        }
    }

    private fun authRequired(): Result {
        container.notifier.notifyAuthRequired()
        // 재시도해도 해결되지 않으므로 failure. 주기 작업은 다음 주기에 다시 실행된다.
        return Result.failure(output(SyncOutcome.AUTH_REQUIRED))
    }

    private inline fun output(outcome: SyncOutcome, block: Data.Builder.() -> Unit = {}): Data =
        Data.Builder()
            .putAll(workDataOf(KEY_OUTCOME to outcome.name))
            .apply(block)
            .build()

    companion object {
        private const val TAG = "LmsSyncWorker"
        private const val MAX_RETRIES = 3

        const val KEY_TRIGGER = "trigger"
        const val TRIGGER_PERIODIC = "periodic"
        const val TRIGGER_MANUAL = "manual"

        const val KEY_OUTCOME = "outcome"
        const val KEY_SYNCED_AT = "synced_at"
        const val KEY_TOTAL = "total"
        const val KEY_NEW = "new"
        const val KEY_DUE_CHANGED = "due_changed"
        const val KEY_REMOVED = "removed"
        const val KEY_FAILED_COURSES = "failed_courses"

        fun outcomeOf(data: Data): SyncOutcome? =
            data.getString(KEY_OUTCOME)?.let { name -> SyncOutcome.entries.firstOrNull { it.name == name } }
    }
}
