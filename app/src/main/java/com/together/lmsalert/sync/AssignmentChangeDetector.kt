package com.together.lmsalert.sync

import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.LmsFetchResult
import com.together.lmsalert.lms.model.SubmissionState
import java.time.Instant

data class DueDateChange(
    val assignment: LmsAssignment,
    val oldDueAt: Instant?,
    val newDueAt: Instant?,
)

/** 이전에 저장한 목록과 이번 조회 결과의 차이 (기획서 2.2). */
data class AssignmentChanges(
    val newAssignments: List<LmsAssignment>,
    val dueDateChanged: List<DueDateChange>,
    val descriptionChanged: List<LmsAssignment>,
    val submissionChanged: List<LmsAssignment>,
    /** 조회에 성공한 과목에서 사라진 과제 = 실제 삭제 또는 비공개 처리. */
    val removed: List<LmsAssignment>,
    /** 첫 동기화면 모든 과제가 '신규'로 잡히므로 알림을 보내지 않는다. */
    val isInitialSync: Boolean,
) {
    val hasChanges: Boolean
        get() = newAssignments.isNotEmpty() || dueDateChanged.isNotEmpty() ||
            descriptionChanged.isNotEmpty() || submissionChanged.isNotEmpty() || removed.isNotEmpty()
}

object AssignmentChangeDetector {

    /**
     * @param previous 지난 동기화까지 알고 있던 (삭제되지 않은) 과제 목록
     * @param result   이번 LMS 조회 결과
     */
    fun detect(
        previous: List<LmsAssignment>,
        result: LmsFetchResult,
        isInitialSync: Boolean,
    ): AssignmentChanges {
        val previousById = previous.associateBy { it.id }
        val currentById = result.assignments.associateBy { it.id }

        val newAssignments = result.assignments.filter { it.id !in previousById }

        val dueDateChanged = mutableListOf<DueDateChange>()
        val descriptionChanged = mutableListOf<LmsAssignment>()
        val submissionChanged = mutableListOf<LmsAssignment>()

        for (current in result.assignments) {
            val old = previousById[current.id] ?: continue
            if (old.dueAt != current.dueAt) {
                dueDateChanged += DueDateChange(current, old.dueAt, current.dueAt)
            }
            if (old.description != current.description) {
                descriptionChanged += current
            }
            // 이번에 '미확인'이면 변경으로 보지 않는다. (마지막 확인 상태 유지는 저장소에서 처리)
            if (current.submissionState != SubmissionState.UNKNOWN &&
                old.submissionState != current.submissionState
            ) {
                submissionChanged += current
            }
        }

        // 조회에 실패한 과목, 이번 목록에 없는 과목(학기 종료 등)의 과제는 삭제로 판단하지 않는다.
        val removed = previous.filter {
            it.id !in currentById && it.courseId in result.fetchedCourseIds
        }

        return AssignmentChanges(
            newAssignments = newAssignments,
            dueDateChanged = dueDateChanged,
            descriptionChanged = descriptionChanged,
            submissionChanged = submissionChanged,
            removed = removed,
            isInitialSync = isInitialSync,
        )
    }
}
