package com.together.lmsalert.sync

import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.LmsFetchResult
import com.together.lmsalert.lms.model.SubmissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AssignmentChangeDetectorTest {

    private val due1 = Instant.parse("2026-11-10T14:59:00Z")
    private val due2 = Instant.parse("2026-11-12T14:59:00Z")

    private fun assignment(
        id: Long,
        courseId: Long = 1,
        dueAt: Instant? = due1,
        description: String? = "desc",
        submission: SubmissionState = SubmissionState.NOT_SUBMITTED,
    ) = LmsAssignment(
        id = id, courseId = courseId, courseName = "과목$courseId", name = "과제$id",
        description = description, dueAt = dueAt, lockAt = null, htmlUrl = null,
        submissionState = submission, submittedAt = null,
    )

    private fun result(assignments: List<LmsAssignment>, fetched: Set<Long> = setOf(1), failed: Set<Long> = emptySet()) =
        LmsFetchResult(emptyList(), assignments, fetched, failed, Instant.now())

    @Test
    fun `새로 생긴 과제를 신규로 감지한다`() {
        val changes = AssignmentChangeDetector.detect(
            previous = listOf(assignment(1)),
            result = result(listOf(assignment(1), assignment(2))),
            isInitialSync = false,
        )
        assertEquals(listOf(2L), changes.newAssignments.map { it.id })
        assertTrue(changes.hasChanges)
    }

    @Test
    fun `마감일과 설명 변경을 감지한다`() {
        val changes = AssignmentChangeDetector.detect(
            previous = listOf(assignment(1)),
            result = result(listOf(assignment(1, dueAt = due2, description = "new"))),
            isInitialSync = false,
        )
        assertEquals(1, changes.dueDateChanged.size)
        assertEquals(due1, changes.dueDateChanged[0].oldDueAt)
        assertEquals(due2, changes.dueDateChanged[0].newDueAt)
        assertEquals(listOf(1L), changes.descriptionChanged.map { it.id })
    }

    @Test
    fun `조회에 성공한 과목에서 사라진 과제만 삭제로 본다`() {
        val changes = AssignmentChangeDetector.detect(
            previous = listOf(assignment(1, courseId = 1), assignment(2, courseId = 2), assignment(3, courseId = 3)),
            result = result(emptyList(), fetched = setOf(1), failed = setOf(2)), // 3 은 목록에서 빠진 과목
            isInitialSync = false,
        )
        assertEquals(listOf(1L), changes.removed.map { it.id })
    }

    @Test
    fun `제출 상태가 미확인이면 변경으로 보지 않는다`() {
        val unknown = AssignmentChangeDetector.detect(
            previous = listOf(assignment(1, submission = SubmissionState.SUBMITTED)),
            result = result(listOf(assignment(1, submission = SubmissionState.UNKNOWN))),
            isInitialSync = false,
        )
        assertFalse(unknown.hasChanges)

        val submitted = AssignmentChangeDetector.detect(
            previous = listOf(assignment(1)),
            result = result(listOf(assignment(1, submission = SubmissionState.SUBMITTED))),
            isInitialSync = false,
        )
        assertEquals(listOf(1L), submitted.submissionChanged.map { it.id })
    }

    @Test
    fun `변경이 없으면 hasChanges 가 false`() {
        val changes = AssignmentChangeDetector.detect(
            previous = listOf(assignment(1)),
            result = result(listOf(assignment(1))),
            isInitialSync = false,
        )
        assertFalse(changes.hasChanges)
    }
}
