package com.together.lmsalert.sync

import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.LmsAnnouncement
import com.together.lmsalert.lms.model.LmsFetchResult
import com.together.lmsalert.lms.model.SubmissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * 작업 A(동기화) ↔ 작업 B(데이터 저장) 경계.
 *
 * 동기화 워커는 이 인터페이스만 사용한다. 작업 B 가 Room 으로 구현체를 만들어
 * AppContainer.assignmentStore 를 교체하면 된다.
 */
interface AssignmentStore {

    /** 지금까지 알고 있는 과제 중 삭제/비공개 처리되지 않은 것. */
    suspend fun getKnownAssignments(): List<LmsAssignment>

    /** 마지막으로 동기화에 성공한 시각. 한 번도 없으면 null (= 첫 동기화). */
    suspend fun getLastSyncedAt(): Instant?

    /**
     * 동기화 결과 반영.
     * - [changes].removed 는 목록·캘린더에서 숨기되 기록은 남긴다 (기획서 2.2)
     * - 제출 상태가 UNKNOWN 이면 마지막으로 확인한 상태와 확인 시각을 유지한다
     * - 사용자의 '수동 완료 처리' 값은 덮어쓰지 않는다
     */
    suspend fun saveSyncResult(result: LmsFetchResult, changes: AssignmentChanges)
}

/**
 * 임시 구현 (메모리). 앱 프로세스가 종료되면 사라진다.
 * TODO(작업 B): Room 기반 구현으로 교체
 */
class InMemoryAssignmentStore : AssignmentStore {

    private val mutex = Mutex()
    private val assignments = MutableStateFlow<Map<Long, LmsAssignment>>(emptyMap())
    private var lastSyncedAt: Instant? = null
    private val announcements = MutableStateFlow<Map<Long, LmsAnnouncement>>(emptyMap())
    private val syncedAt = MutableStateFlow<Instant?>(null)
    val announcementsFlow: StateFlow<Map<Long, LmsAnnouncement>> = announcements.asStateFlow()
    val lastSyncedAtFlow: StateFlow<Instant?> = syncedAt.asStateFlow()

    /** 개발용 화면에서 확인하기 위한 스트림. */
    val assignmentsFlow: StateFlow<Map<Long, LmsAssignment>> = assignments.asStateFlow()

    override suspend fun getKnownAssignments(): List<LmsAssignment> =
        mutex.withLock { assignments.value.values.toList() }

    override suspend fun getLastSyncedAt(): Instant? = mutex.withLock { lastSyncedAt }

    override suspend fun saveSyncResult(result: LmsFetchResult, changes: AssignmentChanges) {
        mutex.withLock {
            val next = assignments.value.toMutableMap()
            for (fetched in result.assignments) {
                val old = next[fetched.id]
                next[fetched.id] =
                    if (fetched.submissionState == SubmissionState.UNKNOWN && old != null) {
                        fetched.copy(submissionState = old.submissionState, submittedAt = old.submittedAt)
                    } else {
                        fetched
                    }
            }
            changes.removed.forEach { next.remove(it.id) }
            assignments.value = next
            val nextAnnouncements = announcements.value.toMutableMap()
            val currentIds = result.announcements.map { it.id }.toSet()
            nextAnnouncements.entries.removeAll { (id, notice) ->
                notice.courseId in result.fetchedAnnouncementCourseIds && id !in currentIds
            }
            result.announcements.forEach { nextAnnouncements[it.id] = it }
            announcements.value = nextAnnouncements
            lastSyncedAt = result.fetchedAt
            syncedAt.value = result.fetchedAt
        }
    }
}
