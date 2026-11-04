package com.together.lmsalert.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * LMS 동기화 작업 예약/취소/관찰.
 *
 * - 주기 조회: 로그인 성공 시, 설정에서 주기를 바꿀 때 [schedulePeriodic] 호출
 *   (WorkManager 가 재부팅 후에도 유지하므로 앱 시작마다 다시 부를 필요 없음)
 * - 수동 갱신: 홈 화면 새로고침에서 [syncNow] 호출, 진행 상태는 [observeManualSync]
 */
class SyncScheduler(private val workManager: WorkManager) {

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(interval: SyncInterval) {
        val request = PeriodicWorkRequestBuilder<LmsSyncWorker>(interval.minutes, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(LmsSyncWorker.KEY_TRIGGER to LmsSyncWorker.TRIGGER_PERIODIC))
            .addTag(TAG)
            .build()
        // UPDATE: 이미 예약돼 있으면 주기만 바꾸고 다음 실행 시점은 최대한 유지
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelPeriodic() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    /**
     * 즉시 1회 동기화. 이미 진행 중이면 새로 만들지 않는다.
     * 네트워크 제약을 걸지 않아서, 오프라인이면 대기하지 않고 바로 실패로 끝난다 (사용자에게 결과 표시).
     */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<LmsSyncWorker>()
            .setInputData(workDataOf(LmsSyncWorker.KEY_TRIGGER to LmsSyncWorker.TRIGGER_MANUAL))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun observeManualSync(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(MANUAL_WORK).map { it.firstOrNull() }

    fun observePeriodicSync(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK).map { it.firstOrNull() }

    companion object {
        const val TAG = "lms_sync"
        const val PERIODIC_WORK = "lms_sync_periodic"
        const val MANUAL_WORK = "lms_sync_manual"
    }
}
