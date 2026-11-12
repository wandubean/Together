package com.together.lmsalert.ui

import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.SubmissionState
import java.time.Instant

internal fun LmsAssignment.isDueSoon(now: Instant): Boolean {
    val deadline = dueAt ?: return false
    return submissionState != SubmissionState.SUBMITTED &&
        !deadline.isBefore(now) && deadline.isBefore(now.plusSeconds(3 * 24 * 60 * 60))
}
