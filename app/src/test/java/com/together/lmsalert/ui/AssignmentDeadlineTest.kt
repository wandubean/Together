package com.together.lmsalert.ui

import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.SubmissionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AssignmentDeadlineTest {
    private val now = Instant.parse("2026-11-12T00:00:00Z")
    private val assignment = LmsAssignment(
        id = 1, courseId = 1, courseName = "과목", name = "과제",
        description = null, dueAt = now.plusSeconds(60), lockAt = null,
        htmlUrl = null, submissionState = SubmissionState.NOT_SUBMITTED, submittedAt = null,
    )

    @Test
    fun `미제출 과제만 마감까지 3일 미만일 때 임박으로 집계한다`() {
        assertTrue(assignment.isDueSoon(now))
        assertTrue(assignment.copy(dueAt = now).isDueSoon(now))
        assertTrue(assignment.copy(submissionState = SubmissionState.UNKNOWN).isDueSoon(now))
        assertFalse(assignment.copy(dueAt = now.minusSeconds(1)).isDueSoon(now))
        assertFalse(assignment.copy(dueAt = now.plusSeconds(3 * 24 * 60 * 60)).isDueSoon(now))
        assertFalse(assignment.copy(dueAt = null).isDueSoon(now))
        assertFalse(assignment.copy(submissionState = SubmissionState.SUBMITTED).isDueSoon(now))
    }
}
